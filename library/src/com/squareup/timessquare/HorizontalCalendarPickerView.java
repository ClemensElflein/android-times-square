// Copyright 2012 Square, Inc.
package com.squareup.timessquare;

import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.timessquare.CalendarPickerView.FluentInitializer;
import com.squareup.timessquare.CalendarPickerView.SelectionMode;
import com.squareup.timessquare.MonthCellDescriptor.RangeState;

/**
 * Android component to allow picking a date from a calendar view (a list of
 * months). Must be initialized after inflation with {@link #init(Date, Date)}
 * and can be customized with any of the {@link FluentInitializer} methods
 * returned. The currently selected date can be retrieved with
 * {@link #getSelectedDate()}.
 */
public class HorizontalCalendarPickerView extends FrameLayout {
	private DateFormat monthNameFormat;
	private DateFormat weekdayNameFormat;
	private DateFormat fullDateFormat;
	private Calendar today;
	private int dividerColor;
	private int dayBackgroundResId;
	private int dayTextColorResId;
	private int titleTextColor;
	private int headerTextColor;
	private Locale locale;
	private Calendar minCal;
	private Calendar maxCal;
	private Calendar monthCounter;
	private final List<List<List<MonthCellDescriptor>>> cells = new ArrayList<List<List<MonthCellDescriptor>>>();
	final List<MonthDescriptor> months = new ArrayList<MonthDescriptor>();
	final List<MonthCellDescriptor> selectedCells = new ArrayList<MonthCellDescriptor>();
	final List<MonthCellDescriptor> highlightedCells = new ArrayList<MonthCellDescriptor>();
	final List<Calendar> selectedCals = new ArrayList<Calendar>();
	final List<Calendar> highlightedCals = new ArrayList<Calendar>();

	public HorizontalCalendarPickerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		Resources res = context.getResources();
		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.CalendarPickerView);
		final int bg = a.getColor(
				R.styleable.CalendarPickerView_android_background,
				res.getColor(R.color.calendar_bg));
		dividerColor = a.getColor(R.styleable.CalendarPickerView_dividerColor,
				res.getColor(R.color.calendar_divider));
		dayBackgroundResId = a.getResourceId(
				R.styleable.CalendarPickerView_dayBackground,
				R.drawable.calendar_bg_selector);
		dayTextColorResId = a.getResourceId(
				R.styleable.CalendarPickerView_dayTextColor,
				R.color.calendar_text_selector);
		titleTextColor = a.getColor(
				R.styleable.CalendarPickerView_titleTextColor,
				R.color.calendar_text_active);
		headerTextColor = a.getColor(
				R.styleable.CalendarPickerView_headerTextColor,
				R.color.calendar_text_active);
		a.recycle();
		locale = Locale.getDefault();
		today = Calendar.getInstance();
		monthNameFormat = new SimpleDateFormat(
				context.getString(R.string.month_name_format), locale);
		weekdayNameFormat = new SimpleDateFormat(
				context.getString(R.string.day_name_format), locale);

		Calendar cal = Calendar.getInstance();
		Date now = cal.getTime();
		cal.add(Calendar.YEAR, 2);
		Date end = cal.getTime();
		this.init(now, end, locale);

		MonthView monthView = MonthView.create(
				this,
				(LayoutInflater) getContext().getSystemService(
						Context.LAYOUT_INFLATER_SERVICE), weekdayNameFormat,
				null, today, dividerColor, dayBackgroundResId,
				dayTextColorResId, titleTextColor, headerTextColor);
		monthView.init(months.get(0), cells.get(0), false);
		this.addView(monthView);
	}

	/**
	 * Both date parameters must be non-null and their {@link Date#getTime()}
	 * must not return 0. Time of day will be ignored. For instance, if you pass
	 * in {@code minDate} as 11/16/2012 5:15pm and {@code maxDate} as 11/16/2013
	 * 4:30am, 11/16/2012 will be the first selectable date and 11/15/2013 will
	 * be the last selectable date ({@code maxDate} is exclusive).
	 * <p>
	 * This will implicitly set the {@link SelectionMode} to
	 * {@link SelectionMode#SINGLE}. If you want a different selection mode, use
	 * {@link FluentInitializer#inMode(SelectionMode)} on the
	 * {@link FluentInitializer} this method returns.
	 * <p>
	 * The calendar will be constructed using the given locale. This means that
	 * all names (months, days) will be in the language of the locale and the
	 * weeks start with the day specified by the locale.
	 * 
	 * @param minDate
	 *            Earliest selectable date, inclusive. Must be earlier than
	 *            {@code maxDate}.
	 * @param maxDate
	 *            Latest selectable date, exclusive. Must be later than
	 *            {@code minDate}.
	 */
	private void init(Date minDate, Date maxDate, Locale locale) {
		if (minDate == null || maxDate == null) {
			throw new IllegalArgumentException(
					"minDate and maxDate must be non-null.  ");
		}
		if (minDate.after(maxDate)) {
			throw new IllegalArgumentException(
					"minDate must be before maxDate.  ");
		}
		if (minDate.getTime() == 0 || maxDate.getTime() == 0) {
			throw new IllegalArgumentException(
					"minDate and maxDate must be non-zero.  ");
		}
		if (locale == null) {
			throw new IllegalArgumentException("Locale is null.");
		}

		// Make sure that all calendar instances use the same locale.
		this.locale = locale;
		today = Calendar.getInstance(locale);
		minCal = Calendar.getInstance(locale);
		maxCal = Calendar.getInstance(locale);
		monthCounter = Calendar.getInstance(locale);
		monthNameFormat = new SimpleDateFormat(getContext().getString(
				R.string.month_name_format), locale);
		for (MonthDescriptor month : months) {
			month.setLabel(monthNameFormat.format(month.getDate()));
		}
		weekdayNameFormat = new SimpleDateFormat(getContext().getString(
				R.string.day_name_format), locale);
		fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);

		// Clear previous state.
		cells.clear();
		months.clear();
		minCal.setTime(minDate);
		maxCal.setTime(maxDate);
		setMidnight(minCal);
		setMidnight(maxCal);

		// maxDate is exclusive: bump back to the previous day so if maxDate is
		// the first of a month,
		// we don't accidentally include that month in the view.
		maxCal.add(MINUTE, -1);

		// Now iterate between minCal and maxCal and build up our list of months
		// to show.
		monthCounter.setTime(minCal.getTime());
		final int maxMonth = maxCal.get(MONTH);
		final int maxYear = maxCal.get(YEAR);
		while ((monthCounter.get(MONTH) <= maxMonth // Up to, including the
													// month.
				|| monthCounter.get(YEAR) < maxYear) // Up to the year.
				&& monthCounter.get(YEAR) < maxYear + 1) { // But not > next yr.
			Date date = monthCounter.getTime();
			MonthDescriptor month = new MonthDescriptor(
					monthCounter.get(MONTH), monthCounter.get(YEAR), date,
					monthNameFormat.format(date));
			cells.add(getMonthCells(month, monthCounter));
			Logr.d("Adding month %s", month);
			months.add(month);
			monthCounter.add(MONTH, 1);
		}

	}

	List<List<MonthCellDescriptor>> getMonthCells(MonthDescriptor month,
			Calendar startCal) {
		Calendar cal = Calendar.getInstance(locale);
		cal.setTime(startCal.getTime());
		List<List<MonthCellDescriptor>> cells = new ArrayList<List<MonthCellDescriptor>>();
		cal.set(DAY_OF_MONTH, 1);
		int firstDayOfWeek = cal.get(DAY_OF_WEEK);
		int offset = cal.getFirstDayOfWeek() - firstDayOfWeek;
		if (offset > 0) {
			offset -= 7;
		}
		cal.add(Calendar.DATE, offset);

		Calendar minSelectedCal = minDate(selectedCals);
		Calendar maxSelectedCal = maxDate(selectedCals);

		while ((cal.get(MONTH) < month.getMonth() + 1 || cal.get(YEAR) < month
				.getYear()) //
				&& cal.get(YEAR) <= month.getYear()) {
			Logr.d("Building week row starting at %s", cal.getTime());
			List<MonthCellDescriptor> weekCells = new ArrayList<MonthCellDescriptor>();
			cells.add(weekCells);
			for (int c = 0; c < 7; c++) {
				Date date = cal.getTime();
				boolean isCurrentMonth = cal.get(MONTH) == month.getMonth();
				boolean isSelected = isCurrentMonth
						&& containsDate(selectedCals, cal);
				boolean isSelectable = isCurrentMonth
						&& betweenDates(cal, minCal, maxCal);
				boolean isToday = sameDate(cal, today);
				boolean isHighlighted = containsDate(highlightedCals, cal);
				int value = cal.get(DAY_OF_MONTH);

				MonthCellDescriptor.RangeState rangeState = MonthCellDescriptor.RangeState.NONE;
				if (selectedCals.size() > 1) {
					if (sameDate(minSelectedCal, cal)) {
						rangeState = MonthCellDescriptor.RangeState.FIRST;
					} else if (sameDate(maxDate(selectedCals), cal)) {
						rangeState = MonthCellDescriptor.RangeState.LAST;
					} else if (betweenDates(cal, minSelectedCal, maxSelectedCal)) {
						rangeState = MonthCellDescriptor.RangeState.MIDDLE;
					}
				}

				weekCells.add(new MonthCellDescriptor(date, isCurrentMonth,
						isSelectable, isSelected, isToday, isHighlighted,
						value, rangeState));
				cal.add(DATE, 1);
			}
		}
		return cells;
	}

	private static boolean containsDate(List<Calendar> selectedCals,
			Calendar cal) {
		for (Calendar selectedCal : selectedCals) {
			if (sameDate(cal, selectedCal)) {
				return true;
			}
		}
		return false;
	}

	private static Calendar minDate(List<Calendar> selectedCals) {
		if (selectedCals == null || selectedCals.size() == 0) {
			return null;
		}
		Collections.sort(selectedCals);
		return selectedCals.get(0);
	}

	private static Calendar maxDate(List<Calendar> selectedCals) {
		if (selectedCals == null || selectedCals.size() == 0) {
			return null;
		}
		Collections.sort(selectedCals);
		return selectedCals.get(selectedCals.size() - 1);
	}

	private static boolean sameDate(Calendar cal, Calendar selectedDate) {
		return cal.get(MONTH) == selectedDate.get(MONTH)
				&& cal.get(YEAR) == selectedDate.get(YEAR)
				&& cal.get(DAY_OF_MONTH) == selectedDate.get(DAY_OF_MONTH);
	}

	private static boolean betweenDates(Calendar cal, Calendar minCal,
			Calendar maxCal) {
		final Date date = cal.getTime();
		return betweenDates(date, minCal, maxCal);
	}

	static boolean betweenDates(Date date, Calendar minCal, Calendar maxCal) {
		final Date min = minCal.getTime();
		return (date.equals(min) || date.after(min)) // >= minCal
				&& date.before(maxCal.getTime()); // && < maxCal
	}

	private static boolean sameMonth(Calendar cal, MonthDescriptor month) {
		return (cal.get(MONTH) == month.getMonth() && cal.get(YEAR) == month
				.getYear());
	}

	/** Clears out the hours/minutes/seconds/millis of a Calendar. */
	static void setMidnight(Calendar cal) {
		cal.set(HOUR_OF_DAY, 0);
		cal.set(MINUTE, 0);
		cal.set(SECOND, 0);
		cal.set(MILLISECOND, 0);
	}
}

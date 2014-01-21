/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.billington.calendar.recurrencepicker;

import android.accounts.Account;
import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract.Calendars;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.SearchView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;

public class Utils {
    private static final boolean DEBUG = false;
    private static final String TAG = "CalUtils";

    // Set to 0 until we have UI to perform undo
    public static final long UNDO_DELAY = 0;

    // For recurring events which instances of the series are being modified
    public static final int MODIFY_UNINITIALIZED = 0;
    public static final int MODIFY_SELECTED = 1;
    public static final int MODIFY_ALL_FOLLOWING = 2;
    public static final int MODIFY_ALL = 3;

    // When the edit event view finishes it passes back the appropriate exit
    // code.
    public static final int DONE_REVERT = 1 << 0;
    public static final int DONE_SAVE = 1 << 1;
    public static final int DONE_DELETE = 1 << 2;
    // And should re run with DONE_EXIT if it should also leave the view, just
    // exiting is identical to reverting
    public static final int DONE_EXIT = 1 << 0;

    public static final String OPEN_EMAIL_MARKER = " <";
    public static final String CLOSE_EMAIL_MARKER = ">";

    public static final String INTENT_KEY_DETAIL_VIEW = "DETAIL_VIEW";
    public static final String INTENT_KEY_VIEW_TYPE = "VIEW";
    public static final String INTENT_VALUE_VIEW_TYPE_DAY = "DAY";
    public static final String INTENT_KEY_HOME = "KEY_HOME";

    public static final int MONDAY_BEFORE_JULIAN_EPOCH = Time.EPOCH_JULIAN_DAY - 3;
    public static final int DECLINED_EVENT_ALPHA = 0x66;
    public static final int DECLINED_EVENT_TEXT_ALPHA = 0xC0;

    private static final float SATURATION_ADJUST = 1.3f;
    private static final float INTENSITY_ADJUST = 0.8f;

    // Defines used by the DNA generation code
    static final int DAY_IN_MINUTES = 60 * 24;
    static final int WEEK_IN_MINUTES = DAY_IN_MINUTES * 7;
    // The work day is being counted as 6am to 8pm
    static int WORK_DAY_MINUTES = 14 * 60;
    static int WORK_DAY_START_MINUTES = 6 * 60;
    static int WORK_DAY_END_MINUTES = 20 * 60;
    static int WORK_DAY_END_LENGTH = (24 * 60) - WORK_DAY_END_MINUTES;
    static int CONFLICT_COLOR = 0xFF000000;
    static boolean mMinutesLoaded = false;

    public static final int YEAR_MIN = 1970;
    public static final int YEAR_MAX = 2036;

    // The name of the shared preferences file. This name must be maintained for
    // historical
    // reasons, as it's what PreferenceManager assigned the first time the file
    // was created.
    static final String SHARED_PREFS_NAME = "com.android.calendar_preferences";

    public static final String KEY_QUICK_RESPONSES = "preferences_quick_responses";

    public static final String KEY_ALERTS_VIBRATE_WHEN = "preferences_alerts_vibrateWhen";

    public static final String APPWIDGET_DATA_TYPE = "vnd.android.data/update";

    static final String MACHINE_GENERATED_ADDRESS = "calendar.google.com";

    private static boolean mAllowWeekForDetailView = false;
    private static long mTardis = 0;
    private static String sVersion = null;

    private static final Pattern mWildcardPattern = Pattern.compile("^.*$");

    /**
     * A coordinate must be of the following form for Google Maps to correctly use it:
     * Latitude, Longitude
     * <p/>
     * This may be in decimal form:
     * Latitude: {-90 to 90}
     * Longitude: {-180 to 180}
     * <p/>
     * Or, in degrees, minutes, and seconds:
     * Latitude: {-90 to 90}° {0 to 59}' {0 to 59}"
     * Latitude: {-180 to 180}° {0 to 59}' {0 to 59}"
     * + or - degrees may also be represented with N or n, S or s for latitude, and with
     * E or e, W or w for longitude, where the direction may either precede or follow the value.
     * <p/>
     * Some examples of coordinates that will be accepted by the regex:
     * 37.422081°, -122.084576°
     * 37.422081,-122.084576
     * +37°25'19.49", -122°5'4.47"
     * 37°25'19.49"N, 122°5'4.47"W
     * N 37° 25' 19.49",  W 122° 5' 4.47"
     */
    private static final String COORD_DEGREES_LATITUDE =
            "([-+NnSs]" + "(\\s)*)?"
                    + "[1-9]?[0-9](\u00B0)" + "(\\s)*"
                    + "([1-5]?[0-9]\')?" + "(\\s)*"
                    + "([1-5]?[0-9]" + "(\\.[0-9]+)?\")?"
                    + "((\\s)*" + "[NnSs])?";
    private static final String COORD_DEGREES_LONGITUDE =
            "([-+EeWw]" + "(\\s)*)?"
                    + "(1)?[0-9]?[0-9](\u00B0)" + "(\\s)*"
                    + "([1-5]?[0-9]\')?" + "(\\s)*"
                    + "([1-5]?[0-9]" + "(\\.[0-9]+)?\")?"
                    + "((\\s)*" + "[EeWw])?";
    private static final String COORD_DEGREES_PATTERN =
            COORD_DEGREES_LATITUDE
                    + "(\\s)*" + "," + "(\\s)*"
                    + COORD_DEGREES_LONGITUDE;
    private static final String COORD_DECIMAL_LATITUDE =
            "[+-]?"
                    + "[1-9]?[0-9]" + "(\\.[0-9]+)"
                    + "(\u00B0)?";
    private static final String COORD_DECIMAL_LONGITUDE =
            "[+-]?"
                    + "(1)?[0-9]?[0-9]" + "(\\.[0-9]+)"
                    + "(\u00B0)?";
    private static final String COORD_DECIMAL_PATTERN =
            COORD_DECIMAL_LATITUDE
                    + "(\\s)*" + "," + "(\\s)*"
                    + COORD_DECIMAL_LONGITUDE;
    private static final Pattern COORD_PATTERN =
            Pattern.compile(COORD_DEGREES_PATTERN + "|" + COORD_DECIMAL_PATTERN);

    private static final String NANP_ALLOWED_SYMBOLS = "()+-*#.";
    private static final int NANP_MIN_DIGITS = 7;
    private static final int NANP_MAX_DIGITS = 11;


    /**
     * Returns whether the SDK is the Jellybean release or later.
     */
    public static boolean isJellybeanOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    /**
     * Returns whether the SDK is the KeyLimePie release or later.
     */
    public static boolean isKeyLimePieOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }


    /**
     * Gets the intent action for telling the widget to update.
     */
    public static String getWidgetUpdateAction(Context context) {
        return context.getPackageName() + ".APPWIDGET_UPDATE";
    }

    /**
     * Gets the intent action for telling the widget to update.
     */
    public static String getWidgetScheduledUpdateAction(Context context) {
        return context.getPackageName() + ".APPWIDGET_SCHEDULED_UPDATE";
    }

    /**
     * Gets the intent action for telling the widget to update.
     */
    public static String getSearchAuthority(Context context) {
        return context.getPackageName() + ".CalendarRecentSuggestionsProvider";
    }


    protected static void tardis() {
        mTardis = System.currentTimeMillis();
    }

    protected static long getTardis() {
        return mTardis;
    }


    public static MatrixCursor matrixCursorFromCursor(Cursor cursor) {
        if (cursor == null) {
            return null;
        }

        String[] columnNames = cursor.getColumnNames();
        if (columnNames == null) {
            columnNames = new String[]{};
        }
        MatrixCursor newCursor = new MatrixCursor(columnNames);
        int numColumns = cursor.getColumnCount();
        String data[] = new String[numColumns];
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            for (int i = 0; i < numColumns; i++) {
                data[i] = cursor.getString(i);
            }
            newCursor.addRow(data);
        }
        return newCursor;
    }

    /**
     * Compares two cursors to see if they contain the same data.
     *
     * @return Returns true of the cursors contain the same data and are not
     * null, false otherwise
     */
    public static boolean compareCursors(Cursor c1, Cursor c2) {
        if (c1 == null || c2 == null) {
            return false;
        }

        int numColumns = c1.getColumnCount();
        if (numColumns != c2.getColumnCount()) {
            return false;
        }

        if (c1.getCount() != c2.getCount()) {
            return false;
        }

        c1.moveToPosition(-1);
        c2.moveToPosition(-1);
        while (c1.moveToNext() && c2.moveToNext()) {
            for (int i = 0; i < numColumns; i++) {
                if (!TextUtils.equals(c1.getString(i), c2.getString(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * If the given intent specifies a time (in milliseconds since the epoch),
     * then that time is returned. Otherwise, the current time is returned.
     */
    public static final long timeFromIntentInMillis(Intent intent) {
        // If the time was specified, then use that. Otherwise, use the current
        // time.
        Uri data = intent.getData();
        long millis = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, -1);
        if (millis == -1 && data != null && data.isHierarchical()) {
            List<String> path = data.getPathSegments();
            if (path.size() == 2 && path.get(0).equals("time")) {
                try {
                    millis = Long.valueOf(data.getLastPathSegment());
                } catch (NumberFormatException e) {
                    Log.i("Calendar", "timeFromIntentInMillis: Data existed but no valid time "
                            + "found. Using current time.");
                }
            }
        }
        if (millis <= 0) {
            millis = System.currentTimeMillis();
        }
        return millis;
    }


    /**
     * Returns a list joined together by the provided delimiter, for example,
     * ["a", "b", "c"] could be joined into "a,b,c"
     *
     * @param things the things to join together
     * @param delim  the delimiter to use
     * @return a string contained the things joined together
     */
    public static String join(List<?> things, String delim) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Object thing : things) {
            if (first) {
                first = false;
            } else {
                builder.append(delim);
            }
            builder.append(thing.toString());
        }
        return builder.toString();
    }

    /**
     * Returns the week since {@link android.text.format.Time#EPOCH_JULIAN_DAY} (Jan 1, 1970)
     * adjusted for first day of week.
     * <p/>
     * This takes a julian day and the week start day and calculates which
     * week since {@link android.text.format.Time#EPOCH_JULIAN_DAY} that day occurs in, starting
     * at 0. *Do not* use this to compute the ISO week number for the year.
     *
     * @param julianDay      The julian day to calculate the week number for
     * @param firstDayOfWeek Which week day is the first day of the week,
     *                       see {@link android.text.format.Time#SUNDAY}
     * @return Weeks since the epoch
     */
    public static int getWeeksSinceEpochFromJulianDay(int julianDay, int firstDayOfWeek) {
        int diff = Time.THURSDAY - firstDayOfWeek;
        if (diff < 0) {
            diff += 7;
        }
        int refDay = Time.EPOCH_JULIAN_DAY - diff;
        return (julianDay - refDay) / 7;
    }

    /**
     * Takes a number of weeks since the epoch and calculates the Julian day of
     * the Monday for that week.
     * <p/>
     * This assumes that the week containing the {@link android.text.format.Time#EPOCH_JULIAN_DAY}
     * is considered week 0. It returns the Julian day for the Monday
     * {@code week} weeks after the Monday of the week containing the epoch.
     *
     * @param week Number of weeks since the epoch
     * @return The julian day for the Monday of the given week since the epoch
     */
    public static int getJulianMondayFromWeeksSinceEpoch(int week) {
        return MONDAY_BEFORE_JULIAN_EPOCH + week * 7;
    }

    /**
     * Get first day of week as android.text.format.Time constant.
     *
     * @return the first day of week in android.text.format.Time
     */
    public static int getFirstDayOfWeek(Context context) {
        int startDay = Calendar.getInstance().getFirstDayOfWeek();

        if (startDay == Calendar.SATURDAY) {
            return Time.SATURDAY;
        } else if (startDay == Calendar.MONDAY) {
            return Time.MONDAY;
        } else {
            return Time.SUNDAY;
        }
    }

    /**
     * Get first day of week as java.util.Calendar constant.
     *
     * @return the first day of week as a java.util.Calendar constant
     */
    public static int getFirstDayOfWeekAsCalendar(Context context) {
        return convertDayOfWeekFromTimeToCalendar(getFirstDayOfWeek(context));
    }

    /**
     * Converts the day of the week from android.text.format.Time to java.util.Calendar
     */
    public static int convertDayOfWeekFromTimeToCalendar(int timeDayOfWeek) {
        switch (timeDayOfWeek) {
            case Time.MONDAY:
                return Calendar.MONDAY;
            case Time.TUESDAY:
                return Calendar.TUESDAY;
            case Time.WEDNESDAY:
                return Calendar.WEDNESDAY;
            case Time.THURSDAY:
                return Calendar.THURSDAY;
            case Time.FRIDAY:
                return Calendar.FRIDAY;
            case Time.SATURDAY:
                return Calendar.SATURDAY;
            case Time.SUNDAY:
                return Calendar.SUNDAY;
            default:
                throw new IllegalArgumentException("Argument must be between Time.SUNDAY and " +
                        "Time.SATURDAY");
        }
    }


    /**
     * Determine whether the column position is Saturday or not.
     *
     * @param column         the column position
     * @param firstDayOfWeek the first day of week in android.text.format.Time
     * @return true if the column is Saturday position
     */
    public static boolean isSaturday(int column, int firstDayOfWeek) {
        return (firstDayOfWeek == Time.SUNDAY && column == 6)
                || (firstDayOfWeek == Time.MONDAY && column == 5)
                || (firstDayOfWeek == Time.SATURDAY && column == 0);
    }

    /**
     * Determine whether the column position is Sunday or not.
     *
     * @param column         the column position
     * @param firstDayOfWeek the first day of week in android.text.format.Time
     * @return true if the column is Sunday position
     */
    public static boolean isSunday(int column, int firstDayOfWeek) {
        return (firstDayOfWeek == Time.SUNDAY && column == 0)
                || (firstDayOfWeek == Time.MONDAY && column == 6)
                || (firstDayOfWeek == Time.SATURDAY && column == 1);
    }

    /**
     * Convert given UTC time into current local time. This assumes it is for an
     * allday event and will adjust the time to be on a midnight boundary.
     *
     * @param recycle Time object to recycle, otherwise null.
     * @param utcTime Time to convert, in UTC.
     * @param tz      The time zone to convert this time to.
     */
    public static long convertAlldayUtcToLocal(Time recycle, long utcTime, String tz) {
        if (recycle == null) {
            recycle = new Time();
        }
        recycle.timezone = Time.TIMEZONE_UTC;
        recycle.set(utcTime);
        recycle.timezone = tz;
        return recycle.normalize(true);
    }

    public static long convertAlldayLocalToUTC(Time recycle, long localTime, String tz) {
        if (recycle == null) {
            recycle = new Time();
        }
        recycle.timezone = tz;
        recycle.set(localTime);
        recycle.timezone = Time.TIMEZONE_UTC;
        return recycle.normalize(true);
    }

    /**
     * Finds and returns the next midnight after "theTime" in milliseconds UTC
     *
     * @param recycle - Time object to recycle, otherwise null.
     * @param theTime - Time used for calculations (in UTC)
     * @param tz      The time zone to convert this time to.
     */
    public static long getNextMidnight(Time recycle, long theTime, String tz) {
        if (recycle == null) {
            recycle = new Time();
        }
        recycle.timezone = tz;
        recycle.set(theTime);
        recycle.monthDay++;
        recycle.hour = 0;
        recycle.minute = 0;
        recycle.second = 0;
        return recycle.normalize(true);
    }

    /**
     * Scan through a cursor of calendars and check if names are duplicated.
     * This travels a cursor containing calendar display names and fills in the
     * provided map with whether or not each name is repeated.
     *
     * @param isDuplicateName The map to put the duplicate check results in.
     * @param cursor          The query of calendars to check
     * @param nameIndex       The column of the query that contains the display name
     */
    public static void checkForDuplicateNames(
            Map<String, Boolean> isDuplicateName, Cursor cursor, int nameIndex) {
        isDuplicateName.clear();
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String displayName = cursor.getString(nameIndex);
            // Set it to true if we've seen this name before, false otherwise
            if (displayName != null) {
                isDuplicateName.put(displayName, isDuplicateName.containsKey(displayName));
            }
        }
    }

    /**
     * Null-safe object comparison
     *
     * @param s1
     * @param s2
     * @return
     */
    public static boolean equals(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    public static void setAllowWeekForDetailView(boolean allowWeekView) {
        mAllowWeekForDetailView = allowWeekView;
    }

    public static boolean getAllowWeekForDetailView() {
        return mAllowWeekForDetailView;
    }

    public static boolean getConfigBool(Context c, int key) {
        return c.getResources().getBoolean(key);
    }

    /**
     * For devices with Jellybean or later, darkens the given color to ensure that white text is
     * clearly visible on top of it.  For devices prior to Jellybean, does nothing, as the
     * sync adapter handles the color change.
     *
     * @param color
     */
    public static int getDisplayColorFromColor(int color) {
        if (!isJellybeanOrLater()) {
            return color;
        }

        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(hsv[1] * SATURATION_ADJUST, 1.0f);
        hsv[2] = hsv[2] * INTENSITY_ADJUST;
        return Color.HSVToColor(hsv);
    }

    // This takes a color and computes what it would look like blended with
    // white. The result is the color that should be used for declined events.
    public static int getDeclinedColorFromColor(int color) {
        int bg = 0xffffffff;
        int a = DECLINED_EVENT_ALPHA;
        int r = (((color & 0x00ff0000) * a) + ((bg & 0x00ff0000) * (0xff - a))) & 0xff000000;
        int g = (((color & 0x0000ff00) * a) + ((bg & 0x0000ff00) * (0xff - a))) & 0x00ff0000;
        int b = (((color & 0x000000ff) * a) + ((bg & 0x000000ff) * (0xff - a))) & 0x0000ff00;
        return (0xff000000) | ((r | g | b) >> 8);
    }

    // A single strand represents one color of events. Events are divided up by
    // color to make them convenient to draw. The black strand is special in
    // that it holds conflicting events as well as color settings for allday on
    // each day.
    public static class DNAStrand {
        public float[] points;
        public int[] allDays; // color for the allday, 0 means no event
        int position;
        public int color;
        int count;
    }

    // A segment is a single continuous length of time occupied by a single
    // color. Segments should never span multiple days.
    private static class DNASegment {
        int startMinute; // in minutes since the start of the week
        int endMinute;
        int color; // Calendar color or black for conflicts
        int day; // quick reference to the day this segment is on
    }


    // This processes all the segments, sorts them by color, and generates a
    // list of points to draw
    private static void weaveDNAStrands(LinkedList<DNASegment> segments, int firstJulianDay,
                                        HashMap<Integer, DNAStrand> strands, int top, int bottom, int[] dayXs) {
        // First, get rid of any colors that ended up with no segments
        Iterator<DNAStrand> strandIterator = strands.values().iterator();
        while (strandIterator.hasNext()) {
            DNAStrand strand = strandIterator.next();
            if (strand.count < 1 && strand.allDays == null) {
                strandIterator.remove();
                continue;
            }
            strand.points = new float[strand.count * 4];
            strand.position = 0;
        }
        // Go through each segment and compute its points
        for (DNASegment segment : segments) {
            // Add the points to the strand of that color
            DNAStrand strand = strands.get(segment.color);
            int dayIndex = segment.day - firstJulianDay;
            int dayStartMinute = segment.startMinute % DAY_IN_MINUTES;
            int dayEndMinute = segment.endMinute % DAY_IN_MINUTES;
            int height = bottom - top;
            int workDayHeight = height * 3 / 4;
            int remainderHeight = (height - workDayHeight) / 2;

            int x = dayXs[dayIndex];
            int y0 = 0;
            int y1 = 0;

            y0 = top + getPixelOffsetFromMinutes(dayStartMinute, workDayHeight, remainderHeight);
            y1 = top + getPixelOffsetFromMinutes(dayEndMinute, workDayHeight, remainderHeight);
            if (DEBUG) {
                Log.d(TAG, "Adding " + Integer.toHexString(segment.color) + " at x,y0,y1: " + x
                        + " " + y0 + " " + y1 + " for " + dayStartMinute + " " + dayEndMinute);
            }
            strand.points[strand.position++] = x;
            strand.points[strand.position++] = y0;
            strand.points[strand.position++] = x;
            strand.points[strand.position++] = y1;
        }
    }

    /**
     * Compute a pixel offset from the top for a given minute from the work day
     * height and the height of the top area.
     */
    private static int getPixelOffsetFromMinutes(int minute, int workDayHeight,
                                                 int remainderHeight) {
        int y;
        if (minute < WORK_DAY_START_MINUTES) {
            y = minute * remainderHeight / WORK_DAY_START_MINUTES;
        } else if (minute < WORK_DAY_END_MINUTES) {
            y = remainderHeight + (minute - WORK_DAY_START_MINUTES) * workDayHeight
                    / WORK_DAY_MINUTES;
        } else {
            y = remainderHeight + workDayHeight + (minute - WORK_DAY_END_MINUTES) * remainderHeight
                    / WORK_DAY_END_LENGTH;
        }
        return y;
    }

    /**
     * Try to get a strand of the given color. Create it if it doesn't exist.
     */
    private static DNAStrand getOrCreateStrand(HashMap<Integer, DNAStrand> strands, int color) {
        DNAStrand strand = strands.get(color);
        if (strand == null) {
            strand = new DNAStrand();
            strand.color = color;
            strand.count = 0;
            strands.put(strand.color, strand);
        }
        return strand;
    }


    /**
     * This sets up a search view to use Calendar's search suggestions provider
     * and to allow refining the search.
     *
     * @param view The {@link android.widget.SearchView} to set up
     * @param act  The activity using the view
     */
    public static void setUpSearchView(SearchView view, Activity act) {
        SearchManager searchManager = (SearchManager) act.getSystemService(Context.SEARCH_SERVICE);
        view.setSearchableInfo(searchManager.getSearchableInfo(act.getComponentName()));
        view.setQueryRefinementEnabled(true);
    }

    // Calculate the time until midnight + 1 second and set the handler to
    // do run the runnable
    public static void setMidnightUpdater(Handler h, Runnable r, String timezone) {
        if (h == null || r == null || timezone == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Time time = new Time(timezone);
        time.set(now);
        long runInMillis = (24 * 3600 - time.hour * 3600 - time.minute * 60 -
                time.second + 1) * 1000;
        h.removeCallbacks(r);
        h.postDelayed(r, runInMillis);
    }

    // Stop the midnight update thread
    public static void resetMidnightUpdater(Handler h, Runnable r) {
        if (h == null || r == null) {
            return;
        }
        h.removeCallbacks(r);
    }

    /**
     * Returns the timezone to display in the event info, if the local timezone is different
     * from the event timezone.  Otherwise returns null.
     */
    public static String getDisplayedTimezone(long startMillis, String localTimezone,
                                              String eventTimezone) {
        String tzDisplay = null;
        if (!TextUtils.equals(localTimezone, eventTimezone)) {
            // Figure out if this is in DST
            TimeZone tz = TimeZone.getTimeZone(localTimezone);
            if (tz == null || tz.getID().equals("GMT")) {
                tzDisplay = localTimezone;
            } else {
                Time startTime = new Time(localTimezone);
                startTime.set(startMillis);
                tzDisplay = tz.getDisplayName(startTime.isDst != 0, TimeZone.SHORT);
            }
        }
        return tzDisplay;
    }

    /**
     * Returns whether the specified time interval is in a single day.
     */
    private static boolean singleDayEvent(long startMillis, long endMillis, long localGmtOffset) {
        if (startMillis == endMillis) {
            return true;
        }

        // An event ending at midnight should still be a single-day event, so check
        // time end-1.
        int startDay = Time.getJulianDay(startMillis, localGmtOffset);
        int endDay = Time.getJulianDay(endMillis - 1, localGmtOffset);
        return startDay == endDay;
    }

    // Using int constants as a return value instead of an enum to minimize resources.
    private static final int TODAY = 1;
    private static final int TOMORROW = 2;
    private static final int NONE = 0;

    /**
     * Returns TODAY or TOMORROW if applicable.  Otherwise returns NONE.
     */
    private static int isTodayOrTomorrow(Resources r, long dayMillis,
                                         long currentMillis, long localGmtOffset) {
        int startDay = Time.getJulianDay(dayMillis, localGmtOffset);
        int currentDay = Time.getJulianDay(currentMillis, localGmtOffset);

        int days = startDay - currentDay;
        if (days == 1) {
            return TOMORROW;
        } else if (days == 0) {
            return TODAY;
        } else {
            return NONE;
        }
    }

    /**
     * Create an intent for emailing attendees of an event.
     *
     * @param resources    The resources for translating strings.
     * @param eventTitle   The title of the event to use as the email subject.
     * @param body         The default text for the email body.
     * @param toEmails     The list of emails for the 'to' line.
     * @param ccEmails     The list of emails for the 'cc' line.
     * @param ownerAccount The owner account to use as the email sender.
     */
    public static Intent createEmailAttendeesIntent(Resources resources, String eventTitle,
                                                    String body, List<String> toEmails, List<String> ccEmails, String ownerAccount) {
        List<String> toList = toEmails;
        List<String> ccList = ccEmails;
        if (toEmails.size() <= 0) {
            if (ccEmails.size() <= 0) {
                // TODO: Return a SEND intent if no one to email to, to at least populate
                // a draft email with the subject (and no recipients).
                throw new IllegalArgumentException("Both toEmails and ccEmails are empty.");
            }

            // Email app does not work with no "to" recipient.  Move all 'cc' to 'to'
            // in this case.
            toList = ccEmails;
            ccList = null;
        }

        // Use the event title as the email subject (prepended with 'Re: ').
        String subject = null;
        if (eventTitle != null) {
            subject = resources.getString(R.string.email_subject_prefix) + eventTitle;
        }

        // Use the SENDTO intent with a 'mailto' URI, because using SEND will cause
        // the picker to show apps like text messaging, which does not make sense
        // for email addresses.  We put all data in the URI instead of using the extra
        // Intent fields (ie. EXTRA_CC, etc) because some email apps might not handle
        // those (though gmail does).
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("mailto");

        // We will append the first email to the 'mailto' field later (because the
        // current state of the Email app requires it).  Add the remaining 'to' values
        // here.  When the email codebase is updated, we can simplify this.
        if (toList.size() > 1) {
            for (int i = 1; i < toList.size(); i++) {
                // The Email app requires repeated parameter settings instead of
                // a single comma-separated list.
                uriBuilder.appendQueryParameter("to", toList.get(i));
            }
        }

        // Add the subject parameter.
        if (subject != null) {
            uriBuilder.appendQueryParameter("subject", subject);
        }

        // Add the subject parameter.
        if (body != null) {
            uriBuilder.appendQueryParameter("body", body);
        }

        // Add the cc parameters.
        if (ccList != null && ccList.size() > 0) {
            for (String email : ccList) {
                uriBuilder.appendQueryParameter("cc", email);
            }
        }

        // Insert the first email after 'mailto:' in the URI manually since Uri.Builder
        // doesn't seem to have a way to do this.
        String uri = uriBuilder.toString();
        if (uri.startsWith("mailto:")) {
            StringBuilder builder = new StringBuilder(uri);
            builder.insert(7, Uri.encode(toList.get(0)));
            uri = builder.toString();
        }

        // Start the email intent.  Email from the account of the calendar owner in case there
        // are multiple email accounts.
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse(uri));
        emailIntent.putExtra("fromAccountString", ownerAccount);

        // Workaround a Email bug that overwrites the body with this intent extra.  If not
        // set, it clears the body.
        if (body != null) {
            emailIntent.putExtra(Intent.EXTRA_TEXT, body);
        }

        return Intent.createChooser(emailIntent, resources.getString(R.string.email_picker_label));
    }

    /**
     * Example fake email addresses used as attendee emails are resources like conference rooms,
     * or another calendar, etc.  These all end in "calendar.google.com".
     */
    public static boolean isValidEmail(String email) {
        return email != null && !email.endsWith(MACHINE_GENERATED_ADDRESS);
    }

    /**
     * Returns true if:
     * (1) the email is not a resource like a conference room or another calendar.
     * Catch most of these by filtering out suffix calendar.google.com.
     * (2) the email is not equal to the sync account to prevent mailing himself.
     */
    public static boolean isEmailableFrom(String email, String syncAccountName) {
        return Utils.isValidEmail(email) && !email.equals(syncAccountName);
    }

    private static class CalendarBroadcastReceiver extends BroadcastReceiver {

        Runnable mCallBack;

        public CalendarBroadcastReceiver(Runnable callback) {
            super();
            mCallBack = callback;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_DATE_CHANGED) ||
                    intent.getAction().equals(Intent.ACTION_TIME_CHANGED) ||
                    intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED) ||
                    intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                if (mCallBack != null) {
                    mCallBack.run();
                }
            }
        }
    }

    public static BroadcastReceiver setTimeChangesReceiver(Context c, Runnable callback) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);

        CalendarBroadcastReceiver r = new CalendarBroadcastReceiver(callback);
        c.registerReceiver(r, filter);
        return r;
    }

    public static void clearTimeChangesReceiver(Context c, BroadcastReceiver r) {
        c.unregisterReceiver(r);
    }

    /**
     * Return the app version code.
     */
    public static String getVersionCode(Context context) {
        if (sVersion == null) {
            try {
                sVersion = context.getPackageManager().getPackageInfo(
                        context.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                // Can't find version; just leave it blank.
                Log.e(TAG, "Error finding package " + context.getApplicationInfo().packageName);
            }
        }
        return sVersion;
    }

    /**
     * Checks the server for an updated list of Calendars (in the background).
     * <p/>
     * If a Calendar is added on the web (and it is selected and not
     * hidden) then it will be added to the list of calendars on the phone
     * (when this finishes).  When a new calendar from the
     * web is added to the phone, then the events for that calendar are also
     * downloaded from the web.
     * <p/>
     * This sync is done automatically in the background when the
     * SelectCalendars activity and fragment are started.
     *
     * @param account - The account to sync. May be null to sync all accounts.
     */
    public static void startCalendarMetafeedSync(Account account) {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean("metafeedonly", true);
        ContentResolver.requestSync(account, Calendars.CONTENT_URI.getAuthority(), extras);
    }

    /**
     * Replaces stretches of text that look like addresses and phone numbers with clickable
     * links. If lastDitchGeo is true, then if no links are found in the textview, the entire
     * string will be converted to a single geo link. Any spans that may have previously been
     * in the text will be cleared out.
     * <p/>
     * This is really just an enhanced version of Linkify.addLinks().
     *
     * @param text         - The string to search for links.
     * @param lastDitchGeo - If no links are found, turn the entire string into one geo link.
     * @return Spannable object containing the list of URL spans found.
     */
    public static Spannable extendedLinkify(String text, boolean lastDitchGeo) {
        // We use a copy of the string argument so it's available for later if necessary.
        Spannable spanText = SpannableString.valueOf(text);

        /*
         * If the text includes a street address like "1600 Amphitheater Parkway, 94043",
         * the current Linkify code will identify "94043" as a phone number and invite
         * you to dial it (and not provide a map link for the address).  For outside US,
         * use Linkify result iff it spans the entire text.  Otherwise send the user to maps.
         */
        String defaultPhoneRegion = System.getProperty("user.region", "US");
        if (!defaultPhoneRegion.equals("US")) {
            Linkify.addLinks(spanText, Linkify.ALL);

            // If Linkify links the entire text, use that result.
            URLSpan[] spans = spanText.getSpans(0, spanText.length(), URLSpan.class);
            if (spans.length == 1) {
                int linkStart = spanText.getSpanStart(spans[0]);
                int linkEnd = spanText.getSpanEnd(spans[0]);
                if (linkStart <= indexFirstNonWhitespaceChar(spanText) &&
                        linkEnd >= indexLastNonWhitespaceChar(spanText) + 1) {
                    return spanText;
                }
            }

            // Otherwise, to be cautious and to try to prevent false positives, reset the spannable.
            spanText = SpannableString.valueOf(text);
            // If lastDitchGeo is true, default the entire string to geo.
            if (lastDitchGeo && !text.isEmpty()) {
                Linkify.addLinks(spanText, mWildcardPattern, "geo:0,0?q=");
            }
            return spanText;
        }

        /*
         * For within US, we want to have better recognition of phone numbers without losing
         * any of the existing annotations.  Ideally this would be addressed by improving Linkify.
         * For now we manage it as a second pass over the text.
         *
         * URIs and e-mail addresses are pretty easy to pick out of text.  Phone numbers
         * are a bit tricky because they have radically different formats in different
         * countries, in terms of both the digits and the way in which they are commonly
         * written or presented (e.g. the punctuation and spaces in "(650) 555-1212").
         * The expected format of a street address is defined in WebView.findAddress().  It's
         * pretty narrowly defined, so it won't often match.
         *
         * The RFC 3966 specification defines the format of a "tel:" URI.
         *
         * Start by letting Linkify find anything that isn't a phone number.  We have to let it
         * run first because every invocation removes all previous URLSpan annotations.
         *
         * Ideally we'd use the external/libphonenumber routines, but those aren't available
         * to unbundled applications.
         */
        boolean linkifyFoundLinks = Linkify.addLinks(spanText,
                Linkify.ALL & ~(Linkify.PHONE_NUMBERS));

        /*
         * Get a list of any spans created by Linkify, for the coordinate overlapping span check.
         */
        URLSpan[] existingSpans = spanText.getSpans(0, spanText.length(), URLSpan.class);

        /*
         * Check for coordinates.
         * This must be done before phone numbers because longitude may look like a phone number.
         */
        Matcher coordMatcher = COORD_PATTERN.matcher(spanText);
        int coordCount = 0;
        while (coordMatcher.find()) {
            int start = coordMatcher.start();
            int end = coordMatcher.end();
            if (spanWillOverlap(spanText, existingSpans, start, end)) {
                continue;
            }

            URLSpan span = new URLSpan("geo:0,0?q=" + coordMatcher.group());
            spanText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            coordCount++;
        }

        /*
         * Update the list of existing spans, for the phone number overlapping span check.
         */
        existingSpans = spanText.getSpans(0, spanText.length(), URLSpan.class);

        /*
         * Search for phone numbers.
         *
         * Some URIs contain strings of digits that look like phone numbers.  If both the URI
         * scanner and the phone number scanner find them, we want the URI link to win.  Since
         * the URI scanner runs first, we just need to avoid creating overlapping spans.
         */
        int[] phoneSequences = findNanpPhoneNumbers(text);

        /*
         * Insert spans for the numbers we found.  We generate "tel:" URIs.
         */
        int phoneCount = 0;
        for (int match = 0; match < phoneSequences.length / 2; match++) {
            int start = phoneSequences[match * 2];
            int end = phoneSequences[match * 2 + 1];

            if (spanWillOverlap(spanText, existingSpans, start, end)) {
                continue;
            }

            /*
             * The Linkify code takes the matching span and strips out everything that isn't a
             * digit or '+' sign.  We do the same here.  Extension numbers will get appended
             * without a separator, but the dialer wasn't doing anything useful with ";ext="
             * anyway.
             */

            //String dialStr = phoneUtil.format(match.number(),
            //        PhoneNumberUtil.PhoneNumberFormat.RFC3966);
            StringBuilder dialBuilder = new StringBuilder();
            for (int i = start; i < end; i++) {
                char ch = spanText.charAt(i);
                if (ch == '+' || Character.isDigit(ch)) {
                    dialBuilder.append(ch);
                }
            }
            URLSpan span = new URLSpan("tel:" + dialBuilder.toString());

            spanText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            phoneCount++;
        }

        /*
         * If lastDitchGeo, and no other links have been found, set the entire string as a geo link.
         */
        if (lastDitchGeo && !text.isEmpty() &&
                !linkifyFoundLinks && phoneCount == 0 && coordCount == 0) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "No linkification matches, using geo default");
            }
            Linkify.addLinks(spanText, mWildcardPattern, "geo:0,0?q=");
        }

        return spanText;
    }

    private static int indexFirstNonWhitespaceChar(CharSequence str) {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int indexLastNonWhitespaceChar(CharSequence str) {
        for (int i = str.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds North American Numbering Plan (NANP) phone numbers in the input text.
     *
     * @param text The text to scan.
     * @return A list of [start, end) pairs indicating the positions of phone numbers in the input.
     */
    // @VisibleForTesting
    static int[] findNanpPhoneNumbers(CharSequence text) {
        ArrayList<Integer> list = new ArrayList<Integer>();

        int startPos = 0;
        int endPos = text.length() - NANP_MIN_DIGITS + 1;
        if (endPos < 0) {
            return new int[]{};
        }

        /*
         * We can't just strip the whitespace out and crunch it down, because the whitespace
         * is significant.  March through, trying to figure out where numbers start and end.
         */
        while (startPos < endPos) {
            // skip whitespace
            while (Character.isWhitespace(text.charAt(startPos)) && startPos < endPos) {
                startPos++;
            }
            if (startPos == endPos) {
                break;
            }

            // check for a match at this position
            int matchEnd = findNanpMatchEnd(text, startPos);
            if (matchEnd > startPos) {
                list.add(startPos);
                list.add(matchEnd);
                startPos = matchEnd;    // skip past match
            } else {
                // skip to next whitespace char
                while (!Character.isWhitespace(text.charAt(startPos)) && startPos < endPos) {
                    startPos++;
                }
            }
        }

        int[] result = new int[list.size()];
        for (int i = list.size() - 1; i >= 0; i--) {
            result[i] = list.get(i);
        }
        return result;
    }

    /**
     * Checks to see if there is a valid phone number in the input, starting at the specified
     * offset.  If so, the index of the last character + 1 is returned.  The input is assumed
     * to begin with a non-whitespace character.
     *
     * @return Exclusive end position, or -1 if not a match.
     */
    private static int findNanpMatchEnd(CharSequence text, int startPos) {
        /*
         * A few interesting cases:
         *   94043                              # too short, ignore
         *   123456789012                       # too long, ignore
         *   +1 (650) 555-1212                  # 11 digits, spaces
         *   (650) 555 5555                     # Second space, only when first is present.
         *   (650) 555-1212, (650) 555-1213     # two numbers, return first
         *   1-650-555-1212                     # 11 digits with leading '1'
         *   *#650.555.1212#*!                  # 10 digits, include #*, ignore trailing '!'
         *   555.1212                           # 7 digits
         *
         * For the most part we want to break on whitespace, but it's common to leave a space
         * between the initial '1' and/or after the area code.
         */

        // Check for "tel:" URI prefix.
        if (text.length() > startPos + 4
                && text.subSequence(startPos, startPos + 4).toString().equalsIgnoreCase("tel:")) {
            startPos += 4;
        }

        int endPos = text.length();
        int curPos = startPos;
        int foundDigits = 0;
        char firstDigit = 'x';
        boolean foundWhiteSpaceAfterAreaCode = false;

        while (curPos <= endPos) {
            char ch;
            if (curPos < endPos) {
                ch = text.charAt(curPos);
            } else {
                ch = 27;    // fake invalid symbol at end to trigger loop break
            }

            if (Character.isDigit(ch)) {
                if (foundDigits == 0) {
                    firstDigit = ch;
                }
                foundDigits++;
                if (foundDigits > NANP_MAX_DIGITS) {
                    // too many digits, stop early
                    return -1;
                }
            } else if (Character.isWhitespace(ch)) {
                if ((firstDigit == '1' && foundDigits == 4) ||
                        (foundDigits == 3)) {
                    foundWhiteSpaceAfterAreaCode = true;
                } else if (firstDigit == '1' && foundDigits == 1) {
                } else if (foundWhiteSpaceAfterAreaCode
                        && ((firstDigit == '1' && (foundDigits == 7)) || (foundDigits == 6))) {
                } else {
                    break;
                }
            } else if (NANP_ALLOWED_SYMBOLS.indexOf(ch) == -1) {
                break;
            }
            // else it's an allowed symbol

            curPos++;
        }

        if ((firstDigit != '1' && (foundDigits == 7 || foundDigits == 10)) ||
                (firstDigit == '1' && foundDigits == 11)) {
            // match
            return curPos;
        }

        return -1;
    }

    /**
     * Determines whether a new span at [start,end) will overlap with any existing span.
     */
    private static boolean spanWillOverlap(Spannable spanText, URLSpan[] spanList, int start,
                                           int end) {
        if (start == end) {
            // empty span, ignore
            return false;
        }
        for (URLSpan span : spanList) {
            int existingStart = spanText.getSpanStart(span);
            int existingEnd = spanText.getSpanEnd(span);
            if ((start >= existingStart && start < existingEnd) ||
                    end > existingStart && end <= existingEnd) {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    CharSequence seq = spanText.subSequence(start, end);
                    Log.v(TAG, "Not linkifying " + seq + " as phone number due to overlap");
                }
                return true;
            }
        }

        return false;
    }

}

package com.crazyboss.alarmboss;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateFormat;

import java.util.Calendar;

/**
 * The single place for scheduling, dedupe and widget rules. The app AND
 * both widgets call these methods so the rules cannot drift apart.
 *
 * Toggle-or-create rule (user's core requirement): never create a second
 * alarm for a time that already exists -- flip the existing one instead.
 */
public final class AlarmHelper {
    private AlarmHelper() {
    }

    public static final String ACTION_ALARM = "com.crazyboss.alarmboss.ALARM";
    public static final String ACTION_SNOOZE = "com.crazyboss.alarmboss.SNOOZE";
    public static final String ACTION_QUICK_CHIP = "com.crazyboss.alarmboss.QUICK_CHIP";
    public static final String ACTION_CLOCK_ADJ = "com.crazyboss.alarmboss.CLOCK_ADJ";
    public static final String ACTION_CLOCK_SET = "com.crazyboss.alarmboss.CLOCK_SET";
    public static final String ACTION_SCHEDULE_TOGGLE =
            "com.crazyboss.alarmboss.SCHEDULE_TOGGLE";
    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_SNOOZED = "is_snooze_row";
    public static final String CHANNEL_ID = "alarmboss_alarms";

    private static final String PREFS = "alarmboss_prefs";
    private static final String KEY_GLOBAL_RINGTONE = "global_ringtone";
    private static final String KEY_FORCE_MAX = "force_max_volume";

    // ---- preferences -----------------------------------------------------

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String globalRingtoneUri(Context ctx) {
        return prefs(ctx).getString(KEY_GLOBAL_RINGTONE, null);
    }

    public static void setGlobalRingtoneUri(Context ctx, String uri) {
        prefs(ctx).edit().putString(KEY_GLOBAL_RINGTONE, uri).apply();
    }

    /** Default ON: the user wants the alarm as loud as his volume allows. */
    public static boolean forceMaxVolume(Context ctx) {
        return prefs(ctx).getBoolean(KEY_FORCE_MAX, true);
    }

    // ---- v1.5 customization settings (all default to prior behaviour) ----

    /** Group the main list by hour slot. Default true. */
    public static boolean groupByHour(Context ctx) {
        return prefs(ctx).getBoolean("group_by_hour", true);
    }

    public static void setGroupByHour(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("group_by_hour", on).apply();
    }

    private static int validChoice(int v, int fallback) {
        return (v == 5 || v == 10 || v == 15 || v == 30) ? v : fallback;
    }

    /** Snooze length in minutes. Default 10. */
    public static int snoozeMinutes(Context ctx) {
        return validChoice(prefs(ctx).getInt("snooze_minutes", 10), 10);
    }

    public static void setSnoozeMinutes(Context ctx, int minutes) {
        prefs(ctx).edit().putInt("snooze_minutes", validChoice(minutes, 10)).apply();
    }

    /** Auto-stop ringing after, in minutes. Default 10. */
    public static int autoStopMinutes(Context ctx) {
        return validChoice(prefs(ctx).getInt("auto_stop_minutes", 10), 10);
    }

    public static void setAutoStopMinutes(Context ctx, int minutes) {
        prefs(ctx).edit().putInt("auto_stop_minutes",
                validChoice(minutes, 10)).apply();
    }

    /** Vibrate while ringing. Default true. */
    public static boolean vibrateRinging(Context ctx) {
        return prefs(ctx).getBoolean("vibrate_ringing", true);
    }

    public static void setVibrateRinging(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("vibrate_ringing", on).apply();
    }

    /**
     * Time-format override: "system" (default), "12h" or "24h".
     * System = the existing DateFormat.is24HourFormat behaviour.
     */
    public static String timeFormatPref(Context ctx) {
        String v = prefs(ctx).getString("time_format", "system");
        return ("12h".equals(v) || "24h".equals(v)) ? v : "system";
    }

    public static void setTimeFormatPref(Context ctx, String value) {
        if (!"12h".equals(value) && !"24h".equals(value)) {
            value = "system";
        }
        prefs(ctx).edit().putString("time_format", value).apply();
    }

    /** Alarm list order: "time" (default) or "label". */
    public static String sortOrder(Context ctx) {
        String v = prefs(ctx).getString("sort_order", "time");
        return "label".equals(v) ? "label" : "time";
    }

    public static void setSortOrder(Context ctx, String value) {
        prefs(ctx).edit().putString("sort_order",
                "label".equals(value) ? "label" : "time").apply();
    }

    /** Show the Snooze button on the firing screen. Default true. */
    public static boolean allowSnooze(Context ctx) {
        return prefs(ctx).getBoolean("allow_snooze", true);
    }

    public static void setAllowSnooze(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("allow_snooze", on).apply();
    }

    public static void setForceMaxVolume(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_FORCE_MAX, on).apply();
    }

    // ---- formatting ------------------------------------------------------

    public static String formatTime(Context ctx, int hour, int minute) {
        String pref = timeFormatPref(ctx);
        boolean use24 = "24h".equals(pref)
                || ("system".equals(pref) && DateFormat.is24HourFormat(ctx));
        if (use24) {
            return String.format("%02d:%02d", hour, minute);
        }
        int h12 = hour % 12;
        if (h12 == 0) {
            h12 = 12;
        }
        return String.format("%d:%02d %s", h12, minute, hour < 12 ? "AM" : "PM");
    }

    private static String hourLabel12(int hour) {
        int h12 = hour % 12;
        return String.valueOf(h12 == 0 ? 12 : h12);
    }

    private static String hourSuffix(int hour) {
        return hour < 12 ? "AM" : "PM";
    }

    /**
     * Hour-slot header label in the app's time format, e.g. "8 – 9 AM"
     * in 12h mode or "8 – 9" in 24h mode, plus the alarm count.
     * The end hour wraps past midnight (23 -> "11 PM – 12 AM").
     */
    public static String slotHeaderLabel(Context ctx, int hour, int count) {
        String range;
        if ("24h".equals(timeFormatPref(ctx))
                || ("system".equals(timeFormatPref(ctx))
                && DateFormat.is24HourFormat(ctx))) {
            range = hour + " – " + ((hour + 1) % 24);
        } else if (hourSuffix(hour).equals(hourSuffix((hour + 1) % 24))) {
            range = hourLabel12(hour) + " – " + hourLabel12((hour + 1) % 24)
                    + " " + hourSuffix(hour);
        } else {
            range = hourLabel12(hour) + " " + hourSuffix(hour) + " – "
                    + hourLabel12((hour + 1) % 24) + " "
                    + hourSuffix((hour + 1) % 24);
        }
        return range + " • " + count + (count == 1 ? " alarm" : " alarms");
    }

    public static String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + "m";
        }
        if (minutes % 60 == 0) {
            return (minutes / 60) + "h";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static final String[] DAY_NAMES =
            {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public static String daysSummary(int days) {
        if (days == 0) {
            return "Once";
        }
        if (days == 0x7F) {
            return "Every day";
        }
        if (days == 0x1F) {
            return "Weekdays";
        }
        if (days == 0x60) {
            return "Weekends";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(DAY_NAMES[i]);
            }
        }
        return sb.toString();
    }

    /** Calendar.SUNDAY=1..SATURDAY=7 -> bit index (Mon=0 .. Sun=6). */
    public static int bitForCalendarDay(int calendarDay) {
        return (calendarDay + 5) % 7;
    }

    // ---- trigger computation ----------------------------------------------

    /** Next fire instant for an alarm, strictly after now where applicable. */
    public static long nextTrigger(Alarm a, long now) {
        if (a.triggerAt != 0) {
            // Exact one-time widget alarm: use its timestamp verbatim. A
            // past value fires immediately on schedule (missed alarm rings
            // on boot instead of being silently dropped).
            return a.triggerAt;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        if (a.days == 0) {
            if (c.getTimeInMillis() >= now) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_YEAR, 1);
            return c.getTimeInMillis();
        }
        for (int i = 0; i < 8; i++) {
            int bit = bitForCalendarDay(c.get(Calendar.DAY_OF_WEEK));
            if ((a.days & (1 << bit)) != 0 && c.getTimeInMillis() >= now) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    // ---- scheduling ---------------------------------------------------------

    private static PendingIntent alarmPendingIntent(Context ctx, long alarmId,
                                                    String action) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);
        return PendingIntent.getBroadcast(ctx, (int) alarmId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am =
                    (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    public static void schedule(Context ctx, Alarm a) {
        AlarmManager am =
                (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        long trigger = nextTrigger(a, System.currentTimeMillis());
        PendingIntent pi = alarmPendingIntent(ctx, a.id, ACTION_ALARM);
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            // Inexact fallback; MainActivity shows a banner for this case.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    public static void scheduleSnooze(Context ctx, long alarmId, int minutes) {
        scheduleSnoozeAt(ctx, alarmId,
                System.currentTimeMillis() + minutes * 60L * 1000L);
    }

    /** Schedule a snooze at an absolute time and persist it in the DB. */
    public static void scheduleSnoozeAt(Context ctx, long alarmId, long triggerMs) {
        AlarmManager am =
                (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = alarmPendingIntent(ctx, alarmId, ACTION_SNOOZE);
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        }
        AlarmDbHelper db = new AlarmDbHelper(ctx);
        Alarm a = db.get(alarmId);
        if (a != null) {
            a.snoozeUntil = triggerMs;
            db.update(a);
        }
        refreshAllWidgets(ctx);
    }

    /** Cancel only the pending snooze; NEVER toggles enabled. */
    public static void cancelSnooze(Context ctx, long alarmId) {
        AlarmManager am =
                (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(alarmPendingIntent(ctx, alarmId, ACTION_SNOOZE));
        }
        AlarmDbHelper db = new AlarmDbHelper(ctx);
        Alarm a = db.get(alarmId);
        if (a != null && a.snoozeUntil != 0) {
            a.snoozeUntil = 0;
            db.update(a);
        }
        refreshAllWidgets(ctx);
    }

    public static void cancel(Context ctx, Alarm a) {
        AlarmManager am =
                (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(alarmPendingIntent(ctx, a.id, ACTION_ALARM));
        am.cancel(alarmPendingIntent(ctx, a.id, ACTION_SNOOZE));
    }

    /** Flip enabled state in the DB and schedule/cancel to match. */
    public static void setEnabled(Context ctx, Alarm a, boolean enabled) {
        a.enabled = enabled;
        if (!enabled) {
            // Toggling off cancels any pending snooze too; cancel() below
            // already cancels its PendingIntent, this clears the DB row.
            a.snoozeUntil = 0;
        }
        new AlarmDbHelper(ctx).update(a);
        if (enabled) {
            schedule(ctx, a);
        } else {
            cancel(ctx, a);
        }
        refreshAllWidgets(ctx);
    }

    // ---- ringtone resolution ------------------------------------------------

    /** Alarm's own tone, else global, else the system default alarm tone. */
    public static Uri resolveRingtoneUri(Context ctx, Alarm a) {
        if (a != null && a.ringtoneUri != null) {
            return Uri.parse(a.ringtoneUri);
        }
        String global = globalRingtoneUri(ctx);
        if (global != null) {
            return Uri.parse(global);
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }

    public static String ringtoneTitle(Context ctx, String uriString) {
        if (uriString == null) {
            return "Global";
        }
        try {
            Ringtone r = RingtoneManager.getRingtone(ctx, Uri.parse(uriString));
            if (r != null) {
                String title = r.getTitle(ctx);
                if (title != null && !title.trim().isEmpty()) {
                    return title;
                }
            }
        } catch (Exception ignored) {
            // Fall through to the file-name lookup.
        }
        // Music files picked from storage: show the real file name.
        try {
            android.database.Cursor c = ctx.getContentResolver().query(
                    Uri.parse(uriString),
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String name = c.getString(0);
                        if (name != null && !name.trim().isEmpty()) {
                            return name;
                        }
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic label.
        }
        return "Custom";
    }

    public static String globalRingtoneTitle(Context ctx) {
        String uri = globalRingtoneUri(ctx);
        if (uri == null) {
            return "Default alarm tone";
        }
        return ringtoneTitle(ctx, uri);
    }

    /**
     * True if AlarmBoss can actually play this tone. Guards against the
     * silent-fallback case: a URI that looks valid but cannot be opened
     * must be refused at pick time, not discovered at alarm time.
     */
    /**
     * Playability gate: MediaPlayer prepare is the test. A RingtoneManager
     * lookup must NOT gate this — it returns null for Storage Access
     * Framework document URIs even when MediaPlayer can play them once
     * the persistable read grant is held. Genuinely unplayable URIs still
     * fail prepare and are refused.
     */
    public static boolean canPlayTone(Context ctx, String uriString) {
        if (uriString == null) {
            return false;
        }
        android.media.MediaPlayer mp = null;
        try {
            mp = new android.media.MediaPlayer();
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(ctx, Uri.parse(uriString));
            mp.prepare();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (mp != null) {
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Clock-widget time-format prefs: 0 = System, 1 = 12h, 2 = 24h. */
    public static int clockFormat(Context ctx, int widgetId) {
        int f = prefs(ctx).getInt("widget_clock_fmt_" + widgetId, 0);
        return (f >= 0 && f <= 2) ? f : 0;
    }

    public static void setClockFormat(Context ctx, int widgetId, int format) {
        prefs(ctx).edit().putInt("widget_clock_fmt_" + widgetId, format).apply();
    }

    /** Minute step for the clock widget M+/M- buttons. Default 5. */
    public static int clockMinuteStep(Context ctx, int widgetId) {
        int s = prefs(ctx).getInt("widget_clock_step_" + widgetId, 5);
        return (s == 1 || s == 5 || s == 15) ? s : 5;
    }

    public static void setClockMinuteStep(Context ctx, int widgetId, int step) {
        prefs(ctx).edit().putInt("widget_clock_step_" + widgetId, step).apply();
    }

    /** Clock-widget time text honouring the widget's format choice. */
    public static String formatClockTime(Context ctx, int hour, int minute, int format) {
        if (format == 2) {
            return String.format("%02d:%02d", hour, minute);
        }
        if (format == 1) {
            int h12 = hour % 12;
            if (h12 == 0) {
                h12 = 12;
            }
            return String.format("%d:%02d %s", h12, minute, hour < 12 ? "AM" : "PM");
        }
        return formatTime(ctx, hour, minute);
    }

    /** Footer text for both widgets: next upcoming enabled alarm. */
    public static String nextAlarmFooter(Context ctx, int format) {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (Alarm a : new AlarmDbHelper(ctx).getAll()) {
            if (!a.enabled) {
                continue;
            }
            long t = nextTrigger(a, now);
            if (t >= now && t < best) {
                best = t;
            }
        }
        if (best == Long.MAX_VALUE) {
            return "No alarms on";
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(best);
        return "Next: " + formatClockTime(ctx,
                c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE), format);
    }

    // ---- widget rules ---------------------------------------------------------

    public static class ToggleResult {
        public final boolean nowOn;
        public final boolean created;
        public final String timeText;

        public ToggleResult(boolean nowOn, boolean created, String timeText) {
            this.nowOn = nowOn;
            this.created = created;
            this.timeText = timeText;
        }
    }

    /** Target instant for a quick chip: now + duration, minute precision. */
    public static long quickTarget(int durationMinutes) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, durationMinutes);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int[] hourMinuteOf(long epochMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(epochMs);
        return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
    }

    /**
     * Quick chip tap: an existing one-time alarm at exactly this target is
     * toggled; otherwise a new one-time alarm is created with the global
     * ringtone. Never duplicates.
     */
    public static ToggleResult toggleOrCreateQuick(Context ctx, int durationMinutes) {
        long target = quickTarget(durationMinutes);
        AlarmDbHelper db = new AlarmDbHelper(ctx);
        Alarm existing = db.findQuickByTrigger(target);
        int[] hm = hourMinuteOf(target);
        String timeText = formatTime(ctx, hm[0], hm[1]);
        if (existing != null) {
            boolean nowOn = !existing.enabled;
            setEnabled(ctx, existing, nowOn);
            refreshAllWidgets(ctx);
            return new ToggleResult(nowOn, false, timeText);
        }
        Alarm a = new Alarm(hm[0], hm[1], "In " + formatDuration(durationMinutes),
                0, null, true, target);
        a.id = db.insert(a);
        schedule(ctx, a);
        refreshAllWidgets(ctx);
        return new ToggleResult(true, true, timeText);
    }

    /** Is there an enabled one-time alarm at exactly this target? (chip state) */
    public static boolean quickTargetIsOn(Context ctx, int durationMinutes) {
        Alarm existing = new AlarmDbHelper(ctx)
                .findQuickByTrigger(quickTarget(durationMinutes));
        return existing != null && existing.enabled;
    }

    /** Time-of-day one-time trigger: today if still ahead, else tomorrow. */
    public static long timeOfDayTrigger(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        if (c.getTimeInMillis() < System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    /**
     * Clock widget SET: a saved alarm with the same hour+minute (any days)
     * is toggled; otherwise a one-time alarm for that time of day is
     * created with the global ringtone. Never duplicates.
     */
    public static ToggleResult toggleOrCreateClock(Context ctx, int hour, int minute) {
        AlarmDbHelper db = new AlarmDbHelper(ctx);
        Alarm existing = db.findByTimeOfDay(hour, minute);
        String timeText = formatTime(ctx, hour, minute);
        if (existing != null) {
            boolean nowOn = !existing.enabled;
            if (existing.days == 0) {
                // Stale triggerAt: a re-enabled one-time alarm must ring at
                // its next occurrence, never instantly from an old timestamp.
                existing.triggerAt = timeOfDayTrigger(hour, minute);
                db.update(existing);
            }
            setEnabled(ctx, existing, nowOn);
            refreshAllWidgets(ctx);
            return new ToggleResult(nowOn, false, timeText);
        }
        Alarm a = new Alarm(hour, minute, "Widget alarm", 0, null, true,
                timeOfDayTrigger(hour, minute));
        a.id = db.insert(a);
        schedule(ctx, a);
        refreshAllWidgets(ctx);
        return new ToggleResult(true, true, timeText);
    }

    // ---- schedule occurrences (next 7 days, enabled alarms only) --------

    /** One scheduled firing of an alarm, for the schedule widget. */
    public static class Occurrence {
        public final int dayOffset; // 0 = today, 1..6 = later this week
        public final String dayLabel; // "Today" or a weekday name
        public final int hour;
        public final int minute;
        public final String label;
        public final long alarmId;
        public final long triggerMs;
        public final boolean snoozed;

        public Occurrence(int dayOffset, String dayLabel, int hour, int minute,
                          String label, long alarmId, long triggerMs,
                          boolean snoozed) {
            this.dayOffset = dayOffset;
            this.dayLabel = dayLabel;
            this.hour = hour;
            this.minute = minute;
            this.label = label;
            this.alarmId = alarmId;
            this.triggerMs = triggerMs;
            this.snoozed = snoozed;
        }
    }

    /** Day offset of t from today (0..6), or -1 outside the window. */
    private static int dayOffsetOf(long todayStart, long t) {
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(todayStart);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        for (int i = 0; i <= 6; i++) {
            if (base.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                    && base.get(Calendar.DAY_OF_YEAR)
                    == c.get(Calendar.DAY_OF_YEAR)) {
                return i;
            }
            base.add(Calendar.DAY_OF_YEAR, 1);
        }
        return -1;
    }

    private static String dayLabelFor(int offset, long t) {
        if (offset == 0) {
            return "Today";
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return DAY_NAMES[bitForCalendarDay(c.get(Calendar.DAY_OF_WEEK))];
    }

    /**
     * Every firing of every ENABLED alarm in the next 7 days, plus every
     * pending snooze (any alarm, enabled or not), sorted by trigger time. Rules: one-time alarm with triggerAt != 0 counts
     * iff triggerAt >= now; one-time alarm with triggerAt == 0 counts at
     * its next time-of-day occurrence; repeat alarms count on each
     * matching day in the window whose date+time >= now. Disabled alarms
     * are never listed (they are not scheduled).
     */
    public static java.util.List<Occurrence> occurrencesForNext7Days(
            Context ctx, long now) {
        java.util.List<Occurrence> out = new java.util.ArrayList<>();
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayStart = today.getTimeInMillis();
        for (Alarm a : new AlarmDbHelper(ctx).getAll()) {
            String label = (a.label == null || a.label.isEmpty())
                    ? "Alarm" : a.label;
            // Pending snoozes are listed for EVERY alarm, enabled or not:
            // a snoozed one-time alarm is disabled in the DB at fire time,
            // so without this its snooze would be invisible to the widget.
            if (a.snoozeUntil >= now) {
                int offset = dayOffsetOf(todayStart, a.snoozeUntil);
                if (offset >= 0) {
                    Calendar sc = Calendar.getInstance();
                    sc.setTimeInMillis(a.snoozeUntil);
                    out.add(new Occurrence(offset,
                            dayLabelFor(offset, a.snoozeUntil),
                            sc.get(Calendar.HOUR_OF_DAY),
                            sc.get(Calendar.MINUTE),
                            label, a.id, a.snoozeUntil, true));
                }
            }
            if (!a.enabled) {
                continue;
            }
            if (a.triggerAt != 0) {
                if (a.triggerAt < now) {
                    continue;
                }
                int offset = dayOffsetOf(todayStart, a.triggerAt);
                if (offset < 0) {
                    continue;
                }
                out.add(new Occurrence(offset, dayLabelFor(offset, a.triggerAt),
                        a.hour, a.minute, label, a.id, a.triggerAt, false));
            } else if (a.days == 0) {
                long t = timeOfDayTrigger(a.hour, a.minute);
                if (t < now) {
                    continue;
                }
                int offset = dayOffsetOf(todayStart, t);
                if (offset < 0) {
                    continue;
                }
                out.add(new Occurrence(offset, dayLabelFor(offset, t),
                        a.hour, a.minute, label, a.id, t, false));
            } else {
                for (int offset = 0; offset <= 6; offset++) {
                    Calendar d = (Calendar) today.clone();
                    d.add(Calendar.DAY_OF_YEAR, offset);
                    int bit = bitForCalendarDay(d.get(Calendar.DAY_OF_WEEK));
                    if ((a.days & (1 << bit)) == 0) {
                        continue;
                    }
                    d.set(Calendar.HOUR_OF_DAY, a.hour);
                    d.set(Calendar.MINUTE, a.minute);
                    d.set(Calendar.SECOND, 0);
                    d.set(Calendar.MILLISECOND, 0);
                    long t = d.getTimeInMillis();
                    if (t < now) {
                        continue;
                    }
                    out.add(new Occurrence(offset, dayLabelFor(offset, t),
                            a.hour, a.minute, label, a.id, t, false));
                }
            }
        }
        java.util.Collections.sort(out,
                new java.util.Comparator<Occurrence>() {
                    @Override
                    public int compare(Occurrence o1, Occurrence o2) {
                        if (o1.triggerMs != o2.triggerMs) {
                            return o1.triggerMs < o2.triggerMs ? -1 : 1;
                        }
                        return 0;
                    }
                });
        return out;
    }

    // ---- widget refresh (real-time schedule updates) ----------------------

    /**
     * Refresh every AlarmBoss widget immediately, from any mutation point.
     * This is a direct AppWidgetManager update, NOT a broadcast: custom
     * implicit broadcasts do not reach manifest-registered receivers when
     * the app is in the background on Android 8+. Rendering only — it
     * never mutates alarms, so no update loop is possible.
     */
    public static void refreshAllWidgets(Context ctx) {
        try {
            android.appwidget.AppWidgetManager manager =
                    android.appwidget.AppWidgetManager.getInstance(ctx);
            if (manager == null) {
                return;
            }
            int[] scheduleIds = manager.getAppWidgetIds(
                    new android.content.ComponentName(ctx,
                            ScheduleWidgetProvider.class));
            int[] clockIds = manager.getAppWidgetIds(
                    new android.content.ComponentName(ctx,
                            ClockWidgetProvider.class));
            int[] quickIds = manager.getAppWidgetIds(
                    new android.content.ComponentName(ctx,
                            QuickWidgetProvider.class));
            for (int id : scheduleIds) {
                ScheduleWidgetProvider.updateWidget(ctx, manager, id);
            }
            for (int id : clockIds) {
                ClockWidgetProvider.updateWidget(ctx, manager, id);
            }
            for (int id : quickIds) {
                QuickWidgetProvider.updateWidget(ctx, manager, id);
            }
            if (scheduleIds.length > 0) {
                manager.notifyAppWidgetViewDataChanged(scheduleIds,
                        R.id.schedule_list);
            }
        } catch (Exception ignored) {
            // Refresh must never break a mutation path.
        }
    }

    // ---- per-widget stored state -------------------------------------------

    public static int[] quickDurations(Context ctx, int widgetId) {
        String csv = prefs(ctx).getString("widget_quick_" + widgetId, null);
        if (csv == null || csv.isEmpty()) {
            return new int[]{30, 60, 120, 180};
        }
        String[] parts = csv.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 60;
            }
        }
        return out;
    }

    public static void setQuickDurations(Context ctx, int widgetId, int[] minutes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < minutes.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(minutes[i]);
        }
        prefs(ctx).edit().putString("widget_quick_" + widgetId, sb.toString()).apply();
    }

    /** Clock widget pending time; default = now rounded up to the next 5 min. */
    public static int[] clockTime(Context ctx, int widgetId) {
        SharedPreferences p = prefs(ctx);
        if (!p.contains("widget_clock_h_" + widgetId)) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            int m = c.get(Calendar.MINUTE);
            int rem = m % 5;
            if (rem != 0) {
                c.add(Calendar.MINUTE, 5 - rem);
            }
            return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
        }
        return new int[]{
                p.getInt("widget_clock_h_" + widgetId, 7),
                p.getInt("widget_clock_m_" + widgetId, 0)};
    }

    public static void setClockTime(Context ctx, int widgetId, int hour, int minute) {
        prefs(ctx).edit()
                .putInt("widget_clock_h_" + widgetId, hour)
                .putInt("widget_clock_m_" + widgetId, minute)
                .apply();
    }

    public static void clearWidgetPrefs(Context ctx, int widgetId) {
        prefs(ctx).edit()
                .remove("widget_quick_" + widgetId)
                .remove("widget_clock_h_" + widgetId)
                .remove("widget_clock_m_" + widgetId)
                .remove("widget_clock_fmt_" + widgetId)
                .remove("widget_clock_step_" + widgetId)
                .apply();
    }

    // ---- fallback notification (when FGS start is refused) -------------------

    public static void ensureChannel(Context ctx) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Alarms",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("AlarmBoss alarm alerts");
        nm.createNotificationChannel(ch);
    }

    /** Full-screen notification for an alarm, used by the service and fallback. */
    public static Notification buildAlarmNotification(Context ctx, Alarm a) {
        ensureChannel(ctx);
        Intent intent = new Intent(ctx, AlarmActivity.class);
        intent.putExtra(EXTRA_ALARM_ID, a.id);
        PendingIntent fullScreen = PendingIntent.getActivity(ctx, (int) a.id,
                intent, PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
        String label = (a.label == null || a.label.isEmpty()) ? "Alarm" : a.label;
        return new Notification.Builder(ctx, CHANNEL_ID)
                .setContentTitle(label)
                .setContentText(formatTime(ctx, a.hour, a.minute))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreen, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }

    public static void postAlarmNotification(Context ctx, Alarm a) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) a.id, buildAlarmNotification(ctx, a));
        }
    }
}

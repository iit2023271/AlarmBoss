package com.crazyboss.alarmboss;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;

/**
 * Clock widget: H+/H-/M+/M- (minute step 5, with hour carry) and SET.
 * SET toggles a saved alarm with the same hour+minute, or creates a
 * one-time alarm for that time of day with the global ringtone. No
 * ringtone choice here by design -- it always uses the global ringtone.
 */
public class ClockWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int id : widgetIds) {
            updateWidget(context, manager, id);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager manager,
                                    int widgetId) {
        int[] hm = AlarmHelper.clockTime(context, widgetId);
        int format = AlarmHelper.clockFormat(context, widgetId);
        String timeText = AlarmHelper.formatClockTime(context, hm[0], hm[1], format);
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_clock);
        views.setTextViewText(R.id.clock_time, timeText);
        // Visible state for the displayed time: SET toggling ON/OFF must be
        // readable on the widget itself (Toasts are unreliable on Honor).
        Alarm existing = new AlarmDbHelper(context).findByTimeOfDay(hm[0], hm[1]);
        String status;
        if (existing == null) {
            status = "No alarm at " + timeText;
        } else if (existing.enabled) {
            status = "ON • " + timeText;
        } else {
            status = "OFF • " + timeText;
        }
        views.setTextViewText(R.id.clock_status, status);
        views.setTextViewText(R.id.clock_next,
                AlarmHelper.nextAlarmFooter(context, format));
        // Tapping the time or status opens the app.
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context,
                widgetId * 10 + 8, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.clock_time, openPi);
        views.setOnClickPendingIntent(R.id.clock_status, openPi);
        views.setOnClickPendingIntent(R.id.clock_next, openPi);
        views.setOnClickPendingIntent(R.id.btn_h_minus,
                adjIntent(context, widgetId, "h", -1, 1));
        views.setOnClickPendingIntent(R.id.btn_h_plus,
                adjIntent(context, widgetId, "h", 1, 2));
        views.setOnClickPendingIntent(R.id.btn_m_minus,
                adjIntent(context, widgetId, "m", -1, 3));
        views.setOnClickPendingIntent(R.id.btn_m_plus,
                adjIntent(context, widgetId, "m", 1, 4));
        Intent set = new Intent(context, ClockWidgetProvider.class);
        set.setAction(AlarmHelper.ACTION_CLOCK_SET);
        set.putExtra("widget_id", widgetId);
        views.setOnClickPendingIntent(R.id.btn_set, PendingIntent.getBroadcast(
                context, widgetId * 10 + 9, set,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(widgetId, views);
    }

    private static PendingIntent adjIntent(Context context, int widgetId,
                                           String field, int delta, int slot) {
        Intent intent = new Intent(context, ClockWidgetProvider.class);
        intent.setAction(AlarmHelper.ACTION_CLOCK_ADJ);
        intent.putExtra("widget_id", widgetId);
        intent.putExtra("field", field);
        intent.putExtra("delta", delta);
        return PendingIntent.getBroadcast(context, widgetId * 10 + slot, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int widgetId = intent.getIntExtra("widget_id", -1);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (AlarmHelper.ACTION_CLOCK_ADJ.equals(action) && widgetId >= 0) {
            int[] hm = AlarmHelper.clockTime(context, widgetId);
            int hour = hm[0];
            int minute = hm[1];
            String field = intent.getStringExtra("field");
            int delta = intent.getIntExtra("delta", 0);
            if ("h".equals(field)) {
                hour = (hour + delta + 24) % 24;
            } else if ("m".equals(field)) {
                int step = AlarmHelper.clockMinuteStep(context, widgetId);
                minute += delta * step;
                while (minute >= 60) {
                    minute -= 60;
                    hour = (hour + 1) % 24;
                }
                while (minute < 0) {
                    minute += 60;
                    hour = (hour + 23) % 24;
                }
            }
            AlarmHelper.setClockTime(context, widgetId, hour, minute);
            updateWidget(context, manager, widgetId);
            return;
        }
        if (AlarmHelper.ACTION_CLOCK_SET.equals(action) && widgetId >= 0) {
            int[] hm = AlarmHelper.clockTime(context, widgetId);
            AlarmHelper.ToggleResult r =
                    AlarmHelper.toggleOrCreateClock(context, hm[0], hm[1]);
            updateWidget(context, manager, widgetId);
            String msg;
            if (r.created) {
                msg = "Alarm created: " + r.timeText;
            } else if (r.nowOn) {
                msg = "Alarm on: " + r.timeText;
            } else {
                msg = "Alarm off: " + r.timeText;
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        for (int id : widgetIds) {
            AlarmHelper.clearWidgetPrefs(context, id);
        }
    }
}

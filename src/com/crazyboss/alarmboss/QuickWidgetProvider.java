package com.crazyboss.alarmboss;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

/**
 * Quick widget: up to 4 duration chips (configurable). Tapping a chip
 * toggles the one-time alarm at now + duration, or creates it with the
 * global ringtone. Never duplicates.
 */
public class QuickWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int id : widgetIds) {
            updateWidget(context, manager, id);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager manager,
                                    int widgetId) {
        int[] durations = AlarmHelper.quickDurations(context, widgetId);
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_quick);
        int[] chips = {R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4};
        for (int i = 0; i < chips.length; i++) {
            if (i < durations.length) {
                boolean on = AlarmHelper.quickTargetIsOn(context, durations[i]);
                views.setTextViewText(chips[i],
                        AlarmHelper.formatDuration(durations[i])
                                + (on ? " • ON" : " • OFF"));
                views.setViewVisibility(chips[i], View.VISIBLE);
                Intent intent = new Intent(context, QuickWidgetProvider.class);
                intent.setAction(AlarmHelper.ACTION_QUICK_CHIP);
                intent.putExtra("widget_id", widgetId);
                intent.putExtra("chip_index", i);
                PendingIntent pi = PendingIntent.getBroadcast(context,
                        widgetId * 10 + i, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(chips[i], pi);
            } else {
                views.setViewVisibility(chips[i], View.GONE);
            }
        }
        views.setTextViewText(R.id.quick_next,
                AlarmHelper.nextAlarmFooter(context, 0));
        // Tapping the title or footer opens the app.
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context,
                widgetId * 10 + 8, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.quick_label, openPi);
        views.setOnClickPendingIntent(R.id.quick_next, openPi);
        manager.updateAppWidget(widgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AlarmHelper.ACTION_QUICK_CHIP.equals(intent.getAction())) {
            int widgetId = intent.getIntExtra("widget_id", -1);
            int chipIndex = intent.getIntExtra("chip_index", -1);
            int[] durations = AlarmHelper.quickDurations(context, widgetId);
            if (widgetId >= 0 && chipIndex >= 0 && chipIndex < durations.length) {
                AlarmHelper.ToggleResult r = AlarmHelper.toggleOrCreateQuick(
                        context, durations[chipIndex]);
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                updateWidget(context, manager, widgetId);
                String msg;
                if (r.created) {
                    msg = "Quick alarm created: " + r.timeText;
                } else if (r.nowOn) {
                    msg = "Quick alarm on: " + r.timeText;
                } else {
                    msg = "Quick alarm off: " + r.timeText;
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
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

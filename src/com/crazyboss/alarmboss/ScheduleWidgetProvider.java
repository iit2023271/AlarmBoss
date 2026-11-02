package com.crazyboss.alarmboss;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * Schedule widget: today's upcoming alarms plus this week's, in one
 * simple scrolling list. Row tap toggles that alarm (via a fill-in
 * intent); the title and the empty view open the app. Content comes
 * from ScheduleWidgetService; freshness comes from
 * AlarmHelper.refreshAllWidgets, called at every mutation point.
 */
public class ScheduleWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int id : widgetIds) {
            updateWidget(context, manager, id);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager manager,
                                    int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_schedule);

        // Title opens the app.
        Intent open = new Intent(context, MainActivity.class);
        views.setOnClickPendingIntent(R.id.schedule_title,
                PendingIntent.getActivity(context, widgetId, open,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE));

        // Collection adapter. A unique data URI per widget id keeps the
        // factory intents distinct across widget instances.
        Intent svc = new Intent(context, ScheduleWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse("alarmboss://schedule/" + widgetId));
        views.setRemoteAdapter(R.id.schedule_list, svc);
        views.setEmptyView(R.id.schedule_list, R.id.schedule_empty);

        // Empty view opens the app too.
        Intent openEmpty = new Intent(context, MainActivity.class);
        views.setOnClickPendingIntent(R.id.schedule_empty,
                PendingIntent.getActivity(context, widgetId + 100000, openEmpty,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE));

        // Row taps use the fill-in intent template; each row supplies its
        // alarm id. The toggle itself is handled in onReceive below.
        Intent tpl = new Intent(context, ScheduleWidgetProvider.class);
        tpl.setAction(AlarmHelper.ACTION_SCHEDULE_TOGGLE);
        views.setPendingIntentTemplate(R.id.schedule_list,
                PendingIntent.getBroadcast(context, widgetId, tpl,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE));

        manager.updateAppWidget(widgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AlarmHelper.ACTION_SCHEDULE_TOGGLE.equals(intent.getAction())) {
            long alarmId = intent.getLongExtra(AlarmHelper.EXTRA_ALARM_ID, -1);
            if (alarmId >= 0) {
                if (intent.getBooleanExtra(AlarmHelper.EXTRA_SNOOZED, false)) {
                    // Snooze row: cancel ONLY the snooze. Never toggle
                    // enabled — a snoozed one-time alarm is disabled in
                    // the DB, and flipping it on would schedule stale time.
                    AlarmHelper.cancelSnooze(context, alarmId);
                } else {
                    Alarm a = new AlarmDbHelper(context).get(alarmId);
                    if (a != null) {
                        // setEnabled refreshes all widgets, which re-queries
                        // this list — the toggled alarm visibly appears or
                        // disappears right away.
                        AlarmHelper.setEnabled(context, a, !a.enabled);
                    }
                }
            }
            return;
        }
        super.onReceive(context, intent);
    }
}

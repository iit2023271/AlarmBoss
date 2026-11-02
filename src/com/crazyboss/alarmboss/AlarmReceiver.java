package com.crazyboss.alarmboss;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fires when an alarm (or a snooze) goes off.
 * Bookkeeping happens HERE, before the service starts, so a later dismiss
 * or snooze can never double-schedule: one-time alarms are disabled at
 * fire time, repeat alarms are rescheduled to their next day at fire time.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmHelper.EXTRA_ALARM_ID, -1);
        if (alarmId < 0) {
            return;
        }
        String action = intent.getAction();
        if (AlarmHelper.ACTION_SNOOZE.equals(action)) {
            // The snooze is consumed when it rings: clear it from the DB
            // (so no leftover row) and refresh the widget BEFORE ringing.
            // The original alarm's state was already settled at first fire.
            AlarmDbHelper db = new AlarmDbHelper(context);
            Alarm snoozed = db.get(alarmId);
            if (snoozed != null && snoozed.snoozeUntil != 0) {
                snoozed.snoozeUntil = 0;
                db.update(snoozed);
            }
            AlarmHelper.refreshAllWidgets(context);
            startService(context, alarmId);
            return;
        }
        AlarmDbHelper db = new AlarmDbHelper(context);
        Alarm a = db.get(alarmId);
        if (a == null || !a.enabled) {
            return;
        }
        if (a.isOneTime()) {
            a.enabled = false;
            db.update(a);
        } else {
            AlarmHelper.schedule(context, a);
        }
        AlarmHelper.refreshAllWidgets(context);
        startService(context, alarmId);
    }

    private void startService(Context context, long alarmId) {
        Intent service = new Intent(context, AlarmService.class);
        service.putExtra(AlarmHelper.EXTRA_ALARM_ID, alarmId);
        try {
            context.startForegroundService(service);
        } catch (Exception e) {
            // On some devices a background FGS start is refused. Fall back
            // to posting the full-screen notification directly; opening it
            // (or tapping it) starts the service from AlarmActivity.
            Alarm a = new AlarmDbHelper(context).get(alarmId);
            if (a != null) {
                AlarmHelper.postAlarmNotification(context, a);
            }
        }
    }
}

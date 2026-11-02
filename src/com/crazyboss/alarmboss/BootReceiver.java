package com.crazyboss.alarmboss;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** After a reboot, reschedule every alarm that is still enabled. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        try {
            List<Alarm> alarms = new AlarmDbHelper(context).getAll();
            for (Alarm a : alarms) {
                if (a.enabled) {
                    AlarmHelper.schedule(context, a);
                }
            }
            // Re-arm pending snoozes; drop stale ones so no phantom row
            // survives a reboot. scheduleSnoozeAt re-persists + refreshes.
            long now = System.currentTimeMillis();
            AlarmDbHelper db = new AlarmDbHelper(context);
            for (Alarm a : alarms) {
                if (a.snoozeUntil > now) {
                    AlarmHelper.scheduleSnoozeAt(context, a.id, a.snoozeUntil);
                } else if (a.snoozeUntil != 0) {
                    a.snoozeUntil = 0;
                    db.update(a);
                }
            }
            AlarmHelper.refreshAllWidgets(context);
        } catch (Exception ignored) {
            // Never crash the boot sequence; the user can re-enable manually.
        }
    }
}

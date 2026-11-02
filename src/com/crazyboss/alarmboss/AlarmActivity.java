package com.crazyboss.alarmboss;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * Full-screen alarm screen shown over the lock screen. Back is disabled
 * on purpose: the user must choose Dismiss or Snooze.
 */
public class AlarmActivity extends Activity {
    private long alarmId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_alarm);

        alarmId = getIntent().getLongExtra(AlarmHelper.EXTRA_ALARM_ID, -1);
        Alarm a = alarmId >= 0 ? new AlarmDbHelper(this).get(alarmId) : null;

        TextView timeView = findViewById(R.id.alarm_time_big);
        TextView labelView = findViewById(R.id.alarm_label_big);
        if (a != null) {
            timeView.setText(AlarmHelper.formatTime(this, a.hour, a.minute));
            labelView.setText((a.label == null || a.label.isEmpty())
                    ? "Alarm" : a.label);
        } else {
            timeView.setText("");
            labelView.setText("Alarm");
        }

        // Make sure audio is playing even if this activity (not the service)
        // was the entry point (notification tapped after an FGS refusal).
        if (alarmId >= 0) {
            Intent service = new Intent(this, AlarmService.class);
            service.putExtra(AlarmHelper.EXTRA_ALARM_ID, alarmId);
            try {
                startForegroundService(service);
            } catch (Exception ignored) {
                // Already ringing, or the service is stopping; UI still works.
            }
        }

        Button dismiss = findViewById(R.id.dismiss_button);
        dismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(AlarmActivity.this, AlarmService.class));
                finish();
            }
        });
        Button snooze = findViewById(R.id.snooze_button);
        final int snoozeMinutes = AlarmHelper.snoozeMinutes(this);
        snooze.setText("Snooze " + snoozeMinutes + " min");
        if (!AlarmHelper.allowSnooze(this)) {
            snooze.setVisibility(View.GONE);
        }
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (alarmId >= 0) {
                    AlarmHelper.scheduleSnooze(AlarmActivity.this, alarmId, snoozeMinutes);
                }
                stopService(new Intent(AlarmActivity.this, AlarmService.class));
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Disabled: choose Dismiss or Snooze instead of backing out while
        // the alarm is still ringing.
    }
}

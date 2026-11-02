package com.crazyboss.alarmboss;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * Add / edit one alarm. Saving NEVER duplicates: same hour + minute +
 * days as an existing alarm just enables that alarm instead.
 */
public class AddEditActivity extends Activity {
    private static final int REQ_RINGTONE = 1001;
    private static final int REQ_FILE = 1005;

    private long alarmId = -1;
    private TimePicker timePicker;
    private EditText labelInput;
    private CheckBox[] dayBoxes;
    private CheckBox globalCheck;
    private Button chooseButton;
    private TextView ringtoneName;
    private String chosenRingtoneUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit);

        alarmId = getIntent().getLongExtra(AlarmHelper.EXTRA_ALARM_ID, -1);
        timePicker = findViewById(R.id.time_picker);
        timePicker.setIs24HourView(DateFormat.is24HourFormat(this));
        labelInput = findViewById(R.id.label_input);
        dayBoxes = new CheckBox[]{
                findViewById(R.id.day_mon), findViewById(R.id.day_tue),
                findViewById(R.id.day_wed), findViewById(R.id.day_thu),
                findViewById(R.id.day_fri), findViewById(R.id.day_sat),
                findViewById(R.id.day_sun)};
        globalCheck = findViewById(R.id.global_ringtone_check);
        chooseButton = findViewById(R.id.choose_ringtone_button);
        ringtoneName = findViewById(R.id.ringtone_name);

        globalCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                chooseButton.setEnabled(!isChecked);
                refreshRingtoneLine();
            }
        });
        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TonePicker.showChoice(AddEditActivity.this,
                        new Runnable() {
                            @Override
                            public void run() {
                                TonePicker.startSystemPicker(AddEditActivity.this,
                                        REQ_RINGTONE, "Choose alarm sound",
                                        chosenRingtoneUri);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                TonePicker.startFilePicker(AddEditActivity.this,
                                        REQ_FILE);
                            }
                        });
            }
        });

        if (alarmId != -1) {
            Alarm a = new AlarmDbHelper(this).get(alarmId);
            if (a != null) {
                timePicker.setHour(a.hour);
                timePicker.setMinute(a.minute);
                labelInput.setText(a.label == null ? "" : a.label);
                for (int i = 0; i < 7; i++) {
                    dayBoxes[i].setChecked((a.days & (1 << i)) != 0);
                }
                if (a.ringtoneUri != null) {
                    chosenRingtoneUri = a.ringtoneUri;
                    globalCheck.setChecked(false);
                    ringtoneName.setText(
                            AlarmHelper.ringtoneTitle(this, chosenRingtoneUri));
                }
                findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
            } else {
                alarmId = -1;
            }
        }
        refreshRingtoneLine();

        findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Alarm a = new AlarmDbHelper(AddEditActivity.this).get(alarmId);
                if (a != null) {
                    AlarmHelper.cancel(AddEditActivity.this, a);
                    new AlarmDbHelper(AddEditActivity.this).delete(alarmId);
                    AlarmHelper.refreshAllWidgets(AddEditActivity.this);
                }
                finish();
            }
        });
    }

    /** One place for the ringtone line: "Global: <name>" or the own tone. */
    private void refreshRingtoneLine() {
        if (globalCheck.isChecked()) {
            ringtoneName.setText("Global: "
                    + AlarmHelper.globalRingtoneTitle(this));
        } else if (chosenRingtoneUri != null) {
            ringtoneName.setText(
                    AlarmHelper.ringtoneTitle(this, chosenRingtoneUri));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_RINGTONE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                if (!AlarmHelper.canPlayTone(this, uri.toString())) {
                    Toast.makeText(this,
                            "This sound can't be played by AlarmBoss — pick a system tone",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                chosenRingtoneUri = uri.toString();
                globalCheck.setChecked(false);
                ringtoneName.setText(
                        AlarmHelper.ringtoneTitle(this, chosenRingtoneUri));
            }
        }
        if (requestCode == REQ_FILE && resultCode == RESULT_OK && data != null) {
            String picked = TonePicker.handleFileResult(this, data);
            if (picked != null) {
                chosenRingtoneUri = picked;
                globalCheck.setChecked(false);
                ringtoneName.setText(
                        AlarmHelper.ringtoneTitle(this, chosenRingtoneUri));
            } else {
                Toast.makeText(this,
                        "This sound can't be played by AlarmBoss — pick a system tone",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void save() {
        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();
        String label = labelInput.getText().toString().trim();
        int days = 0;
        for (int i = 0; i < 7; i++) {
            if (dayBoxes[i].isChecked()) {
                days |= (1 << i);
            }
        }
        String ringtoneUri = globalCheck.isChecked() ? null : chosenRingtoneUri;

        AlarmDbHelper db = new AlarmDbHelper(this);
        Alarm match = db.findByTimeAndDays(hour, minute, days, alarmId);
        if (match != null) {
            // Dedupe: enable the existing alarm instead of creating a copy.
            // In edit mode the edited row is removed (it was merged).
            AlarmHelper.setEnabled(this, match, true);
            if (alarmId != -1) {
                Alarm edited = db.get(alarmId);
                if (edited != null) {
                    AlarmHelper.cancel(this, edited);
                    db.delete(alarmId);
                }
            }
            // setEnabled already refreshed, but the merged-row delete above
            // changed the list again, so refresh once more.
            AlarmHelper.refreshAllWidgets(this);
            Toast.makeText(this, R.string.already_exists, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (alarmId == -1) {
            Alarm a = new Alarm(hour, minute, label, days, ringtoneUri, true, 0);
            a.id = db.insert(a);
            AlarmHelper.schedule(this, a);
            AlarmHelper.refreshAllWidgets(this);
            Toast.makeText(this, "Alarm set: "
                    + AlarmHelper.formatTime(this, hour, minute),
                    Toast.LENGTH_SHORT).show();
        } else {
            Alarm a = db.get(alarmId);
            if (a == null) {
                finish();
                return;
            }
            a.hour = hour;
            a.minute = minute;
            a.label = label;
            a.days = days;
            a.ringtoneUri = ringtoneUri;
            // A time-of-day edit drops any old exact trigger.
            a.triggerAt = 0;
            db.update(a);
            if (a.enabled) {
                AlarmHelper.cancel(this, a);
                AlarmHelper.schedule(this, a);
            }
            AlarmHelper.refreshAllWidgets(this);
            Toast.makeText(this, "Alarm saved", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}

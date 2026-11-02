package com.crazyboss.alarmboss;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/** Global ringtone, volume policy, bulk ringtone, permission shortcuts. */
public class SettingsActivity extends Activity {
    private static final int REQ_GLOBAL_RINGTONE = 1002;
    private static final int REQ_GLOBAL_FILE = 1004;

    private TextView globalName;
    private CheckBox forceMaxCheck;
    private Button previewButton;
    private MediaPlayer previewPlayer;
    private final Handler previewHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        globalName = findViewById(R.id.global_ringtone_name);
        forceMaxCheck = findViewById(R.id.force_max_check);
        Button pickButton = findViewById(R.id.pick_global_button);

        refreshGlobalName();
        forceMaxCheck.setChecked(AlarmHelper.forceMaxVolume(this));
        forceMaxCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AlarmHelper.setForceMaxVolume(SettingsActivity.this, isChecked);
            }
        });

        pickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TonePicker.showChoice(SettingsActivity.this,
                        new Runnable() {
                            @Override
                            public void run() {
                                String existing = AlarmHelper.globalRingtoneUri(
                                        SettingsActivity.this);
                                TonePicker.startSystemPicker(SettingsActivity.this,
                                        REQ_GLOBAL_RINGTONE,
                                        "Choose global alarm sound", existing);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                TonePicker.startFilePicker(SettingsActivity.this,
                                        REQ_GLOBAL_FILE);
                            }
                        });
            }
        });

        previewButton = findViewById(R.id.preview_global_button);
        previewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePreview();
            }
        });

        findViewById(R.id.apply_all_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int n = new AlarmDbHelper(SettingsActivity.this).applyGlobalToAll();
                Toast.makeText(SettingsActivity.this,
                        "Global ringtone applied to " + n + " alarm(s)",
                        Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.exact_alarm_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startActivity(new Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                } else {
                    Toast.makeText(SettingsActivity.this,
                            "Not needed on this Android version",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.battery_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(SettingsActivity.this,
                            "Battery settings could not be opened on this device",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // ---- v1.5 Customization section ---------------------------------
        CheckBox groupCheck = findViewById(R.id.group_by_hour_check);
        groupCheck.setChecked(AlarmHelper.groupByHour(this));
        groupCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AlarmHelper.setGroupByHour(SettingsActivity.this, isChecked);
            }
        });

        CheckBox vibrateCheck = findViewById(R.id.vibrate_ringing_check);
        vibrateCheck.setChecked(AlarmHelper.vibrateRinging(this));
        vibrateCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AlarmHelper.setVibrateRinging(SettingsActivity.this, isChecked);
            }
        });

        CheckBox allowSnoozeCheck = findViewById(R.id.allow_snooze_check);
        allowSnoozeCheck.setChecked(AlarmHelper.allowSnooze(this));
        allowSnoozeCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AlarmHelper.setAllowSnooze(SettingsActivity.this, isChecked);
            }
        });

        setupMinutesSpinner(R.id.snooze_minutes_spinner,
                AlarmHelper.snoozeMinutes(this),
                new MinutesSaver() {
                    @Override
                    public void save(int minutes) {
                        AlarmHelper.setSnoozeMinutes(SettingsActivity.this, minutes);
                    }
                });
        setupMinutesSpinner(R.id.auto_stop_spinner,
                AlarmHelper.autoStopMinutes(this),
                new MinutesSaver() {
                    @Override
                    public void save(int minutes) {
                        AlarmHelper.setAutoStopMinutes(SettingsActivity.this, minutes);
                    }
                });

        final String[] formatLabels = {"System", "12h", "24h"};
        final String[] formatValues = {"system", "12h", "24h"};
        Spinner formatSpinner = findViewById(R.id.time_format_spinner);
        formatSpinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, formatLabels));
        formatSpinner.setSelection(indexOf(formatValues,
                AlarmHelper.timeFormatPref(this)));
        formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                AlarmHelper.setTimeFormatPref(SettingsActivity.this,
                        formatValues[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final String[] sortLabels = {"Time", "Label A–Z"};
        final String[] sortValues = {"time", "label"};
        Spinner sortSpinner = findViewById(R.id.sort_order_spinner);
        sortSpinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, sortLabels));
        sortSpinner.setSelection(indexOf(sortValues,
                AlarmHelper.sortOrder(this)));
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                AlarmHelper.setSortOrder(SettingsActivity.this, sortValues[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.delete_all_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setMessage("Delete all alarms? This cannot be undone.")
                        .setPositiveButton("Delete",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        AlarmDbHelper db =
                                                new AlarmDbHelper(SettingsActivity.this);
                                        int n = 0;
                                        for (Alarm a : db.getAll()) {
                                            AlarmHelper.cancel(SettingsActivity.this, a);
                                            db.delete(a.id);
                                            n++;
                                        }
                                        AlarmHelper.refreshAllWidgets(SettingsActivity.this);
                                        Toast.makeText(SettingsActivity.this,
                                                n + " alarms deleted",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private interface MinutesSaver {
        void save(int minutes);
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    /** Spinner with the 5 / 10 / 15 / 30 minute choices. */
    private void setupMinutesSpinner(int spinnerId, int currentMinutes,
                                     final MinutesSaver saver) {
        final String[] labels = {"5 minutes", "10 minutes", "15 minutes", "30 minutes"};
        final int[] values = {5, 10, 15, 30};
        Spinner spinner = findViewById(spinnerId);
        spinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        int sel = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentMinutes) {
                sel = i;
            }
        }
        spinner.setSelection(sel);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                saver.save(values[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void refreshGlobalName() {
        globalName.setText("Global: " + AlarmHelper.globalRingtoneTitle(this));
    }

    /** Play the resolved global tone once, so the user can HEAR it applied. */
    private void togglePreview() {
        if (previewPlayer != null) {
            stopPreview();
            return;
        }
        Uri uri = AlarmHelper.resolveRingtoneUri(this, null);
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(this, uri);
            mp.prepare();
            mp.start();
            previewPlayer = mp;
            previewButton.setText("Stop preview");
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer p) {
                    stopPreview();
                }
            });
            previewHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopPreview();
                }
            }, 4000);
        } catch (Exception e) {
            Toast.makeText(this, "Could not preview this sound",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        previewHandler.removeCallbacksAndMessages(null);
        if (previewPlayer != null) {
            try {
                previewPlayer.stop();
            } catch (Exception ignored) {
            }
            try {
                previewPlayer.release();
            } catch (Exception ignored) {
            }
            previewPlayer = null;
        }
        if (previewButton != null) {
            previewButton.setText("Preview global ringtone");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_GLOBAL_RINGTONE && resultCode == RESULT_OK
                && data != null) {
            Uri uri = data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                if (!AlarmHelper.canPlayTone(this, uri.toString())) {
                    Toast.makeText(this,
                            "This sound can't be played by AlarmBoss — pick a system tone",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                AlarmHelper.setGlobalRingtoneUri(this, uri.toString());
                refreshGlobalName();
            }
        }
        if (requestCode == REQ_GLOBAL_FILE && resultCode == RESULT_OK
                && data != null) {
            String picked = TonePicker.handleFileResult(this, data);
            if (picked != null) {
                AlarmHelper.setGlobalRingtoneUri(this, picked);
                refreshGlobalName();
            } else {
                Toast.makeText(this,
                        "This sound can't be played by AlarmBoss — pick a system tone",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}

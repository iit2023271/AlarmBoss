package com.crazyboss.alarmboss;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/** Configure screen for the clock widget: minute step and time format. */
public class ClockWidgetConfigureActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private RadioGroup stepGroup;
    private RadioGroup formatGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_clock_configure);

        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        stepGroup = findViewById(R.id.cfg_step_group);
        formatGroup = findViewById(R.id.cfg_format_group);

        int step = AlarmHelper.clockMinuteStep(this, widgetId);
        int stepButton = step == 1 ? R.id.cfg_step_1
                : step == 15 ? R.id.cfg_step_15 : R.id.cfg_step_5;
        ((RadioButton) findViewById(stepButton)).setChecked(true);

        int format = AlarmHelper.clockFormat(this, widgetId);
        int formatButton = format == 1 ? R.id.cfg_fmt_12
                : format == 2 ? R.id.cfg_fmt_24 : R.id.cfg_fmt_system;
        ((RadioButton) findViewById(formatButton)).setChecked(true);

        Button save = findViewById(R.id.cfg_clock_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkedStep = stepGroup.getCheckedRadioButtonId();
                int step = checkedStep == R.id.cfg_step_1 ? 1
                        : checkedStep == R.id.cfg_step_15 ? 15 : 5;
                int checkedFormat = formatGroup.getCheckedRadioButtonId();
                int format = checkedFormat == R.id.cfg_fmt_12 ? 1
                        : checkedFormat == R.id.cfg_fmt_24 ? 2 : 0;
                AlarmHelper.setClockMinuteStep(
                        ClockWidgetConfigureActivity.this, widgetId, step);
                AlarmHelper.setClockFormat(
                        ClockWidgetConfigureActivity.this, widgetId, format);
                AppWidgetManager manager = AppWidgetManager.getInstance(
                        ClockWidgetConfigureActivity.this);
                ClockWidgetProvider.updateWidget(
                        ClockWidgetConfigureActivity.this, manager, widgetId);
                Intent result = new Intent();
                result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }
}

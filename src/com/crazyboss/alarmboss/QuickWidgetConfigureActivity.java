package com.crazyboss.alarmboss;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Configure screen for the quick widget: durations + chip count (2-4). */
public class QuickWidgetConfigureActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private CheckBox[] boxes;
    private RadioGroup countGroup;
    private static final int[] PRESETS = {15, 30, 60, 120, 180, 360};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_quick_configure);

        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        boxes = new CheckBox[]{
                findViewById(R.id.cfg_15), findViewById(R.id.cfg_30),
                findViewById(R.id.cfg_1h), findViewById(R.id.cfg_2h),
                findViewById(R.id.cfg_3h), findViewById(R.id.cfg_6h)};
        int[] current = AlarmHelper.quickDurations(this, widgetId);
        for (int i = 0; i < PRESETS.length; i++) {
            for (int d : current) {
                if (d == PRESETS[i]) {
                    boxes[i].setChecked(true);
                }
            }
        }

        countGroup = findViewById(R.id.cfg_count_group);
        int countButton = current.length >= 4 ? R.id.cfg_count_4
                : current.length == 3 ? R.id.cfg_count_3 : R.id.cfg_count_2;
        ((RadioButton) findViewById(countButton)).setChecked(true);

        Button save = findViewById(R.id.cfg_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Integer> picked = new ArrayList<>();
                for (int i = 0; i < PRESETS.length; i++) {
                    if (boxes[i].isChecked()) {
                        picked.add(PRESETS[i]);
                    }
                }
                if (picked.isEmpty()) {
                    picked.add(60);
                }
                int checkedCount = countGroup.getCheckedRadioButtonId();
                int count = checkedCount == R.id.cfg_count_2 ? 2
                        : checkedCount == R.id.cfg_count_3 ? 3 : 4;
                if (picked.size() > count) {
                    picked = picked.subList(0, count);
                }
                while (picked.size() < count) {
                    // Pad with the first unused preset so the widget
                    // always shows exactly the chosen chip count.
                    boolean added = false;
                    for (int p : PRESETS) {
                        if (!picked.contains(p)) {
                            picked.add(p);
                            added = true;
                            break;
                        }
                    }
                    if (!added) {
                        break;
                    }
                }
                int[] out = new int[picked.size()];
                for (int i = 0; i < picked.size(); i++) {
                    out[i] = picked.get(i);
                }
                AlarmHelper.setQuickDurations(
                        QuickWidgetConfigureActivity.this, widgetId, out);
                AppWidgetManager manager =
                        AppWidgetManager.getInstance(QuickWidgetConfigureActivity.this);
                QuickWidgetProvider.updateWidget(
                        QuickWidgetConfigureActivity.this, manager, widgetId);
                Intent result = new Intent();
                result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }
}

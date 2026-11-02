package com.crazyboss.alarmboss;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Alarm list + instant search. Long-press enters multi-select mode.
 * v1.5: hour-slot groups (collapsed by default), ViewHolder + ringtone
 * title cache for fast scrolling, multi-delete, badge snooze-off.
 */
public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 2001;
    private static final int REQ_APPLY_RINGTONE = 1003;
    private static final int REQ_APPLY_FILE = 1006;
    private static final String KEY_EXPANDED_HOURS = "expanded_hours";

    private ListView listView;
    private AlarmAdapter adapter;
    private TextView emptyText;
    private LinearLayout banner;
    private TextView bannerText;
    private Button bannerButton;
    private List<Long> pendingApplyIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText searchBox = findViewById(R.id.search_box);
        listView = findViewById(R.id.alarm_list);
        emptyText = findViewById(R.id.empty_text);
        banner = findViewById(R.id.exact_alarm_banner);
        bannerText = findViewById(R.id.banner_text);
        bannerButton = findViewById(R.id.banner_button);

        adapter = new AlarmAdapter();
        if (savedInstanceState != null) {
            ArrayList<Integer> saved =
                    savedInstanceState.getIntegerArrayList(KEY_EXPANDED_HOURS);
            if (saved != null) {
                adapter.setExpandedHours(new HashSet<Integer>(saved));
            }
        }
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Alarm a = adapter.getItem(position);
                if (a == null) {
                    // Header row: toggle expansion (defensive; headers also
                    // carry their own click listener in getView).
                    adapter.toggleHeader(position);
                    return;
                }
                Intent intent = new Intent(MainActivity.this, AddEditActivity.class);
                intent.putExtra(AlarmHelper.EXTRA_ALARM_ID, a.id);
                startActivity(intent);
            }
        });
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                getMenuInflater().inflate(R.menu.action_mode, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.action_apply_ringtone) {
                    pendingApplyIds = new ArrayList<>();
                    for (int i = 0; i < adapter.getCount(); i++) {
                        if (listView.isItemChecked(i)) {
                            Alarm a = adapter.getItem(i);
                            if (a != null) {
                                pendingApplyIds.add(a.id);
                            }
                        }
                    }
                    if (pendingApplyIds.isEmpty()) {
                        Toast.makeText(MainActivity.this,
                                "No alarms selected", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    TonePicker.showChoice(MainActivity.this,
                            new Runnable() {
                                @Override
                                public void run() {
                                    TonePicker.startSystemPicker(MainActivity.this,
                                            REQ_APPLY_RINGTONE,
                                            "Apply ringtone to selected", null);
                                }
                            },
                            new Runnable() {
                                @Override
                                public void run() {
                                    TonePicker.startFilePicker(MainActivity.this,
                                            REQ_APPLY_FILE);
                                }
                            });
                    mode.finish();
                    return true;
                }
                if (item.getItemId() == R.id.action_delete) {
                    final List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < adapter.getCount(); i++) {
                        if (listView.isItemChecked(i)) {
                            Alarm a = adapter.getItem(i);
                            if (a != null) {
                                ids.add(a.id);
                            }
                        }
                    }
                    if (ids.isEmpty()) {
                        Toast.makeText(MainActivity.this,
                                "No alarms selected", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("Delete " + ids.size() + " alarm(s)?")
                            .setPositiveButton("Delete",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d,
                                                            int which) {
                                            AlarmDbHelper db =
                                                    new AlarmDbHelper(MainActivity.this);
                                            for (Long id : ids) {
                                                Alarm a = db.get(id);
                                                if (a != null) {
                                                    AlarmHelper.cancel(
                                                            MainActivity.this, a);
                                                }
                                                db.delete(id);
                                            }
                                            refreshList();
                                            Toast.makeText(MainActivity.this,
                                                    ids.size() + " alarms deleted",
                                                    Toast.LENGTH_SHORT).show();
                                            mode.finish();
                                        }
                                    })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                  long id, boolean checked) {
            }
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        findViewById(R.id.add_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AddEditActivity.class));
            }
        });
        findViewById(R.id.settings_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (adapter != null) {
            outState.putIntegerArrayList(KEY_EXPANDED_HOURS,
                    new ArrayList<Integer>(adapter.getExpandedHours()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
        updateBanner();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !AlarmHelper.prefs(this).getBoolean("notif_asked", false)) {
            AlarmHelper.prefs(this).edit().putBoolean("notif_asked", true).apply();
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        updateBanner();
    }

    /** Warning banner: notifications off, else exact alarms off, else hidden. */
    private void updateBanner() {
        boolean notifDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
        if (notifDenied) {
            bannerText.setText("Notifications are off — alarms cannot appear "
                    + "on the lock screen until you allow them.");
            bannerButton.setText("Open app settings");
            bannerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            });
            banner.setVisibility(View.VISIBLE);
            return;
        }
        if (!AlarmHelper.canScheduleExact(this)) {
            bannerText.setText("Exact alarms are not allowed — alarms may ring late. "
                    + "Allow AlarmBoss to schedule exact alarms.");
            bannerButton.setText("Open exact alarm settings");
            bannerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startActivity(new Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                    }
                }
            });
            banner.setVisibility(View.VISIBLE);
            return;
        }
        banner.setVisibility(View.GONE);
    }

    private void refreshList() {
        List<Alarm> all = new AlarmDbHelper(this).getAll();
        adapter.setAlarms(all);
        // Empty-state text is handled by the adapter's filter(), which
        // knows the current query; it must not be overridden here.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_APPLY_RINGTONE && resultCode == RESULT_OK
                && data != null && pendingApplyIds != null) {
            Uri uri = data.getParcelableExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                AlarmDbHelper db = new AlarmDbHelper(this);
                for (Long id : pendingApplyIds) {
                    Alarm a = db.get(id);
                    if (a != null) {
                        a.ringtoneUri = uri.toString();
                        db.update(a);
                    }
                }
                Toast.makeText(this, "Ringtone applied to "
                        + pendingApplyIds.size() + " alarm(s)",
                        Toast.LENGTH_SHORT).show();
                refreshList();
            }
        }
        if (requestCode == REQ_APPLY_FILE && resultCode == RESULT_OK
                && data != null && pendingApplyIds != null) {
            String picked = TonePicker.handleFileResult(this, data);
            if (picked != null) {
                AlarmDbHelper db = new AlarmDbHelper(this);
                for (Long id : pendingApplyIds) {
                    Alarm a = db.get(id);
                    if (a != null) {
                        a.ringtoneUri = picked;
                        db.update(a);
                    }
                }
                Toast.makeText(this, "Ringtone applied to "
                        + pendingApplyIds.size() + " alarm(s)",
                        Toast.LENGTH_SHORT).show();
                refreshList();
            } else {
                Toast.makeText(this,
                        "This sound can't be played by AlarmBoss — pick a system tone",
                        Toast.LENGTH_LONG).show();
            }
        }
        pendingApplyIds = null;
    }

    // ---- list adapter: search + hour-slot groups + ViewHolder -------------

    /** One display row: an Integer hour = slot header, an Alarm = alarm row. */
    private class AlarmAdapter extends BaseAdapter {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ALARM = 1;

        private List<Alarm> full = new ArrayList<>();
        private List<Alarm> shown = new ArrayList<>();
        private List<Object> rows = new ArrayList<>();
        private String query = "";
        private Set<Integer> expandedHours = new HashSet<>();
        /** Ringtone titles resolved once per refreshList, never per scroll. */
        private Map<String, String> titleCache = new HashMap<>();

        void setAlarms(List<Alarm> alarms) {
            full = alarms;
            rebuild();
        }

        void filter(String q) {
            query = q == null ? "" : q;
            rebuild();
        }

        Set<Integer> getExpandedHours() {
            return expandedHours;
        }

        void setExpandedHours(Set<Integer> hours) {
            expandedHours = hours;
        }

        void toggleHeader(int position) {
            Object row = rows.get(position);
            if (row instanceof Integer) {
                int hour = (Integer) row;
                if (expandedHours.contains(hour)) {
                    expandedHours.remove(hour);
                } else {
                    expandedHours.add(hour);
                }
                notifyDataSetChanged();
            }
        }

        private void rebuild() {
            shown = new ArrayList<>();
            for (Alarm a : full) {
                if (matches(a, query)) {
                    shown.add(a);
                }
            }
            titleCache = new HashMap<>();
            boolean grouped = AlarmHelper.groupByHour(MainActivity.this)
                    && query.trim().isEmpty();
            rows = new ArrayList<>();
            if (grouped) {
                // One header per hour that has alarms, ascending 0-23.
                // Within a slot, alarms follow the sort-order setting.
                TreeMap<Integer, List<Alarm>> byHour = new TreeMap<>();
                for (Alarm a : shown) {
                    List<Alarm> slot = byHour.get(a.hour);
                    if (slot == null) {
                        slot = new ArrayList<>();
                        byHour.put(a.hour, slot);
                    }
                    slot.add(a);
                }
                for (Map.Entry<Integer, List<Alarm>> e : byHour.entrySet()) {
                    List<Alarm> slot = e.getValue();
                    Collections.sort(slot, slotComparator());
                    rows.add(e.getKey());
                    if (expandedHours.contains(e.getKey())) {
                        rows.addAll(slot);
                    }
                }
            } else {
                Collections.sort(shown, flatComparator());
                rows.addAll(shown);
            }
            notifyDataSetChanged();
            updateEmptyText();
        }

        /** Comparator for alarms inside one hour slot. */
        private Comparator<Alarm> slotComparator() {
            if ("label".equals(AlarmHelper.sortOrder(MainActivity.this))) {
                return new Comparator<Alarm>() {
                    @Override
                    public int compare(Alarm a, Alarm b) {
                        int c = displayLabel(a).compareToIgnoreCase(displayLabel(b));
                        if (c != 0) {
                            return c;
                        }
                        if (a.minute != b.minute) {
                            return a.minute - b.minute;
                        }
                        return (int) (a.id - b.id);
                    }
                };
            }
            return new Comparator<Alarm>() {
                @Override
                public int compare(Alarm a, Alarm b) {
                    if (a.minute != b.minute) {
                        return a.minute - b.minute;
                    }
                    return (int) (a.id - b.id);
                }
            };
        }

        /** Comparator for the flat list (grouping off or search active). */
        private Comparator<Alarm> flatComparator() {
            if ("label".equals(AlarmHelper.sortOrder(MainActivity.this))) {
                return new Comparator<Alarm>() {
                    @Override
                    public int compare(Alarm a, Alarm b) {
                        int c = displayLabel(a).compareToIgnoreCase(displayLabel(b));
                        if (c != 0) {
                            return c;
                        }
                        if (a.hour != b.hour) {
                            return a.hour - b.hour;
                        }
                        if (a.minute != b.minute) {
                            return a.minute - b.minute;
                        }
                        return (int) (a.id - b.id);
                    }
                };
            }
            return new Comparator<Alarm>() {
                @Override
                public int compare(Alarm a, Alarm b) {
                    if (a.hour != b.hour) {
                        return a.hour - b.hour;
                    }
                    if (a.minute != b.minute) {
                        return a.minute - b.minute;
                    }
                    return (int) (a.id - b.id);
                }
            };
        }

        private String displayLabel(Alarm a) {
            return (a.label == null || a.label.isEmpty()) ? "Alarm" : a.label;
        }

        /** Cached title lookup: no per-row I/O while scrolling. */
        private String cachedTitle(String uriString) {
            String key = uriString == null ? "" : uriString;
            String title = titleCache.get(key);
            if (title == null) {
                title = uriString == null
                        ? AlarmHelper.globalRingtoneTitle(MainActivity.this)
                        : AlarmHelper.ringtoneTitle(MainActivity.this, uriString);
                titleCache.put(key, title);
            }
            return title;
        }

        /** Empty-state text: a no-match message beats a blank list. */
        private void updateEmptyText() {
            if (!query.trim().isEmpty() && shown.isEmpty()) {
                emptyText.setText("No alarms match \"" + query.trim() + "\"");
                emptyText.setVisibility(View.VISIBLE);
            } else if (query.trim().isEmpty() && full.isEmpty()) {
                emptyText.setText(R.string.no_alarms);
                emptyText.setVisibility(View.VISIBLE);
            } else {
                emptyText.setVisibility(View.GONE);
            }
        }

        /**
         * Label substring, days-summary text, or time in either format.
         * Times are stored 24h but usually displayed 12h, so a bare
         * "H:MM" (H in 1..12) matches both H:MM and (H+12):MM, and
         * "H:MM am/pm" matches the exact 24h conversion. Bare "H"
         * matches hour H and H+12. Digit strings "HHMM"/"HMM" match
         * both their 24h and 12h equivalents ("0730" finds 7:30 and
         * 19:30). Alarms with no label display "Alarm" and match "alarm".
         */
        private boolean matches(Alarm a, String q) {
            String query = q.trim().toLowerCase();
            if (query.isEmpty()) {
                return true;
            }
            String displayLabel = (a.label == null || a.label.isEmpty())
                    ? "alarm" : a.label.toLowerCase();
            if (displayLabel.contains(query)) {
                return true;
            }
            if (AlarmHelper.daysSummary(a.days).toLowerCase().contains(query)) {
                return true;
            }
            String t = query.replaceAll("\\s+", "");
            boolean pm = t.endsWith("pm");
            boolean am = t.endsWith("am");
            if (am || pm) {
                t = t.substring(0, t.length() - 2);
            }
            try {
                if (t.contains(":")) {
                    String[] parts = t.split(":");
                    if (parts.length != 2) {
                        return false;
                    }
                    int h = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]);
                    if (am || pm) {
                        int h24 = pm ? (h % 12) + 12 : h % 12;
                        return a.hour == h24 && a.minute == m;
                    }
                    if (h >= 1 && h <= 12) {
                        return a.minute == m
                                && (a.hour == h || a.hour == h + 12);
                    }
                    return a.hour == h && a.minute == m;
                }
                if (!t.matches("[0-9]+")) {
                    return false;
                }
                if (t.length() <= 2) {
                    int h = Integer.parseInt(t);
                    if (am || pm) {
                        int h24 = pm ? (h % 12) + 12 : h % 12;
                        return a.hour == h24 && a.minute == 0;
                    }
                    if (h >= 1 && h <= 12) {
                        return a.hour == h || a.hour == h + 12;
                    }
                    return a.hour == h;
                }
                if (t.length() == 3 || t.length() == 4) {
                    int h;
                    int m;
                    if (t.length() == 4) {
                        h = Integer.parseInt(t.substring(0, 2));
                        m = Integer.parseInt(t.substring(2, 4));
                    } else {
                        h = Integer.parseInt(t.substring(0, 1));
                        m = Integer.parseInt(t.substring(1, 3));
                    }
                    if (a.minute != m) {
                        return false;
                    }
                    if (a.hour == h) {
                        return true;
                    }
                    return h >= 1 && h <= 12 && a.hour == h + 12;
                }
            } catch (NumberFormatException e) {
                return false;
            }
            return false;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Alarm getItem(int position) {
            Object row = rows.get(position);
            return row instanceof Alarm ? (Alarm) row : null;
        }

        @Override
        public long getItemId(int position) {
            Alarm a = getItem(position);
            return a != null ? a.id : -position - 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof Integer ? TYPE_HEADER : TYPE_ALARM;
        }

        @Override
        public boolean isEnabled(int position) {
            // Headers are not selectable: long-press on a header cannot
            // start the multi-choice action mode. Header taps are handled
            // by the header row's own click listener in getView.
            return !(rows.get(position) instanceof Integer);
        }

        /** Row views cached once; getView does no findViewById on scroll. */
        private class ViewHolder {
            TextView time;
            TextView label;
            TextView days;
            TextView ringtone;
            TextView badge;
            Switch toggle;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (getItemViewType(position) == TYPE_HEADER) {
                final int headerPos = position;
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(MainActivity.this)
                            .inflate(R.layout.item_slot_header, parent, false);
                }
                final int hour = (Integer) rows.get(position);
                int count = 0;
                for (Alarm a : shown) {
                    if (a.hour == hour) {
                        count++;
                    }
                }
                TextView headerText = row.findViewById(R.id.slot_header_text);
                String prefix = expandedHours.contains(hour) ? "▼ " : "▶ ";
                headerText.setText(prefix
                        + AlarmHelper.slotHeaderLabel(MainActivity.this, hour, count));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleHeader(headerPos);
                    }
                });
                return row;
            }

            View row = convertView;
            ViewHolder holder;
            if (row == null) {
                row = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_alarm, parent, false);
                holder = new ViewHolder();
                holder.time = row.findViewById(R.id.row_time);
                holder.label = row.findViewById(R.id.row_label);
                holder.days = row.findViewById(R.id.row_days);
                holder.ringtone = row.findViewById(R.id.row_ringtone);
                holder.badge = row.findViewById(R.id.row_badge);
                holder.toggle = row.findViewById(R.id.row_toggle);
                row.setTag(holder);
            } else {
                holder = (ViewHolder) row.getTag();
            }
            final Alarm a = getItem(position);
            holder.time.setText(AlarmHelper.formatTime(MainActivity.this, a.hour, a.minute));
            holder.label.setText(displayLabel(a));
            holder.days.setText(AlarmHelper.daysSummary(a.days));
            holder.ringtone.setText(a.ringtoneUri == null
                    ? "Global: " + cachedTitle(null)
                    : cachedTitle(a.ringtoneUri));

            // Plain-text badge, no background resource (v1.5 plain UI).
            // SNOOZED stays: it is a v1.4 Part A feature, not styling.
            holder.badge.setBackground(null);
            if (a.snoozeUntil >= System.currentTimeMillis()) {
                holder.badge.setText("SNOOZED");
                holder.badge.setTextColor(0xFFE8710A);
                holder.badge.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlarmHelper.cancelSnooze(MainActivity.this, a.id);
                        Toast.makeText(MainActivity.this,
                                "Snooze cancelled", Toast.LENGTH_SHORT).show();
                        refreshList();
                    }
                });
            } else if (a.enabled) {
                holder.badge.setText("ON");
                holder.badge.setTextColor(0xFF000000);
                holder.badge.setOnClickListener(null);
            } else {
                holder.badge.setText("OFF");
                holder.badge.setTextColor(0xFF808080);
                holder.badge.setOnClickListener(null);
            }

            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(a.enabled);
            holder.toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView,
                                             boolean isChecked) {
                    if (a.enabled != isChecked) {
                        AlarmHelper.setEnabled(MainActivity.this, a, isChecked);
                    }
                }
            });
            return row;
        }
    }
}

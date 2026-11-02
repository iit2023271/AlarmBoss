package com.crazyboss.alarmboss;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection service for the schedule widget. The factory builds two
 * sections from AlarmHelper.occurrencesForNext7Days: "Today" (offset 0)
 * and "This week" (offsets 1..6). Empty sections are omitted; when both
 * are empty the list is empty and the widget shows its empty view.
 * onDataSetChanged runs on a binder thread; plain DB reads there are
 * fine. Rows carry a fill-in intent with the alarm id for toggling.
 */
public class ScheduleWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ScheduleWidgetFactory(getApplicationContext());
    }

    static class ScheduleWidgetFactory implements RemoteViewsFactory {
        private final Context ctx;
        private final List<Object> items = new ArrayList<>();

        ScheduleWidgetFactory(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDataSetChanged() {
            items.clear();
            List<AlarmHelper.Occurrence> occ =
                    AlarmHelper.occurrencesForNext7Days(ctx,
                            System.currentTimeMillis());
            List<AlarmHelper.Occurrence> today = new ArrayList<>();
            List<AlarmHelper.Occurrence> week = new ArrayList<>();
            for (AlarmHelper.Occurrence o : occ) {
                if (o.dayOffset == 0) {
                    today.add(o);
                } else {
                    week.add(o);
                }
            }
            if (!today.isEmpty()) {
                items.add("Today");
                items.addAll(today);
            }
            if (!week.isEmpty()) {
                items.add("This week");
                items.addAll(week);
            }
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            Object item = items.get(position);
            RemoteViews row = new RemoteViews(ctx.getPackageName(),
                    R.layout.item_schedule);
            if (item instanceof String) {
                row.setTextViewText(R.id.schedule_header_title, (String) item);
                row.setViewVisibility(R.id.schedule_header_title,
                        android.view.View.VISIBLE);
                row.setViewVisibility(R.id.schedule_alarm_row,
                        android.view.View.GONE);
                return row;
            }
            AlarmHelper.Occurrence o = (AlarmHelper.Occurrence) item;
            row.setViewVisibility(R.id.schedule_header_title,
                    android.view.View.GONE);
            row.setViewVisibility(R.id.schedule_alarm_row,
                    android.view.View.VISIBLE);
            String time = AlarmHelper.formatTime(ctx, o.hour, o.minute);
            if (o.dayOffset != 0) {
                time = o.dayLabel + " " + time;
            }
            row.setTextViewText(R.id.schedule_row_time, time);
            row.setTextViewText(R.id.schedule_row_label, o.label);
            if (o.snoozed) {
                row.setTextViewText(R.id.schedule_row_badge, "SNOOZED");
                row.setTextColor(R.id.schedule_row_badge, 0xFFE8710A);
            } else {
                row.setTextViewText(R.id.schedule_row_badge, "ON");
                row.setTextColor(R.id.schedule_row_badge, 0xFFFFFFFF);
            }
            Intent fillIn = new Intent();
            fillIn.putExtra(AlarmHelper.EXTRA_ALARM_ID, o.alarmId);
            fillIn.putExtra(AlarmHelper.EXTRA_SNOOZED, o.snoozed);
            row.setOnClickFillInIntent(R.id.schedule_row, fillIn);
            return row;
        }

        @Override
        public long getItemId(int position) {
            Object item = items.get(position);
            if (item instanceof String) {
                return "Today".equals(item) ? -1 : -2;
            }
            AlarmHelper.Occurrence o = (AlarmHelper.Occurrence) item;
            // Snooze rows get ids far from regular rows so a snoozed and a
            // regular row for the same alarm/day can never collide.
            return o.snoozed ? o.alarmId * 10 + o.dayOffset + 1000000
                    : o.alarmId * 10 + o.dayOffset;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }
    }
}

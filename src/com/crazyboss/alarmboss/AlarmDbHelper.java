package com.crazyboss.alarmboss;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Plain SQLite store for alarms. No Room, no libraries. */
public class AlarmDbHelper extends SQLiteOpenHelper {
    private static final String DB = "alarmboss.db";
    private static final int VERSION = 2;

    public AlarmDbHelper(Context context) {
        super(context, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE alarms ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "hour INTEGER NOT NULL, "
                + "minute INTEGER NOT NULL, "
                + "label TEXT, "
                + "days INTEGER NOT NULL DEFAULT 0, "
                + "ringtoneUri TEXT, "
                + "enabled INTEGER NOT NULL DEFAULT 0, "
                + "triggerAt INTEGER NOT NULL DEFAULT 0, "
                + "snoozeUntil INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 -> v2: pending-snooze persistence. Existing alarms survive.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE alarms ADD COLUMN snoozeUntil INTEGER NOT NULL DEFAULT 0");
        }
    }

    private static ContentValues toValues(Alarm a) {
        ContentValues v = new ContentValues();
        v.put("hour", a.hour);
        v.put("minute", a.minute);
        v.put("label", a.label);
        v.put("days", a.days);
        v.put("ringtoneUri", a.ringtoneUri);
        v.put("enabled", a.enabled ? 1 : 0);
        v.put("triggerAt", a.triggerAt);
        v.put("snoozeUntil", a.snoozeUntil);
        return v;
    }

    private static Alarm fromCursor(Cursor c) {
        Alarm a = new Alarm();
        a.id = c.getLong(c.getColumnIndexOrThrow("id"));
        a.hour = c.getInt(c.getColumnIndexOrThrow("hour"));
        a.minute = c.getInt(c.getColumnIndexOrThrow("minute"));
        a.label = c.getString(c.getColumnIndexOrThrow("label"));
        a.days = c.getInt(c.getColumnIndexOrThrow("days"));
        a.ringtoneUri = c.getString(c.getColumnIndexOrThrow("ringtoneUri"));
        a.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) != 0;
        a.triggerAt = c.getLong(c.getColumnIndexOrThrow("triggerAt"));
        int snoozeIdx = c.getColumnIndex("snoozeUntil");
        a.snoozeUntil = snoozeIdx >= 0 ? c.getLong(snoozeIdx) : 0;
        return a;
    }

    public long insert(Alarm a) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            return db.insert("alarms", null, toValues(a));
        } finally {
            db.close();
        }
    }

    public void update(Alarm a) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.update("alarms", toValues(a), "id=?",
                    new String[]{String.valueOf(a.id)});
        } finally {
            db.close();
        }
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete("alarms", "id=?", new String[]{String.valueOf(id)});
        } finally {
            db.close();
        }
    }

    public Alarm get(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("alarms", null, "id=?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
        }
    }

    public List<Alarm> getAll() {
        List<Alarm> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("alarms", null, null, null, null, null,
                    "hour ASC, minute ASC, id ASC");
            while (c.moveToNext()) {
                out.add(fromCursor(c));
            }
            return out;
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
        }
    }

    /** Exact duplicate check: same hour + minute + days, excluding one id. */
    public Alarm findByTimeAndDays(int hour, int minute, int days, long excludeId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("alarms", null,
                    "hour=? AND minute=? AND days=? AND id!=?",
                    new String[]{String.valueOf(hour), String.valueOf(minute),
                            String.valueOf(days), String.valueOf(excludeId)},
                    null, null, "id ASC");
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
        }
    }

    /** One-time widget alarm with exactly this trigger instant. */
    public Alarm findQuickByTrigger(long triggerAt) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("alarms", null,
                    "days=0 AND triggerAt=?",
                    new String[]{String.valueOf(triggerAt)},
                    null, null, "id ASC");
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
        }
    }

    /** Any alarm (any days) with this time of day. */
    public Alarm findByTimeOfDay(int hour, int minute) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("alarms", null, "hour=? AND minute=?",
                    new String[]{String.valueOf(hour), String.valueOf(minute)},
                    null, null, "id ASC");
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
        }
    }

    /** Point every alarm at the global ringtone (ringtoneUri NULL). */
    public int applyGlobalToAll() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues v = new ContentValues();
            v.putNull("ringtoneUri");
            return db.update("alarms", v, null, null);
        } finally {
            db.close();
        }
    }
}

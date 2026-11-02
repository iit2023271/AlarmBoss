package com.crazyboss.alarmboss;

/**
 * One saved alarm.
 * days is a bitmask: bit 0 = Monday ... bit 6 = Sunday. 0 = one-time.
 * ringtoneUri null means "use the global ringtone".
 * triggerAt is epoch ms for one-time widget alarms; 0 = time-of-day alarm.
 */
public class Alarm {
    public long id = -1;
    public int hour;
    public int minute;
    public String label;
    public int days;
    public String ringtoneUri;
    public boolean enabled;
    public long triggerAt;
    public long snoozeUntil; // epoch ms of pending snooze, 0 = none

    public Alarm() {
    }

    public Alarm(int hour, int minute, String label, int days,
                 String ringtoneUri, boolean enabled, long triggerAt) {
        this.hour = hour;
        this.minute = minute;
        this.label = label;
        this.days = days;
        this.ringtoneUri = ringtoneUri;
        this.enabled = enabled;
        this.triggerAt = triggerAt;
    }

    public boolean isOneTime() {
        return days == 0;
    }
}

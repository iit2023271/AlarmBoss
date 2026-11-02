# Changelog

All notable changes to AlarmBoss, newest first. Dates are the release days (all versions shipped on 2026-11-02, one fast evening of building and phone testing).

## 1.5 — 2026-11-02
- **UI:** reverted the 1.4 Google-style polish back to the classic plain look (flat rows, default buttons, dark translucent widgets) at the user's request
- **Hour slots:** the main list now opens to slot headers (`8 – 9 AM • 3 alarms`), all collapsed; tap a slot to see that hour's alarms, tap again to close; several slots can stay open; rotation-safe
- **Fast scrolling:** proper ViewHolder recycling plus a per-refresh ringtone-title cache — no repeated lookups while scrolling
- **Delete:** long-press multi-select → Delete with confirmation; single delete the same way; Delete-all in Settings (confirmed)
- **Snooze-off, explicit:** tap the SNOOZED tag in the list to cancel a snooze (with toast); widget row tap still cancels; new "Allow snooze" setting hides the Snooze button entirely
- **New settings:** group-by-hour (default on), snooze length 5/10/15/30 min (shown on the button), auto-stop 5/10/15/30 min, vibrate on/off, time format System/12h/24h, list order Time/Label A–Z
- Search shows flat results while typing; clearing the search restores slots

## 1.4 — 2026-11-02
- **Snoozes in the schedule widget:** snoozing now writes a `snoozeUntil` timestamp to the database (DB v1 → v2 migration, alarms survive), so the widget shows a SNOOZED row with the re-ring time — including for one-time alarms, which previously vanished entirely
- Tapping a SNOOZED row cancels the snooze only (never toggles the alarm itself — a snoozed one-time alarm is disabled, and flipping it on would schedule stale time)
- Snooze fire clears the pending snooze and refreshes widgets; toggle-off clears it; BootReceiver re-arms pending snoozes after reboot
- Main list rows show a SNOOZED tag when a snooze is pending
- UI polish (later reverted in 1.5): Google-style light theme, cards, rounded buttons; firing screen kept black for night use

## 1.3 — 2026-11-02
- **Schedule widget:** scrollable Today + This week list (next 6 days, day-prefixed rows), empty state, tap title to open the app
- **Real-time refresh:** every alarm change (app, quick widget, clock widget, firing, boot) pushes fresh updates to all widgets immediately via AppWidgetManager (no reliance on broadcasts, which Android 8+ blocks in the background)
- Tap a schedule row to toggle that alarm; 30-minute tick covers midnight rollover

## 1.2 — 2026-11-02
- **Device music as ringtones:** every tone picker now offers "System tone" or "Music on device" (Storage Access Framework, `audio/*`) with persistable read permission, so songs keep playing after reboot
- Rows show real song names (`Global: song.mp3`); unplayable picks refused with a toast (validation now by MediaPlayer prepare, since the ringtone lookup returns nothing for document URIs)

## 1.1 — 2026-11-02
- **Search fixed:** matches 12h and 24h times both ways, AM/PM, display labels ("Alarm" for unlabeled), and day names; zero results show `No alarms match "q"` instead of a blank list
- **Global ringtone visible:** rows, AddEdit, and Settings show `Global: <tone name>`; Settings gains a Preview button; unplayable tones refused instead of silently falling back
- **Clock widget fixed:** shows status (`ON` / `OFF` / `No alarm at <time>`) + "Next alarm" footer, so SET no longer looks dead; re-enabling a fired one-time alarm re-times it to its next occurrence instead of ringing instantly
- New configure screens: clock widget (minute step 1/5/15, format System/12h/24h), quick widget (2/3/4 chips)

## 1.0 — 2026-11-02
- First release: own alarm store (Android has no API for stock Clock alarms), searchable list, dedupe toggle-or-create (never duplicates), global ringtone auto-applied, apply-to-all and multi-select ringtone, quick widget (duration chips) and clock widget (time + SET), STREAM_ALARM looping audio + vibration, force-max volume with restore, snooze 10 min, auto-stop 10 min, exact alarms with inexact fallback + warning, boot reschedule, full-screen lock-screen ringing view

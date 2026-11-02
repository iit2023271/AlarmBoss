package com.crazyboss.alarmboss;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Foreground service that actually rings: looping alarm audio on
 * STREAM_ALARM plus vibration, with optional force-max volume (saved
 * volume is restored on stop). Auto-stops after 10 minutes like a
 * stock alarm so a missed alarm cannot ring forever.
 */
public class AlarmService extends Service {
    private static final int NOTIFICATION_ID = 1;

    /** Auto-stop + wake-lock duration, from the user's setting (default 10 min). */
    private long autoStopMs() {
        return AlarmHelper.autoStopMinutes(this) * 60L * 1000L;
    }

    private MediaPlayer player;
    private Vibrator vibrator;
    private AudioManager audioManager;
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private long currentAlarmId = -1;
    private int savedVolume = -1;

    private final Runnable autoStop = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        handler = new Handler();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "AlarmBoss:playing");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        long alarmId = intent.getLongExtra(AlarmHelper.EXTRA_ALARM_ID, -1);
        if (alarmId < 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (alarmId == currentAlarmId && player != null && player.isPlaying()) {
            return START_STICKY; // Already ringing this alarm (activity re-start).
        }
        stopPlayback();

        Alarm a = new AlarmDbHelper(this).get(alarmId);
        if (a == null) {
            // Snooze or fallback launch without a row: still ring generically.
            a = new Alarm(0, 0, "Alarm", 0, null, true, 0);
            a.id = alarmId;
        }
        currentAlarmId = alarmId;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID,
                    AlarmHelper.buildAlarmNotification(this, a),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID,
                    AlarmHelper.buildAlarmNotification(this, a));
        }

        if (AlarmHelper.forceMaxVolume(this) && audioManager != null) {
            savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
        }

        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock.acquire(autoStopMs());
        }

        if (AlarmHelper.vibrateRinging(this)
                && vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 500, 500}, 0));
        }

        startAudio(a);
        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, autoStopMs());
        return START_STICKY;
    }

    private void startAudio(Alarm a) {
        Uri[] candidates = new Uri[]{
                AlarmHelper.resolveRingtoneUri(this, a),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        };
        for (Uri uri : candidates) {
            if (uri == null) {
                continue;
            }
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                mp.setDataSource(this, uri);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                return;
            } catch (Exception ignored) {
                // Try the next candidate; a custom file the app cannot read
                // falls back to the system tones, never to silence + crash.
            }
        }
        // No audio available at all: vibration still alerts the user.
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        stopPlayback();
        if (savedVolume >= 0 && audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0);
            savedVolume = -1;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        currentAlarmId = -1;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

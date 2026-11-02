package com.crazyboss.alarmboss;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;

/**
 * One place for tone picking. The system alarm-tone picker only lists
 * tones registered as alarm tones, so the user's own songs need the
 * Storage Access Framework file path instead. Every entry point in the
 * app (global tone, per-alarm tone, multi-select apply) goes through
 * the same choice dialog, so the two paths can never drift apart.
 */
public final class TonePicker {
    private TonePicker() {
    }

    /** Choice dialog: "System tone" or "Music on device". */
    public static void showChoice(Activity activity,
                                  final Runnable onSystem,
                                  final Runnable onFile) {
        new AlertDialog.Builder(activity)
                .setTitle("Choose alarm sound")
                .setItems(new String[]{"System tone", "Music on device"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    onSystem.run();
                                } else {
                                    onFile.run();
                                }
                            }
                        })
                .show();
    }

    /** The existing system alarm-tone picker intent, unchanged. */
    public static void startSystemPicker(Activity activity, int requestCode,
                                         String title, String existingUri) {
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title);
        if (existingUri != null) {
            picker.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    Uri.parse(existingUri));
        }
        activity.startActivityForResult(picker, requestCode);
    }

    /** Storage Access Framework picker for the user's own audio files. */
    public static void startFilePicker(Activity activity, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Handle a file-picker result: take the persistable read grant (so the
     * alarm can still play the file later, including after reboot) and
     * validate playability. Returns the URI string, or null when the pick
     * was cancelled, unusable, or unplayable. A provider that refuses the
     * persistable grant does not reject the pick: the URI is still
     * accepted, and the playback fallback covers a later failure.
     */
    public static String handleFileResult(android.content.Context context,
                                          Intent data) {
        if (data == null) {
            return null;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return null;
        }
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some providers refuse persistable grants; accept anyway.
        }
        if (!AlarmHelper.canPlayTone(context, uri.toString())) {
            return null;
        }
        return uri.toString();
    }
}

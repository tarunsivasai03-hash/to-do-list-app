package com.notionapp.mynotes.utils;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.notionapp.mynotes.R;
import com.notionapp.mynotes.models.Note;
import com.notionapp.mynotes.receivers.ReminderReceiver;

/**
 * ReminderScheduler — schedules / cancels the alarm behind a note's reminder.
 * Uses an inexact-but-doze-safe alarm, so no exact-alarm permission is needed.
 */
public final class ReminderScheduler {

    public static final String CHANNEL_ID = "note_reminders";

    private ReminderScheduler() {}

    private static PendingIntent pendingIntent(Context context, int noteId, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(Constants.EXTRA_NOTE_ID, noteId);
        return PendingIntent.getBroadcast(context, noteId, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Makes the alarm match the note: scheduled if it is active with a future reminder, else cancelled. */
    public static void sync(Context context, Note note) {
        if (note == null) return;
        if (note.getState() == Note.STATE_ACTIVE && note.getReminderTime() > System.currentTimeMillis()) {
            schedule(context, note.getId(), note.getReminderTime());
        } else {
            cancel(context, note.getId());
        }
    }

    public static void schedule(Context context, int noteId, long timeMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis,
                pendingIntent(context, noteId, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void cancel(Context context, int noteId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingIntent(context, noteId, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(channel);
    }
}

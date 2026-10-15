package com.notionapp.mynotes.receivers;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.notionapp.mynotes.R;
import com.notionapp.mynotes.activities.AddEditNoteActivity;
import com.notionapp.mynotes.repository.NoteRepository;
import com.notionapp.mynotes.utils.ChecklistUtils;
import com.notionapp.mynotes.utils.Constants;
import com.notionapp.mynotes.utils.NotionApp;
import com.notionapp.mynotes.utils.ReminderScheduler;

/** Fires when a note's reminder time arrives and shows a notification. */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final int noteId = intent.getIntExtra(Constants.EXTRA_NOTE_ID, Constants.NO_NOTE_ID);
        if (noteId == Constants.NO_NOTE_ID) return;

        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        final NoteRepository repo = ((NotionApp) app).getRepository();

        repo.loadNote(noteId, note -> {
            try {
                // Skip if the note was deleted, archived or trashed meanwhile
                if (note != null && note.getState() == com.notionapp.mynotes.models.Note.STATE_ACTIVE) {
                    ReminderScheduler.createChannel(app);
                    show(app, noteId, note.getTitle(), note.getContent());
                    repo.clearReminder(noteId);
                }
            } finally {
                result.finish();
            }
        });
    }

    private void show(Context context, int noteId, String title, String content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent open = new Intent(context, AddEditNoteActivity.class);
        open.putExtra(Constants.EXTRA_NOTE_ID, noteId);
        PendingIntent tap = PendingIntent.getActivity(context, noteId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String preview = ChecklistUtils.toPreview(content).trim();
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(preview.isEmpty() ? context.getString(R.string.reminder_default_text) : preview)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(preview))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);

        NotificationManagerCompat.from(context).notify(noteId, b.build());
    }
}

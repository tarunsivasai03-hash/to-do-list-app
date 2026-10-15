package com.notionapp.mynotes.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.notionapp.mynotes.dao.NoteDao;
import com.notionapp.mynotes.models.Note;
import com.notionapp.mynotes.utils.ReminderScheduler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Repository — single access point for note data.
 * Writes run on one background thread; reminder alarms are kept in sync with the data here.
 */
public class NoteRepository {

    /** Trashed notes are permanently deleted after this long. */
    public static final long TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    private final Context appContext;
    private final NoteDao noteDao;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public NoteRepository(Context context, NoteDao noteDao) {
        this.appContext = context.getApplicationContext();
        this.noteDao = noteDao;
    }

    /** Notes in the given state matching search text and category, in the given sort order. */
    public LiveData<List<Note>> getNotes(String query, String category, int sort, int state) {
        return noteDao.getNotes(escapeLike(query), category, sort, state);
    }

    /** Escapes LIKE wildcards so user input is matched literally. */
    private static String escapeLike(String q) {
        if (q == null) return "";
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ==============================
    // Writes
    // ==============================

    public void insertNote(Note note) {
        dbExecutor.execute(() -> {
            long id = noteDao.insertNote(note);
            note.setId((int) id);
            ReminderScheduler.sync(appContext, note);
        });
    }

    public void updateNote(Note note) {
        dbExecutor.execute(() -> {
            noteDao.updateNote(note);
            ReminderScheduler.sync(appContext, note);
        });
    }

    /** Moves a note between active / archived / trashed. */
    public void setState(Note note, int state) {
        dbExecutor.execute(() -> {
            noteDao.setState(note.getId(), state,
                    state == Note.STATE_TRASHED ? System.currentTimeMillis() : 0L);
            // Alarm follows the note: cancelled when archived/trashed, restored when active again
            ReminderScheduler.sync(appContext, noteDao.getNoteById(note.getId()));
        });
    }

    public void deleteForever(Note note) {
        dbExecutor.execute(() -> {
            noteDao.deleteNote(note);
            ReminderScheduler.cancel(appContext, note.getId());
        });
    }

    public void emptyTrash() {
        dbExecutor.execute(noteDao::emptyTrash);
    }

    public void purgeOldTrash() {
        dbExecutor.execute(() -> noteDao.purgeTrash(System.currentTimeMillis() - TRASH_RETENTION_MS));
    }

    public void clearReminder(int noteId) {
        dbExecutor.execute(() -> noteDao.clearReminder(noteId));
    }

    public void rescheduleAllReminders() {
        dbExecutor.execute(() -> {
            for (Note n : noteDao.getUpcomingReminders(System.currentTimeMillis())) {
                ReminderScheduler.sync(appContext, n);
            }
        });
    }

    /** Loads a single note on the DB thread and hands it to the callback (null if missing). */
    public void loadNote(int noteId, Consumer<Note> callback) {
        dbExecutor.execute(() -> callback.accept(noteDao.getNoteById(noteId)));
    }
}

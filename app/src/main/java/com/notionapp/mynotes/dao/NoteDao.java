package com.notionapp.mynotes.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.notionapp.mynotes.models.Note;

import java.util.List;

/**
 * Data Access Object (DAO) for Note operations.
 *
 * Room generates the SQL implementation at compile time via annotationProcessor.
 *
 * - LiveData-returning methods auto-observe database changes (no manual refresh needed)
 * - Methods that modify data (insert/update/delete) must be called off the main thread
 *   — we handle this in the Repository using Java ExecutorService.
 * - getNoteById returns a raw Note (not LiveData) since we need a one-shot fetch for editing.
 */
@Dao
public interface NoteDao {

    // ==============================
    // CRUD Operations
    // ==============================

    /** Insert a new note. OnConflict.REPLACE handles re-inserts (e.g., undo delete). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertNote(Note note);

    /** Update an existing note's fields. */
    @Update
    void updateNote(Note note);

    /** Delete a single note. */
    @Delete
    void deleteNote(Note note);

    /** Delete all notes from the table. */
    @Query("DELETE FROM notes_table")
    void deleteAllNotes();

    // ==============================
    // Query Operations (LiveData = auto-observe)
    // ==============================

    /** Get a single note by ID (for editing). Called on background thread. */
    @Query("SELECT * FROM notes_table WHERE id = :noteId LIMIT 1")
    Note getNoteById(int noteId);

    // ==============================
    // Combined filter + sort
    // ==============================

    /**
     * One query for search, category filter and sort order.
     * Empty query / category mean "no filter". query must be pre-escaped for LIKE
     * (escape char is backslash). sort: 0 newest, 1 oldest, 2 title A-Z, 3 title Z-A.
     */
    @Query("SELECT * FROM notes_table " +
           "WHERE state = :state " +
           "AND (:category = '' OR category = :category) " +
           "AND (:query = '' OR title LIKE '%' || :query || '%' ESCAPE '\\' " +
           "     OR content LIKE '%' || :query || '%' ESCAPE '\\') " +
           "ORDER BY pinned DESC, " +
           "CASE WHEN :sort = 2 THEN title COLLATE NOCASE END ASC, " +
           "CASE WHEN :sort = 3 THEN title COLLATE NOCASE END DESC, " +
           "CASE WHEN :sort = 1 THEN updatedDate END ASC, " +
           "updatedDate DESC")
    LiveData<List<Note>> getNotes(String query, String category, int sort, int state);

    // ==============================
    // Archive / Trash / Reminders
    // ==============================

    @Query("UPDATE notes_table SET state = :state, trashedDate = :trashedDate WHERE id = :id")
    void setState(int id, int state, long trashedDate);

    @Query("DELETE FROM notes_table WHERE state = 2")
    void emptyTrash();

    @Query("DELETE FROM notes_table WHERE state = 2 AND trashedDate < :cutoff")
    void purgeTrash(long cutoff);

    @Query("UPDATE notes_table SET reminderTime = 0 WHERE id = :id")
    void clearReminder(int id);

    @Query("SELECT * FROM notes_table WHERE state = 0 AND reminderTime > :now")
    List<Note> getUpcomingReminders(long now);
}

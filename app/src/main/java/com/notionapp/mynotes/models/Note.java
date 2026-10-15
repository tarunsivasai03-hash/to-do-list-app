package com.notionapp.mynotes.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * Note Entity — represents a single note stored in the Room database.
 *
 * Each field maps to a column in the "notes_table" table.
 * Room auto-generates the id as the primary key (autoGenerate = true).
 */
@Entity(tableName = "notes_table",
        indices = {@Index("updatedDate"), @Index("category")})
public class Note {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;    // Note title (required)
    private String content;  // Note body text
    private String category; // Category: Personal, Work, College, Shopping, Ideas
    private long createdDate; // Timestamp when note was first created
    private long updatedDate; // Timestamp when note was last modified

    @ColumnInfo(defaultValue = "0")
    private boolean pinned;   // Pinned notes are listed first

    @ColumnInfo(defaultValue = "0")
    private int state;        // STATE_ACTIVE / STATE_ARCHIVED / STATE_TRASHED

    @ColumnInfo(defaultValue = "0")
    private long trashedDate; // When it was moved to trash (0 if not trashed)

    @ColumnInfo(defaultValue = "0")
    private long reminderTime; // Epoch millis of the reminder (0 = none)

    public static final int STATE_ACTIVE = 0;
    public static final int STATE_ARCHIVED = 1;
    public static final int STATE_TRASHED = 2;

    // ==============================
    // Constructor
    // ==============================

    /**
     * Main constructor used when creating/inserting a new Note.
     * id is 0 (Room will auto-generate the real ID on insert).
     */
    public Note(String title, String content, String category,
                long createdDate, long updatedDate) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    // ==============================
    // Getters & Setters (required by Room)
    // ==============================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getCreatedDate() { return createdDate; }
    public void setCreatedDate(long createdDate) { this.createdDate = createdDate; }

    public int getState() { return state; }
    public void setState(int state) { this.state = state; }

    public long getTrashedDate() { return trashedDate; }
    public void setTrashedDate(long trashedDate) { this.trashedDate = trashedDate; }

    public long getReminderTime() { return reminderTime; }
    public void setReminderTime(long reminderTime) { this.reminderTime = reminderTime; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    /** Independent copy (DiffUtil needs a new instance to see a change). */
    public Note copy() {
        Note n = new Note(title, content, category, createdDate, updatedDate);
        n.id = id;
        n.pinned = pinned;
        n.state = state;
        n.trashedDate = trashedDate;
        n.reminderTime = reminderTime;
        return n;
    }

    public long getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(long updatedDate) { this.updatedDate = updatedDate; }

    // ==============================
    // Equality (used by DiffUtil in adapter)
    // ==============================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Note)) return false;
        Note note = (Note) o;
        return id == note.id
                && createdDate == note.createdDate
                && updatedDate == note.updatedDate
                && pinned == note.pinned
                && state == note.state
                && trashedDate == note.trashedDate
                && reminderTime == note.reminderTime
                && Objects.equals(title, note.title)
                && Objects.equals(content, note.content)
                && Objects.equals(category, note.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, content, category, createdDate, updatedDate, pinned,
                state, trashedDate, reminderTime);
    }
}

package com.notionapp.mynotes.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.notionapp.mynotes.dao.NoteDao;
import com.notionapp.mynotes.models.Note;

/**
 * Room Database — the single source of truth for all note data.
 *
 * Uses the Singleton pattern to prevent multiple database instances
 * from being opened simultaneously, which would cause data corruption.
 *
 * @Database annotation tells Room:
 *   - entities: which tables to create (Note)
 *   - version: schema version (increment when changing entity structure)
 *   - exportSchema: whether to export schema to a file (false for simplicity)
 */
@Database(entities = {Note.class}, version = 4, exportSchema = true)
public abstract class NoteDatabase extends RoomDatabase {

    /** Room generates the concrete DAO implementation at compile time. */
    public abstract NoteDao noteDao();

    // ==============================
    // Singleton
    // ==============================

    // volatile ensures the INSTANCE value is always read from main memory
    // so changes are immediately visible to all threads.
    private static volatile NoteDatabase INSTANCE;

    /** v3 -> v4: archive/trash state and reminders. */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `notes_table` ADD COLUMN `state` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `notes_table` ADD COLUMN `trashedDate` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `notes_table` ADD COLUMN `reminderTime` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** v2 -> v3: adds the pinned flag. */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `notes_table` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** v1 -> v2: adds indexes on updatedDate and category (no data is touched). */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_table_updatedDate` ON `notes_table` (`updatedDate`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_table_category` ON `notes_table` (`category`)");
        }
    };

    /**
     * Get the singleton database instance.
     *
     * synchronized block ensures only one thread can create the database
     * at a time, preventing race conditions during initialization (double-checked locking).
     */
    public static NoteDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (NoteDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            NoteDatabase.class,
                            "notes_database"   // Database file name on disk
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

package com.notionapp.mynotes.utils;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.notionapp.mynotes.database.NoteDatabase;
import com.notionapp.mynotes.repository.NoteRepository;

/**
 * NotionApp — custom Application class that initializes app-wide singletons.
 *
 * Why a custom Application class?
 * 1. The database and repository are expensive to create — we want ONE instance
 * 2. They need the application context (not activity context) to avoid memory leaks
 * 3. The Application class lives for the entire app lifecycle
 *
 * In Java, lazy initialization is done with null-check pattern
 * instead of Kotlin's `by lazy {}` delegate.
 */
public class NotionApp extends Application {

    // Lazily initialized singletons
    private NoteDatabase database;
    private NoteRepository repository;

    // ==============================
    // Lazy Getters
    // ==============================

    /** Returns the singleton Room database, creating it on first access. */
    public NoteDatabase getDatabase() {
        if (database == null) {
            database = NoteDatabase.getDatabase(this);
        }
        return database;
    }

    /** Returns the singleton Repository, creating it on first access. */
    public NoteRepository getRepository() {
        if (repository == null) {
            repository = new NoteRepository(this, getDatabase().noteDao());
        }
        return repository;
    }

    // ==============================
    // Lifecycle
    // ==============================

    @Override
    public void onCreate() {
        super.onCreate();
        // Restore the user's saved theme preference on app launch.
        // This runs before any activity is created, so the correct theme
        // is already active when the first screen appears.
        applyTheme();

        ReminderScheduler.createChannel(this);
        // Permanently remove notes that have sat in the trash too long
        getRepository().purgeOldTrash();
    }

    /**
     * Reads the saved theme mode from SharedPreferences and applies it.
     */
    private void applyTheme() {
        int themeMode = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getInt(Constants.PREF_THEME_MODE, Constants.THEME_SYSTEM);

        switch (themeMode) {
            case Constants.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Constants.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}

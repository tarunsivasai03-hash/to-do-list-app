package com.notionapp.mynotes.utils;

import java.util.Arrays;
import java.util.List;

/**
 * Constants — centralized app-wide constants.
 *
 * In Java, we use a class with a private constructor (utility class pattern)
 * instead of Kotlin's object singleton. All fields are public static final.
 */
public final class Constants {

    // Prevent instantiation
    private Constants() {}

    // ==============================
    // Intent Extras
    // ==============================
    public static final String EXTRA_NOTE_ID = "extra_note_id";
    public static final int NO_NOTE_ID = -1;

    // ==============================
    // Sort Order
    // ==============================
    public static final int SORT_NEWEST   = 0;
    public static final int SORT_OLDEST   = 1;
    public static final int SORT_TITLE_AZ = 2;
    public static final int SORT_TITLE_ZA = 3;

    // ==============================
    // Categories
    // ==============================
    public static final List<String> CATEGORIES =
            Arrays.asList(
                    "Personal", "Work", "College", "Shopping", "Ideas",
                    "Creative", "Projects", "Wellness", "Goals", "Lifestyle", "Finance", "Reading"
            );

    // ==============================
    // SharedPreferences
    // ==============================
    public static final String PREFS_NAME     = "notion_notes_prefs";
    public static final String PREF_THEME_MODE = "theme_mode";

    // Theme modes
    public static final int THEME_LIGHT  = 0;
    public static final int THEME_DARK   = 1;
    public static final int THEME_SYSTEM = 2;
}

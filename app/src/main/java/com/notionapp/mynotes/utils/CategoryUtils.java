package com.notionapp.mynotes.utils;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.notionapp.mynotes.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CategoryUtils — maps a category to a Notion-style tag color and formats dates.
 * Each category uses one of Notion's tag colors (background + text + dot).
 */
public final class CategoryUtils {

    private CategoryUtils() {}

    private static final int[] GRAY   = {R.color.tag_gray_bg,   R.color.tag_gray_text,   R.color.tag_gray_dot};
    private static final int[] BROWN  = {R.color.tag_brown_bg,  R.color.tag_brown_text,  R.color.tag_brown_dot};
    private static final int[] ORANGE = {R.color.tag_orange_bg, R.color.tag_orange_text, R.color.tag_orange_dot};
    private static final int[] YELLOW = {R.color.tag_yellow_bg, R.color.tag_yellow_text, R.color.tag_yellow_dot};
    private static final int[] GREEN  = {R.color.tag_green_bg,  R.color.tag_green_text,  R.color.tag_green_dot};
    private static final int[] BLUE   = {R.color.tag_blue_bg,   R.color.tag_blue_text,   R.color.tag_blue_dot};
    private static final int[] PURPLE = {R.color.tag_purple_bg, R.color.tag_purple_text, R.color.tag_purple_dot};
    private static final int[] PINK   = {R.color.tag_pink_bg,   R.color.tag_pink_text,   R.color.tag_pink_dot};
    private static final int[] RED    = {R.color.tag_red_bg,    R.color.tag_red_text,    R.color.tag_red_dot};

    private static int[] tag(String category) {
        if (category == null) return GRAY;
        switch (category) {
            case "Personal":  return PURPLE;
            case "Work":      return BLUE;
            case "College":   return GREEN;
            case "Shopping":  return ORANGE;
            case "Ideas":     return YELLOW;
            case "Creative":  return PINK;
            case "Projects":  return RED;
            case "Wellness":  return GREEN;
            case "Goals":     return BLUE;
            case "Lifestyle": return BROWN;
            case "Finance":   return GRAY;
            case "Reading":   return PURPLE;
            default:          return GRAY;
        }
    }

    public static int getTagBg(Context context, String category) {
        return ContextCompat.getColor(context, tag(category)[0]);
    }

    public static int getTagText(Context context, String category) {
        return ContextCompat.getColor(context, tag(category)[1]);
    }

    public static int getTagDot(Context context, String category) {
        return ContextCompat.getColor(context, tag(category)[2]);
    }

    // ==============================
    // Date Formatter
    // ==============================

    public static String formatDate(long timestamp) {
        // Calendar-day aware: "Today"/"Yesterday" follow midnight, not a 24h window.
        Locale locale = Locale.getDefault();
        if (android.text.format.DateUtils.isToday(timestamp)) {
            return new SimpleDateFormat("'Today,' h:mm a", locale).format(new Date(timestamp));
        }
        if (android.text.format.DateUtils.isToday(timestamp + 24L * 60 * 60 * 1000)) {
            return new SimpleDateFormat("'Yesterday,' h:mm a", locale).format(new Date(timestamp));
        }
        return new SimpleDateFormat("MMM d, yyyy", locale).format(new Date(timestamp));
    }
}

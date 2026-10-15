package com.notionapp.mynotes.utils;

/**
 * ChecklistUtils — helpers for "[ ] item" / "[x] item" checklist lines inside note text.
 */
public final class ChecklistUtils {

    public static final String OPEN = "[ ] ";
    public static final String DONE = "[x] ";

    private ChecklistUtils() {}

    /** Returns {checked, total} counts of checklist lines. */
    public static int[] progress(String content) {
        int checked = 0;
        int total = 0;
        if (content != null) {
            for (String line : content.split("\n")) {
                if (line.startsWith(OPEN)) {
                    total++;
                } else if (line.startsWith(DONE)) {
                    total++;
                    checked++;
                }
            }
        }
        return new int[]{checked, total};
    }

    /** Preview text where checklist markers are rendered as box glyphs. */
    public static String toPreview(String content) {
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(OPEN)) {
                line = "\u2610 " + line.substring(OPEN.length());
            } else if (line.startsWith(DONE)) {
                line = "\u2611 " + line.substring(DONE.length());
            }
            if (i > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }
}

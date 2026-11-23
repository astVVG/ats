package io.github.astvvg.ats.common;

import java.util.Locale;

/** Builds LIKE patterns from user input so that % and _ are matched literally. */
public final class LikePatterns {

    public static final char ESCAPE = '\\';

    private LikePatterns() {}

    /** Lower-case "contains" pattern, to compare against lower(column) with {@link #ESCAPE}. */
    public static String containsIgnoreCase(String text) {
        String escaped =
                text.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

package com.newtube.mobile.ui.playback;

import com.newtube.mobile.ui.common.MetaSeparator;

/** Keeps the channel identity in the compact metadata line without repeating it. */
final class WatchMetadataLine {
    private WatchMetadataLine() { }

    static String format(String author, String details) {
        return format(author, details, null, null);
    }

    static String format(String author, String details, String likes, String dislikes) {
        String name = author == null ? "" : author.trim();
        String rest = withoutAuthor(details, name);
        StringBuilder line = new StringBuilder();
        append(line, name);
        append(line, likes);
        append(line, rest);
        append(line, dislikes);
        return line.toString();
    }

    private static void append(StringBuilder line, String part) {
        if (part == null || part.trim().isEmpty()) return;
        if (line.length() > 0) line.append(MetaSeparator.DOT);
        line.append(part.trim());
    }

    static String withoutAuthor(String details, String author) {
        String text = details == null ? "" : details.trim();
        if (author == null || author.isEmpty() || !text.startsWith(author)) return text;
        String rest = text.substring(author.length()).trim();
        if (rest.isEmpty()) return "";
        return rest.startsWith("•") || rest.startsWith("·") ? rest.substring(1).trim() : text;
    }
}

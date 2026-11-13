package com.liskovsoft.mediaserviceinterfaces.data;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Uses source captions and known dubbed languages, without guessing from interface locale or order. */
public final class OriginalSubtitleSelector {
    private OriginalSubtitleSelector() { }

    public static MediaSubtitle select(List<MediaSubtitle> subtitles, List<String> dubbedLanguages) {
        if (subtitles == null) return null;
        List<MediaSubtitle> candidates = new ArrayList<>();
        Set<String> automaticLanguages = new HashSet<>();
        Set<String> allLanguages = new HashSet<>();
        for (MediaSubtitle subtitle : subtitles) {
            if (subtitle == null || !isSource(subtitle.getBaseUrl())) continue;
            String language = language(subtitle.getLanguageCode());
            if (language.isEmpty() || containsLanguage(dubbedLanguages, language)) continue;
            candidates.add(subtitle);
            allLanguages.add(language);
            if ("asr".equals(subtitle.getType())) automaticLanguages.add(language);
        }
        // Source ASR identifies the spoken language even when authors supplied several translations.
        Set<String> languages = automaticLanguages.isEmpty() ? allLanguages : automaticLanguages;
        if (languages.size() != 1) return null;
        String original = languages.iterator().next();
        MediaSubtitle automatic = null;
        for (MediaSubtitle subtitle : candidates) {
            if (!original.equals(language(subtitle.getLanguageCode()))) continue;
            if (!"asr".equals(subtitle.getType())) return subtitle;
            automatic = subtitle;
        }
        return automatic;
    }

    private static boolean isSource(String url) {
        if (url == null) return false;
        try {
            String query = new URI(url).getRawQuery();
            if (query == null) return true;
            for (String pair : query.split("&")) {
                if (pair.startsWith("tlang=") || pair.equals("variant=timing-optimized")) return false;
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean containsLanguage(List<String> values, String expected) {
        if (values != null) for (String value : values) if (expected.equals(language(value))) return true;
        return false;
    }

    private static String language(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', '-').split("-")[0];
    }
}

package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.mediaserviceinterfaces.data.OriginalSubtitleSelector;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class OriginalSubtitleSelectorTest {
    @Test public void prefersAuthorCaptionsInSpokenLanguageOverTranslationsAndAsr() {
        MediaSubtitle author = track("en", null, "");
        assertSame(author, OriginalSubtitleSelector.select(Arrays.asList(
                track("ru", null, "&tlang=ru"), track("fr", null, ""),
                track("en", "asr", ""), author), Collections.emptyList()));
    }

    @Test public void rejectsDubTranscriptsEvenWhenTheResponseOmitsDubbedAudio() {
        MediaSubtitle original = track("en", "asr", "");
        assertSame(original, OriginalSubtitleSelector.select(Arrays.asList(
                track("ru", "asr", "&variant=timing-optimized"), original), Collections.emptyList()));
    }

    @Test public void excludesKnownDubbedAudioLanguages() {
        MediaSubtitle original = track("es", "asr", "");
        assertSame(original, OriginalSubtitleSelector.select(Arrays.asList(track("en", "asr", ""), original),
                Collections.singletonList("en-US")));
    }

    @Test public void ambiguousOriginalLanguageDoesNotFallBackToListOrder() {
        assertNull(OriginalSubtitleSelector.select(Arrays.asList(track("fr", null, ""), track("en", null, "")), null));
        assertNull(OriginalSubtitleSelector.select(Arrays.asList(track("en", "asr", ""), track("es", "asr", "")), null));
    }

    @Test public void acceptsSingleSourceLanguageButNeverOnlyATranslation() {
        MediaSubtitle author = track("ja", null, "");
        assertSame(author, OriginalSubtitleSelector.select(Collections.singletonList(author), null));
        assertNull(OriginalSubtitleSelector.select(Collections.singletonList(track("ru", null, "&tlang=ru")), null));
        assertNull(OriginalSubtitleSelector.select(null, null));
    }

    private static MediaSubtitle track(String language, String type, String query) {
        return new MediaSubtitle() {
            public String getBaseUrl() { return "https://www.youtube.com/api/timedtext?v=test" + query; }
            public String getLanguageCode() { return language; }
            public String getType() { return type; }
            public boolean isTranslatable() { return true; }
            public String getVssId() { return null; }
            public String getName() { return language; }
            public String getMimeType() { return "application/json"; }
            public String getCodecs() { return null; }
        };
    }
}

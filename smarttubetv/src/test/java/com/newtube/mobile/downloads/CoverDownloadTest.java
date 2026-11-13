package com.newtube.mobile.downloads;

import static org.junit.Assert.*;
import org.junit.Test;

public class CoverDownloadTest {
    @Test public void coversTryOriginalResolutionBeforeSmallerSizes() {
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg", CoverDownload.candidates("abcdefghijk").get(0));
        assertTrue(CoverDownload.candidates("abcdefghijk").get(2).endsWith("/hqdefault.jpg"));
        assertFalse(CoverDownload.validId("../../file"));
        assertFalse(CoverDownload.validId("http://youtube.com/watch?v=abcdefghijk"));
    }
    @Test public void filenamesKeepReadableUnicodeAndRemovePathAndControlCharacters() {
        String name = CoverDownload.fileName("Видео: test/cover\n", "abcdefghijk");
        assertEquals("Видео_ test_cover_ [abcdefghijk].jpg", name);
        assertTrue(CoverDownload.fileName("😀".repeat(90), "abcdefghijk").length() < 190);
    }
}

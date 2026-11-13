package com.newtube.mobile.ui.dialog;

import static org.junit.Assert.*;
import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AiVideoShareTest {
    @Test public void jsonCaptionsKeepWordsAndMathematicalSymbols() throws Exception {
        assertEquals("x < 3 & y > 2\nConclusion", AiVideoShare.parseTranscript(
                "{\"events\":[{\"segs\":[{\"utf8\":\"x < 3 & y > 2\"}]},{\"segs\":[{\"utf8\":\"Conclusion\"}]}]}"));
    }
    @Test public void xmlCaptionsDecodeEntitiesWithoutIncludingMarkup() throws Exception {
        assertEquals("First & second next", AiVideoShare.parseTranscript(
                "<transcript><text>First &amp; second</text><text>next</text></transcript>"));
    }
    @Test public void emptyCaptionsAreNotPresentedAsARealTranscript() throws Exception {
        assertEquals("", AiVideoShare.parseTranscript("{\"events\":[{\"tStartMs\":0}]}"));
        String request = AiVideoShare.requestText("Summarize", "Title", "abc123", "");
        assertTrue(request.contains("No transcript is attached"));
        assertFalse(request.contains("Transcript:"));
    }
    @Test public void requestUsesTheChosenInstructionTitleAndCleanVideoLink() {
        String request = AiVideoShare.requestText("My instruction", "Title", "abc123", "Actual words");
        assertTrue(request.startsWith("My instruction\n\nTitle\nhttps://www.youtube.com/watch?v=abc123"));
        assertTrue(request.endsWith("Actual words"));
        assertFalse(request.contains("&t="));
    }
    @Test public void longTranscriptFitsAnIntentAndDisclosesShortening() {
        String request = AiVideoShare.requestText("Summarize", "Title", "abc123", new String(new char[150000]).replace('\0', 'a'));
        assertTrue(request.length() < 121000);
        assertTrue(request.contains("Transcript shortened"));
    }
}

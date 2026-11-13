package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiniPlayerSizeTest {
    @Test
    public void matchesTheOriginalYouTubeWindowMeasuredOnPixel10() {
        int width = MiniPlayerSize.width(1080, 2424, 2.625f);
        assertEquals(519, width);
        assertEquals(292, MiniPlayerSize.height(width));
    }

    @Test
    public void rotatingRetainsTheSameVideoWindowSize() {
        assertEquals(519, MiniPlayerSize.width(2424, 1080, 2.625f));
    }

    @Test
    public void smallerWindowsReceiveAProportionalCard() {
        assertEquals(339, MiniPlayerSize.width(720, 1080, 2.625f));
        assertEquals(191, MiniPlayerSize.height(339));
    }
}

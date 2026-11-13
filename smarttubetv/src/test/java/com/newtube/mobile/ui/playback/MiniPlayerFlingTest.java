package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiniPlayerFlingTest {
    @Test public void quickShortSwipesReachTheOppositeEdge() {
        assertEquals(8f, MiniPlayerFling.releaseCoordinate(147f, -61f, -1000f, 8f, 208f, 1f), 0f);
        assertEquals(630f, MiniPlayerFling.releaseCoordinate(69f, 61f, 1000f, 8f, 630f, 1f), 0f);
    }

    @Test public void slowDragsAndTinyFastMotionsKeepPositionBasedSelection() {
        assertEquals(147f, MiniPlayerFling.releaseCoordinate(147f, -61f, -435f, 8f, 208f, 1f), 0f);
        assertEquals(172f, MiniPlayerFling.releaseCoordinate(172f, -36f, -1000f, 8f, 208f, 1f), 0f);
    }

    @Test public void releaseVelocityDeterminesTheDirectionAfterAReversal() {
        assertEquals(208f, MiniPlayerFling.releaseCoordinate(147f, -61f, 1000f, 8f, 208f, 1f), 0f);
    }

    @Test public void pixelDensityKeepsTheSamePhysicalGesture() {
        float density = 2.625f;
        assertEquals(21f, MiniPlayerFling.releaseCoordinate(380f, -160f, -2667f,
                21f, 540f, density), 0f);
        assertEquals(380f, MiniPlayerFling.releaseCoordinate(380f, -160f, -1143f,
                21f, 540f, density), 0f);
    }
}

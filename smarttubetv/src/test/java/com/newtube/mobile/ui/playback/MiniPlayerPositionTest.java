package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiniPlayerPositionTest {
    private static final float EPSILON = 0.001f;

    @Test public void aNewSessionStartsAtTheBottomRight() {
        MiniPlayerPosition position = new MiniPlayerPosition();
        assertEquals(208f, position.left(8f, 208f), EPSILON);
        assertEquals(630f, position.top(8f, 630f), EPSILON);
    }

    @Test public void everyReleaseSelectsOneOfExactlyFourCorners() {
        for (int x = -100; x <= 300; x += 25) {
            for (int y = -100; y <= 800; y += 25) {
                MiniPlayerPosition position = new MiniPlayerPosition();
                position.remember(x, y, 8f, 208f, 8f, 630f);
                assertEquals(x >= 108 ? 208f : 8f, position.left(8f, 208f), EPSILON);
                assertEquals(y >= 319 ? 630f : 8f, position.top(8f, 630f), EPSILON);
            }
        }
    }

    @Test public void switchingHostOrRotatingPreservesTheChosenCorner() {
        MiniPlayerPosition position = new MiniPlayerPosition();
        position.remember(25f, 100f, 8f, 208f, 8f, 630f);
        assertEquals(8f, position.left(8f, 608f), EPSILON);
        assertEquals(8f, position.top(8f, 230f), EPSILON);
    }

    @Test public void hidingBottomNavigationKeepsTheBottomCornerAtTheNewBoundary() {
        MiniPlayerPosition position = new MiniPlayerPosition();
        position.remember(25f, 600f, 8f, 208f, 8f, 630f);
        assertEquals(686f, position.top(8f, 686f), EPSILON);
    }

    @Test public void constrainedViewportDoesNotOverwriteTheChosenCorner() {
        MiniPlayerPosition position = new MiniPlayerPosition();
        position.remember(25f, 100f, 8f, 208f, 8f, 630f);
        position.remember(8f, 8f, 8f, 8f, 8f, 8f);
        assertEquals(8f, position.left(8f, 208f), EPSILON);
        assertEquals(8f, position.top(8f, 630f), EPSILON);
    }

    @Test public void closingResetsTheNextSessionToTheBottomRight() {
        MiniPlayerPosition position = new MiniPlayerPosition();
        position.remember(-100f, -100f, 8f, 208f, 8f, 630f);
        position.reset();
        assertEquals(208f, position.left(8f, 208f), EPSILON);
        assertEquals(630f, position.top(8f, 630f), EPSILON);
    }
}

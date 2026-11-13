package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WatchMetadataLineTest {
    @Test public void missingChannelOrDetailsDoesNotLeaveAnExtraSeparator() {
        assertEquals("", WatchMetadataLine.format(null, null));
        assertEquals("Channel", WatchMetadataLine.format("Channel", null));
        assertEquals("40K views", WatchMetadataLine.format(null, "40K views"));
    }

    @Test public void fallbackDetailsDoNotRepeatTheChannelName() {
        assertEquals("Channel • 40K views", WatchMetadataLine.format("Channel", "Channel · 40K views"));
        assertEquals("Channel • 40K views", WatchMetadataLine.format("Channel", "Channel • 40K views"));
        assertEquals("Channel", WatchMetadataLine.format("Channel", "Channel"));
    }

    @Test public void similarPrefixesAreNotStrippedAsIfTheyWereAnAuthor() {
        assertEquals("ABC news", WatchMetadataLine.withoutAuthor("ABC news", "AB"));
    }

    @Test public void localizedDetailsArePreserved() {
        assertEquals("Канал • 10 тыс. просмотров • 2 дня назад",
                WatchMetadataLine.format("Канал", "10 тыс. просмотров • 2 дня назад"));
    }

    @Test public void actionCountsPrecedeViewsAndOmitUnavailableValues() {
        assertEquals("Channel • 25K likes • 1M views • 2 days ago",
                WatchMetadataLine.format("Channel", "1M views • 2 days ago", "25K likes", null));
        assertEquals("Channel • 25K likes • 1M views • 348 dislikes",
                WatchMetadataLine.format("Channel", "1M views", "25K likes", "348 dislikes"));
    }
}

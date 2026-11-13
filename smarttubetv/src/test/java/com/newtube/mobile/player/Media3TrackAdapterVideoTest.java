package com.newtube.mobile.player;

import static org.junit.Assert.*;

import android.app.Application;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.google.common.collect.ImmutableList;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class Media3TrackAdapterVideoTest {
    private DefaultTrackSelector selector;
    private Media3TrackAdapter adapter;

    @Before public void setUp() {
        selector = new DefaultTrackSelector(RuntimeEnvironment.getApplication());
        adapter = new Media3TrackAdapter(selector);
    }

    @After public void tearDown() { selector.release(); }

    private static Tracks videoTracks(String groupId, float frameRate) {
        TrackGroup group = new TrackGroup(groupId,
                new Format.Builder().setId("1:").setSampleMimeType(MimeTypes.VIDEO_H264).setCodecs("avc1.640028")
                        .setWidth(426).setHeight(240).setFrameRate(frameRate).setAverageBitrate(300_000).build(),
                new Format.Builder().setId("1:").setSampleMimeType(MimeTypes.VIDEO_H264).setCodecs("avc1.640028")
                        .setWidth(1920).setHeight(1080).setFrameRate(frameRate).setAverageBitrate(4_000_000).build());
        return new Tracks(ImmutableList.of(new Tracks.Group(group, true,
                new int[] {C.FORMAT_HANDLED, C.FORMAT_HANDLED}, new boolean[] {true, true})));
    }

    private void assertPinned1080() {
        TrackSelectionOverride override = selector.getParameters().overrides.values().iterator().next();
        assertEquals(C.TRACK_TYPE_VIDEO, override.getType());
        assertEquals(1, override.trackIndices.size());
        assertEquals(1080, override.mediaTrackGroup.getFormat(override.trackIndices.get(0)).height);
    }

    @Test public void reusedHlsIdsRespectResolutionCodecAndFrameRateAcrossSources() {
        for (float frameRate : new float[] {25, Format.NO_VALUE}) {
            adapter.onTracksChanged(videoTracks("initial", frameRate));
            FormatItem choice = adapter.getFormats(FormatItem.TYPE_VIDEO).get(0);
            assertEquals("1:", choice.getFormatId());
            adapter.selectFormat(choice);
            assertPinned1080();
            adapter.onSourceChanged();
            adapter.onTracksChanged(videoTracks("replacement", 30));
            assertPinned1080();
            adapter.selectFormat(ExoFormatItem.from(choice.toString()));
            assertPinned1080();
        }
        Format avc = new Format.Builder().setId("1:").setSampleMimeType(MimeTypes.VIDEO_H264)
                .setCodecs("avc1.640028").setWidth(1920).setHeight(1080).setFrameRate(30).build();
        Format vp9 = avc.buildUpon().setSampleMimeType(MimeTypes.VIDEO_VP9).setCodecs("vp09.00.40.08").build();
        Format vp9High = vp9.buildUpon().setFrameRate(60).build();
        TrackGroup group = new TrackGroup("mixed", avc, vp9, vp9High);
        adapter.onTracksChanged(new Tracks(ImmutableList.of(new Tracks.Group(group, true,
                new int[] {C.FORMAT_HANDLED, C.FORMAT_HANDLED, C.FORMAT_HANDLED},
                new boolean[] {true, true, true}))));
        adapter.selectFormat(adapter.getFormats(FormatItem.TYPE_VIDEO).get(0));
        TrackSelectionOverride override = selector.getParameters().overrides.values().iterator().next();
        assertEquals(vp9High, override.mediaTrackGroup.getFormat(override.trackIndices.get(0)));
    }

    @Test public void savedPresetsRemainAdaptiveAndAutoClearsAnExplicitPick() {
        FormatItem saved = ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", true);
        adapter.onTracksChanged(videoTracks("initial", 30));
        adapter.selectFormat(saved);
        assertTrue(selector.getParameters().overrides.isEmpty());
        assertEquals(1920, selector.getParameters().maxVideoHeight);
        adapter.selectFormat(adapter.getFormats(FormatItem.TYPE_VIDEO).get(0));
        assertPinned1080();
        adapter.selectFormat(FormatItem.VIDEO_FHD_VP9_60);
        assertTrue(selector.getParameters().overrides.isEmpty());
        adapter.selectFormat(ExoFormatItem.from(saved.toString()));
        assertTrue(selector.getParameters().overrides.isEmpty());
    }

    @Test public void aMissingRungClearsTheOldOverrideAndKeepsItsCeiling() {
        adapter.onTracksChanged(videoTracks("initial", 30));
        adapter.selectFormat(adapter.getFormats(FormatItem.TYPE_VIDEO).get(0));
        FormatItem missing = ExoFormatItem.from(FormatItem.TYPE_VIDEO, FormatItem.TYPE_VIDEO, "18", "avc1.64001e",
                640, 360, 30f, null, false, 400_000, false);
        adapter.selectFormat(missing);
        // The first source has only 240p and 1080p: select the lower available track.
        TrackSelectionOverride lower = selector.getParameters().overrides.values().iterator().next();
        assertEquals(240, lower.mediaTrackGroup.getFormat(lower.trackIndices.get(0)).height);
        Format onlyHigh = new Format.Builder().setId("137").setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1920).setHeight(1080).setFrameRate(30).setCodecs("avc1.640028").build();
        adapter.onTracksChanged(new Tracks(ImmutableList.of(new Tracks.Group(new TrackGroup("only-high", onlyHigh),
                true, new int[] {C.FORMAT_HANDLED}, new boolean[] {false}))));
        assertTrue(selector.getParameters().overrides.isEmpty());
        assertEquals(VideoRung.ceilingSide(360), selector.getParameters().maxVideoHeight);
    }

    @Test public void portraitFallbackUsesTheResolutionClassAndRetainsTheChosenTarget() {
        Format high = new Format.Builder().setId("high").setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1080).setHeight(1920).setFrameRate(30).setCodecs("avc1.640028").build();
        Format low = high.buildUpon().setId("low").setWidth(608).setHeight(1080).build();
        TrackGroup group = new TrackGroup("portrait", high, low);
        adapter.onTracksChanged(new Tracks(ImmutableList.of(new Tracks.Group(group, true,
                new int[] {C.FORMAT_HANDLED, C.FORMAT_HANDLED}, new boolean[] {false, false}))));
        FormatItem target = ExoFormatItem.from(FormatItem.TYPE_VIDEO, FormatItem.TYPE_VIDEO, "136", "avc1.640028",
                1280, 720, 30f, null, false, 2_000_000, false);
        adapter.selectFormat(target);
        TrackSelectionOverride selected = selector.getParameters().overrides.values().iterator().next();
        assertEquals(low, selected.mediaTrackGroup.getFormat(selected.trackIndices.get(0)));
        assertEquals(target, adapter.getSelectedFormat(FormatItem.TYPE_VIDEO));
    }

    @Test public void aFixedVideoChoiceDoesNotEnableTheBackgroundVideoRenderer() {
        adapter.setVideoTrackDisabled(true);
        adapter.onTracksChanged(videoTracks("initial", 30));
        adapter.selectFormat(adapter.getFormats(FormatItem.TYPE_VIDEO).get(0));
        assertTrue(selector.getParameters().disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO));
        assertPinned1080();
    }

    @Test public void anEngineRestartRestoresTheTemporaryPickBeforeTheSavedDefault() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData preferences =
                com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData.instance(context);
        FormatItem saved = ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", true);
        FormatItem temporary = ExoFormatItem.from(FormatItem.TYPE_VIDEO, FormatItem.TYPE_VIDEO, "136", "avc1.640028",
                1280, 720, 30f, null, false, 2_000_000, false);
        preferences.setFormat(saved);
        preferences.setTempVideoFormat(temporary);
        java.util.List<FormatItem> restored = new java.util.ArrayList<>();
        Media3PlayerController controller = new Media3PlayerController(context, null) {
            @Override public void selectFormat(FormatItem item) { restored.add(item); }
        };
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller, "applyPersistedFormats");
        assertEquals(temporary, restored.get(0));
        assertEquals(saved, preferences.getFormat(FormatItem.TYPE_VIDEO));
        preferences.setTempVideoFormat(null);
        restored.clear();
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller, "applyPersistedFormats");
        assertEquals(saved, restored.get(0));
    }
}

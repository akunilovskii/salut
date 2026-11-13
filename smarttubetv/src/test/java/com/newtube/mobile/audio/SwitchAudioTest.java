package com.newtube.mobile.audio;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
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
public class SwitchAudioTest {
    private Context context;
    private SwitchAudioOutput output;
    private FakeSocket socket;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("switch_audio_output", Context.MODE_PRIVATE).edit().clear().commit();
        output = new SwitchAudioOutput(context, (connected, failed) -> { });
        socket = new FakeSocket();
        field("socket", socket);
        field("connected", true);
    }

    @Test public void phonePreservesPcmAndDoesNotTransmit() throws Exception {
        SwitchAudioProcessor processor = processor(48000, 2, C.ENCODING_PCM_16BIT);
        ByteBuffer input = pcm16(960, 2, (short) 12345);
        processor.queueInput(input);
        ByteBuffer actual = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(3840, actual.remaining());
        while (actual.hasRemaining()) assertEquals(12345, actual.getShort());
        assertFalse(input.hasRemaining());
        assertTrue(socket.frames.isEmpty());
    }

    @Test public void emptyDecoderBufferBeforePcmDoesNotCrash() throws Exception {
        SwitchAudioProcessor processor = processor(48000, 2, C.ENCODING_PCM_16BIT);
        processor.queueInput(AudioProcessor.EMPTY_BUFFER);
        assertFalse(processor.getOutput().hasRemaining());
        processor.queueInput(pcm16(960, 2, (short) 12345));
        ByteBuffer actual = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(3840, actual.remaining());
        while (actual.hasRemaining()) assertEquals(12345, actual.getShort());
        processor.queueInput(actual);
        assertFalse(processor.getOutput().hasRemaining());
        assertTrue(socket.frames.isEmpty());
    }

    @Test public void emptyDecoderBufferDoesNotDiscardPendingPcm() throws Exception {
        SwitchAudioProcessor processor = processor(48000, 2, C.ENCODING_PCM_16BIT);
        processor.queueInput(pcm16(960, 2, (short) 6789));
        processor.queueInput(AudioProcessor.EMPTY_BUFFER);
        ByteBuffer actual = processor.getOutput().order(ByteOrder.nativeOrder());
        assertEquals(3840, actual.remaining());
        while (actual.hasRemaining()) assertEquals(6789, actual.getShort());
        assertTrue(socket.frames.isEmpty());
    }

    @Test public void switchSendsTwentyMillisecondFramesAndSilencesPhone() throws Exception {
        SwitchAudioOutput.setSelected(context, true);
        output.setVolume(50);
        SwitchAudioProcessor processor = processor(48000, 2, C.ENCODING_PCM_16BIT);
        processor.queueInput(pcm16(960, 2, (short) 10000));
        ByteBuffer local = processor.getOutput();
        while (local.hasRemaining()) assertEquals(0, local.get());
        assertEquals(1, socket.frames.size());
        assertEquals(3840, socket.frames.get(0).length);
        ByteBuffer remote = ByteBuffer.wrap(socket.frames.get(0)).order(ByteOrder.LITTLE_ENDIAN);
        while (remote.hasRemaining()) {
            short sample = remote.getShort();
            assertTrue(sample > 6000 && sample < 7000);
        }
    }

    @Test public void monoBecomesStereoAndVolumeIsClamped() {
        output.setVolume(200);
        output.flush(48000, 1, C.ENCODING_PCM_16BIT);
        output.handleBuffer(pcm16(960, 1, (short) 20000));
        assertEquals(100, SwitchAudioOutput.getSavedVolume(context));
        ByteBuffer remote = ByteBuffer.wrap(socket.frames.get(0)).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 960; i++) {
            assertTrue(remote.getShort() > 20000);
            assertTrue(remote.getShort() > 20000);
        }
    }

    @Test public void newSwitchRouteBoostsQuietSourceAtFullVolume() {
        assertEquals(100, SwitchAudioOutput.getSavedVolume(context));
        output.flush(48000, 2, C.ENCODING_PCM_16BIT);
        output.handleBuffer(pcm16(960, 2, (short) 12000));
        ByteBuffer remote = ByteBuffer.wrap(socket.frames.get(0)).order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(remote.getShort() > 12000);
    }

    @Test public void slowNetworkQueueDropsFramesInsteadOfGrowingLatency() {
        output.setVolume(50);
        socket.queued = 3840 * 25;
        output.flush(48000, 2, C.ENCODING_PCM_16BIT);
        output.handleBuffer(pcm16(1920, 2, (short) 12000));
        assertTrue(socket.frames.isEmpty());
        socket.queued = 0;
        output.handleBuffer(pcm16(960, 2, (short) 6000));
        assertEquals(1, socket.frames.size());
        assertTrue(ByteBuffer.wrap(socket.frames.get(0)).order(ByteOrder.LITTLE_ENDIAN).getShort() > 3000);
    }

    @Test public void volumeCurveHasSilenceAndAUsefulFullRange() {
        short quiet = sampleAt(5);
        short middle = sampleAt(50);
        short loud = sampleAt(100);
        assertTrue(quiet > 0 && quiet < middle / 10);
        assertTrue(middle < loud / 2);
        assertTrue(loud > 12000 && loud <= 32767);
        assertEquals(0, sampleAt(0));
    }

    private short sampleAt(int percent) {
        socket.frames.clear();
        output.setVolume(percent);
        output.flush(48000, 2, C.ENCODING_PCM_16BIT);
        output.handleBuffer(pcm16(960, 2, (short) 12000));
        return ByteBuffer.wrap(socket.frames.get(0)).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    @Test public void resamplingPersistsAcrossBufferBoundaries() {
        output.setVolume(100);
        output.flush(44100, 1, C.ENCODING_PCM_16BIT);
        for (int i = 0; i < 100; i++) output.handleBuffer(pcm16(441, 1, (short) 8000));
        // Linear interpolation defers the final fractional sample until the next input buffer.
        assertEquals(49, socket.frames.size());
        output.handleBuffer(pcm16(1, 1, (short) 8000));
        assertEquals(50, socket.frames.size());
    }

    @Test public void disconnectedSwitchNeverFallsBackToPhoneUnexpectedly() throws Exception {
        SwitchAudioOutput.setSelected(context, true);
        field("connected", false);
        SwitchAudioProcessor processor = processor(48000, 2, C.ENCODING_PCM_16BIT);
        processor.queueInput(pcm16(960, 2, (short) 10000));
        ByteBuffer local = processor.getOutput();
        while (local.hasRemaining()) assertEquals(0, local.get());
        assertTrue(socket.frames.isEmpty());
    }

    @Test public void hidingSwitchDisconnectsAndRestoresLocalAudioWithoutLosingVolume() throws Exception {
        Field singleton = SwitchAudioOutput.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        singleton.set(null, output);
        try {
            SwitchAudioOutput.setSelected(context, true);
            SwitchAudioOutput.saveVolume(context, 42);
            SwitchAudioOutput.setButtonShown(context, false);
            assertFalse(SwitchAudioOutput.isButtonShown(context));
            assertFalse(SwitchAudioOutput.isSelected(context));
            assertFalse(output.isConnected());
            assertEquals(42, SwitchAudioOutput.getSavedVolume(context));
            SwitchAudioProcessor processor = processor(48000, 2, C.ENCODING_PCM_16BIT);
            processor.queueInput(pcm16(960, 2, (short) 12345));
            ByteBuffer local = processor.getOutput().order(ByteOrder.nativeOrder());
            while (local.hasRemaining()) assertEquals(12345, local.getShort());
            SwitchAudioOutput.setButtonShown(context, true);
            assertTrue(SwitchAudioOutput.isButtonShown(context));
            assertFalse(SwitchAudioOutput.isSelected(context));
        } finally {
            singleton.set(null, previous);
        }
    }

    @Test public void bothButtonsObserveVisibilityAndHiddenButtonIgnoresLatePermission() {
        android.app.Activity activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        android.widget.ImageButton browse = new android.widget.ImageButton(activity);
        android.widget.ImageButton player = new android.widget.ImageButton(activity);
        root.addView(browse);
        root.addView(player);
        activity.setContentView(root);
        SwitchAudioButton.bind(activity, browse, com.liskovsoft.smartyoutubetv2.tv.R.color.mobile_color_on_surface);
        SwitchAudioButton.bind(activity, player, com.liskovsoft.smartyoutubetv2.tv.R.color.mobile_color_on_surface);
        assertEquals(android.view.View.VISIBLE, browse.getVisibility());
        SwitchAudioOutput.setButtonShown(activity, false);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(android.view.View.GONE, browse.getVisibility());
        assertEquals(android.view.View.GONE, player.getVisibility());
        assertTrue(SwitchAudioButton.handlePermissionResult(activity, 113,
                new int[] {android.content.pm.PackageManager.PERMISSION_GRANTED}));
        assertFalse(SwitchAudioOutput.isSelected(activity));
        SwitchAudioOutput.setButtonShown(activity, true);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(android.view.View.VISIBLE, browse.getVisibility());
        assertEquals(android.view.View.VISIBLE, player.getVisibility());
        activity.finish();
    }

    private SwitchAudioProcessor processor(int rate, int channels, int encoding) throws Exception {
        SwitchAudioProcessor processor = new SwitchAudioProcessor(output);
        processor.configure(new AudioFormat(rate, channels, encoding));
        processor.flush();
        return processor;
    }

    private static ByteBuffer pcm16(int frames, int channels, short value) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < frames * channels; i++) buffer.putShort(value);
        buffer.flip();
        return buffer;
    }

    private void field(String name, Object value) throws Exception {
        Field field = SwitchAudioOutput.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(output, value);
    }

    private static final class FakeSocket implements WebSocket {
        final List<byte[]> frames = new ArrayList<>();
        long queued;
        @Override public Request request() { return new Request.Builder().url("http://localhost").build(); }
        @Override public long queueSize() { return queued; }
        @Override public boolean send(String text) { return false; }
        @Override public boolean send(ByteString bytes) { frames.add(bytes.toByteArray()); return true; }
        @Override public boolean close(int code, String reason) { return true; }
        @Override public void cancel() { }
    }
}

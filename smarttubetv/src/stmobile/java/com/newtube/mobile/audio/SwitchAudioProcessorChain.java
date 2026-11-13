package com.newtube.mobile.audio;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessorChain;
import androidx.media3.exoplayer.audio.DefaultAudioSink;


import java.util.Arrays;

public class SwitchAudioProcessorChain implements AudioProcessorChain {
    private final DefaultAudioSink.DefaultAudioProcessorChain defaultChain =
            new DefaultAudioSink.DefaultAudioProcessorChain(new AudioProcessor[0]);
    private final AudioProcessor[] processors;

    public SwitchAudioProcessorChain(SwitchAudioOutput output) {
        AudioProcessor[] defaults = defaultChain.getAudioProcessors();
        processors = Arrays.copyOf(defaults, defaults.length + 1);
        processors[defaults.length] = new SwitchAudioProcessor(output);
    }

    @Override
    public AudioProcessor[] getAudioProcessors() {
        return processors;
    }

    @Override
    public PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters) {
        return defaultChain.applyPlaybackParameters(playbackParameters);
    }

    @Override
    public boolean applySkipSilenceEnabled(boolean skipSilenceEnabled) {
        return defaultChain.applySkipSilenceEnabled(skipSilenceEnabled);
    }

    @Override
    public long getMediaDuration(long playoutDuration) {
        return defaultChain.getMediaDuration(playoutDuration);
    }

    @Override
    public long getSkippedOutputFrameCount() {
        return defaultChain.getSkippedOutputFrameCount();
    }
}

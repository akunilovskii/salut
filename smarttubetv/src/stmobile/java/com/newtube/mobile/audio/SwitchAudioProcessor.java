package com.newtube.mobile.audio;

import java.nio.ByteBuffer;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

/** Copies decoded PCM to Switch before silencing only the phone's output. */
public final class SwitchAudioProcessor extends BaseAudioProcessor {
    private final SwitchAudioOutput output;

    public SwitchAudioProcessor(SwitchAudioOutput output) {
        this.output = output;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat format) throws AudioProcessor.UnhandledAudioFormatException {
        if (format.encoding != C.ENCODING_PCM_16BIT && format.encoding != C.ENCODING_PCM_FLOAT) {
            throw new AudioProcessor.UnhandledAudioFormatException(format);
        }
        return format;
    }

    @Override
    protected void onFlush() {
        output.flush(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, inputAudioFormat.encoding);
    }

    @Override
    public void queueInput(ByteBuffer input) {
        if (!input.hasRemaining()) {
            return;
        }
        int size = input.remaining();
        ByteBuffer target = replaceOutputBuffer(size);
        if (output.isSelected()) {
            output.handleBuffer(input);
            // Keep AudioTrack running to pace the decoder and preserve the video clock.
            while (target.remaining() >= 8) target.putLong(0L);
            while (target.hasRemaining()) target.put((byte) 0);
            input.position(input.limit());
        } else {
            target.put(input);
        }
        target.flip();
    }
}

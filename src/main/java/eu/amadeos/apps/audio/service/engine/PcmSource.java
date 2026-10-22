package eu.amadeos.apps.audio.service.engine;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;
import java.io.IOException;

// --8<-- [start:source]
/**
 * Decoded audio: signed 16-bit little-endian PCM in {@link #format()}. Implementations are read by one
 * thread at a time.
 */
public interface PcmSource extends Closeable {
    /** Signed 16-bit little-endian PCM; the sample rate may differ from what the decoder reports (HE-AAC). */
    AudioFormat format();

    /** Reads whole frames; returns -1 at the end. Blocks while a network stream is buffering. */
    int read(byte[] buffer, int offset, int length) throws IOException;
// --8<-- [end:source]

    /** Total length in frames, or -1 if unknown (streams). */
    default long frameLength() {
        return -1;
    }

    /** Skips frames, e.g. to seek in a file; returns the number of frames skipped. */
    default long skipFrames(long frames) throws IOException {
        int frameSize = format().getFrameSize();
        byte[] scratch = new byte[frameSize * 4096];
        long skipped = 0;
        while (skipped < frames) {
            int want = (int) Math.min(scratch.length / frameSize, frames - skipped) * frameSize;
            int n = read(scratch, 0, want);
            if (n < 0) break;
            skipped += n / frameSize;
        }
        return skipped;
    }

    /** The standard format the engine works with. */
    static AudioFormat pcm16(float sampleRate, int channels) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate,
                false);
    }
}

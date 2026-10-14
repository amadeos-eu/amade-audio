package org.datazuul.amadeos.audio.service.engine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;

/**
 * A {@link PcmSource} reading an already decoded 16-bit {@link AudioInputStream}.
 */
public class AudioInputStreamSource implements PcmSource {
    private final AudioInputStream stream;
    private final AudioFormat format;
    private final long frameLength;
    private final byte[] carry;
    private int carried;

    /**
     * @param playbackFormat format to report, e.g. with a corrected sample rate; null for the stream's own
     * @param frameLength    total frames if known better than the stream knows, else -1
     */
    public AudioInputStreamSource(AudioInputStream stream, AudioFormat playbackFormat, long frameLength) {
        this.stream = stream;
        this.format = playbackFormat != null ? playbackFormat : stream.getFormat();
        long own = stream.getFrameLength();
        this.frameLength = frameLength >= 0 ? frameLength : own == AudioSystem.NOT_SPECIFIED ? -1 : own;
        this.carry = new byte[Math.max(1, format.getFrameSize())];
    }

    public AudioInputStreamSource(AudioInputStream stream) {
        this(stream, null, -1);
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int frameSize = format.getFrameSize();
        int want = length - length % frameSize;
        if (want <= 0) return 0;
        int filled = 0;
        if (carried > 0) {
            System.arraycopy(carry, 0, buffer, offset, carried);
            filled = carried;
            carried = 0;
        }
        while (filled < frameSize) {
            int n = stream.read(buffer, offset + filled, want - filled);
            if (n < 0) return filled == 0 ? -1 : 0; // drop an incomplete last frame
            filled += n;
            if (n == 0) Thread.onSpinWait();
        }
        // Return whole frames only; keep a trailing partial frame for the next call.
        int rest = filled % frameSize;
        if (rest > 0) {
            System.arraycopy(buffer, offset + filled - rest, carry, 0, rest);
            carried = rest;
            filled -= rest;
        }
        return filled;
    }

    @Override
    public long frameLength() {
        return frameLength;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}

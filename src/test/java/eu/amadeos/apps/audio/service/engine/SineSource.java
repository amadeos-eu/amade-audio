package eu.amadeos.apps.audio.service.engine;

import javax.sound.sampled.AudioFormat;

/** A stereo sine of the given length at 44.1 kHz, full scale / 2. */
public final class SineSource implements PcmSource {
    private final double frequency;
    private final long totalFrames;
    private long frame;
    public volatile boolean closed;

    public SineSource(double frequency, double seconds) {
        this.frequency = frequency;
        this.totalFrames = (long) (seconds * 44100);
    }

    @Override
    public AudioFormat format() {
        return PcmSource.pcm16(44100f, 2);
    }

    // --8<-- [start:read]
    @Override
    public int read(byte[] buffer, int offset, int length) {
        if (frame >= totalFrames) return -1;
        int frames = (int) Math.min(length / 4, totalFrames - frame);
        for (int i = 0; i < frames; i++) {
            short s = (short) (16384 * Math.sin(2 * Math.PI * frequency * (frame + i) / 44100));
            int at = offset + i * 4;
            buffer[at] = (byte) s;
            buffer[at + 1] = (byte) (s >> 8);
            buffer[at + 2] = (byte) s;
            buffer[at + 3] = (byte) (s >> 8);
        }
        frame += frames;
        return frames * 4;
    }
    // --8<-- [end:read]

    @Override
    public long frameLength() {
        return totalFrames;
    }

    @Override
    public void close() {
        closed = true;
    }
}

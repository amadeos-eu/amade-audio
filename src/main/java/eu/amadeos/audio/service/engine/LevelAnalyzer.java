package eu.amadeos.audio.service.engine;

import eu.amadeos.audio.domain.AudioLevels;

import javax.sound.sampled.AudioFormat;

/**
 * Peak meters and a 16-band spectrum from the PCM written to the output, published about 25 times a second.
 */
public final class LevelAnalyzer {
    /** Levels below this are shown as silence. */
    static final double FLOOR_DB = -40;
    private static final int FFT_SIZE = 2048;
    private static final double LOW_HZ = 50;
    private static final double HIGH_HZ = 16000;

    private final double[] window = new double[FFT_SIZE];
    private final double[] ring = new double[FFT_SIZE];
    private final double[] re = new double[FFT_SIZE];
    private final double[] im = new double[FFT_SIZE];
    private int ringPos;
    private float peakLeft;
    private float peakRight;
    private long framesSincePublish;

    public LevelAnalyzer() {
        for (int i = 0; i < FFT_SIZE; i++) window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1));
    }

    /**
     * Feeds 16-bit little-endian frames; returns new levels when it is time to publish, otherwise null.
     */
    public AudioLevels process(byte[] buffer, int offset, int length, AudioFormat format) {
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int frames = length / frameSize;
        for (int f = 0; f < frames; f++) {
            int base = offset + f * frameSize;
            int left = sample(buffer, base);
            int right = channels > 1 ? sample(buffer, base + 2) : left;
            peakLeft = Math.max(peakLeft, Math.abs(left) / 32768f);
            peakRight = Math.max(peakRight, Math.abs(right) / 32768f);
            ring[ringPos] = (left + right) / 65536.0;
            ringPos = (ringPos + 1) % FFT_SIZE;
        }
        framesSincePublish += frames;
        if (framesSincePublish < format.getSampleRate() / 25) return null;
        framesSincePublish = 0;
        AudioLevels levels = new AudioLevels(meter(peakLeft), meter(peakRight), spectrum(format.getSampleRate()));
        peakLeft = 0;
        peakRight = 0;
        return levels;
    }

    /** Peak (0..1 of full scale) to meter position 0..1 on a -40..0 dB scale. */
    static float meter(float peak) {
        if (peak <= 0) return 0;
        double db = 20 * Math.log10(peak);
        return (float) Math.max(0, Math.min(1, (db - FLOOR_DB) / -FLOOR_DB));
    }

    private float[] spectrum(float sampleRate) {
        for (int i = 0; i < FFT_SIZE; i++) {
            re[i] = ring[(ringPos + i) % FFT_SIZE] * window[i];
            im[i] = 0;
        }
        Fft.transform(re, im);
        float[] bands = new float[AudioLevels.BANDS];
        double binHz = sampleRate / FFT_SIZE;
        double ratio = Math.pow(HIGH_HZ / LOW_HZ, 1.0 / AudioLevels.BANDS);
        for (int b = 0; b < bands.length; b++) {
            double from = LOW_HZ * Math.pow(ratio, b);
            double to = from * ratio;
            int i0 = Math.max(1, (int) Math.floor(from / binHz));
            int i1 = Math.min(FFT_SIZE / 2 - 1, Math.max(i0, (int) Math.ceil(to / binHz)));
            double max = 0;
            for (int i = i0; i <= i1; i++) max = Math.max(max, Math.hypot(re[i], im[i]));
            // A full-scale sine gives about FFT_SIZE/4 with the Hann window.
            double db = 20 * Math.log10(Math.max(1e-9, max / (FFT_SIZE / 4.0)));
            bands[b] = (float) Math.max(0, Math.min(1, (db + 60) / 60));
        }
        return bands;
    }

    private static int sample(byte[] buffer, int index) {
        return (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
    }
}

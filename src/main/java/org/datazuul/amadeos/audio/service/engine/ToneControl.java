package org.datazuul.amadeos.audio.service.engine;

/**
 * Bass and treble shelving filters (RBJ cookbook biquads) on 16-bit stereo or mono PCM.
 */
public final class ToneControl {
    static final double BASS_HZ = 200;
    static final double TREBLE_HZ = 4000;
    /** Range of the controls in dB. */
    public static final double MAX_DB = 12;

    private final Biquad[] bass = {new Biquad(), new Biquad()};
    private final Biquad[] treble = {new Biquad(), new Biquad()};
    private double bassDb;
    private double trebleDb;
    private float sampleRate;
    private boolean dirty = true;

    /** Sets the gains in dB, from {@code -MAX_DB} to {@code +MAX_DB}; thread-safe. */
    public synchronized void set(double bassDb, double trebleDb) {
        this.bassDb = clamp(bassDb);
        this.trebleDb = clamp(trebleDb);
        dirty = true;
    }

    public synchronized boolean isFlat() {
        return Math.abs(bassDb) < 0.05 && Math.abs(trebleDb) < 0.05;
    }

    /** Filters the frames in place. */
    public synchronized void process(byte[] buffer, int offset, int length, float rate, int channels) {
        if (isFlat()) return;
        if (dirty || rate != sampleRate) {
            sampleRate = rate;
            for (int c = 0; c < 2; c++) {
                bass[c].shelf(true, BASS_HZ, bassDb, rate);
                treble[c].shelf(false, TREBLE_HZ, trebleDb, rate);
            }
            dirty = false;
        }
        int frameSize = channels * 2;
        for (int i = offset; i + frameSize <= offset + length; i += frameSize) {
            for (int c = 0; c < Math.min(2, channels); c++) {
                int idx = i + c * 2;
                double x = (short) ((buffer[idx] & 0xFF) | (buffer[idx + 1] << 8));
                double y = treble[c].apply(bass[c].apply(x));
                int s = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(y)));
                buffer[idx] = (byte) s;
                buffer[idx + 1] = (byte) (s >> 8);
            }
        }
    }

    private static double clamp(double db) {
        return Math.max(-MAX_DB, Math.min(MAX_DB, db));
    }

    private static final class Biquad {
        private double b0 = 1, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        void shelf(boolean low, double freq, double gainDb, double rate) {
            double a = Math.pow(10, gainDb / 40);
            double w0 = 2 * Math.PI * freq / rate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / 2 * Math.sqrt(2); // shelf slope S = 1
            double sq = 2 * Math.sqrt(a) * alpha;
            double nb0, nb1, nb2, na0, na1, na2;
            if (low) {
                nb0 = a * ((a + 1) - (a - 1) * cos + sq);
                nb1 = 2 * a * ((a - 1) - (a + 1) * cos);
                nb2 = a * ((a + 1) - (a - 1) * cos - sq);
                na0 = (a + 1) + (a - 1) * cos + sq;
                na1 = -2 * ((a - 1) + (a + 1) * cos);
                na2 = (a + 1) + (a - 1) * cos - sq;
            } else {
                nb0 = a * ((a + 1) + (a - 1) * cos + sq);
                nb1 = -2 * a * ((a - 1) + (a + 1) * cos);
                nb2 = a * ((a + 1) + (a - 1) * cos - sq);
                na0 = (a + 1) - (a - 1) * cos + sq;
                na1 = 2 * ((a - 1) - (a + 1) * cos);
                na2 = (a + 1) - (a - 1) * cos - sq;
            }
            b0 = nb0 / na0;
            b1 = nb1 / na0;
            b2 = nb2 / na0;
            a1 = na1 / na0;
            a2 = na2 / na0;
        }

        double apply(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}

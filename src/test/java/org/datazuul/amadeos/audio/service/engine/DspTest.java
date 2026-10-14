package org.datazuul.amadeos.audio.service.engine;

import org.datazuul.amadeos.audio.domain.AudioLevels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DspTest {
    @Test
    void fftFindsTheSine() {
        int n = 64;
        double[] re = new double[n];
        double[] im = new double[n];
        for (int i = 0; i < n; i++) re[i] = Math.sin(2 * Math.PI * 5 * i / n);
        Fft.transform(re, im);
        int peak = 0;
        for (int i = 1; i < n / 2; i++) if (Math.hypot(re[i], im[i]) > Math.hypot(re[peak], im[peak])) peak = i;
        assertEquals(5, peak);
        assertThrows(IllegalArgumentException.class, () -> Fft.transform(new double[3], new double[3]));
    }

    @Test
    void meterScale() {
        assertEquals(0f, LevelAnalyzer.meter(0f), 0.001f);
        assertEquals(1f, LevelAnalyzer.meter(1f), 0.001f);
        assertEquals(0.5f, LevelAnalyzer.meter(0.1f), 0.001f); // -20 dB
        assertEquals(0f, LevelAnalyzer.meter(0.001f), 0.001f); // -60 dB
    }

    @Test
    void spectrumShowsLowAndHighTones() {
        float[] low = bands(100);
        float[] high = bands(8000);
        assertTrue(low[1] > 0.6f, "100 Hz shows in a low band");
        assertTrue(low[14] < 0.3f, "and not in a high one");
        assertTrue(high[14] > 0.6f || high[13] > 0.6f, "8 kHz shows in a high band");
    }

    private static float[] bands(double hz) {
        LevelAnalyzer analyzer = new LevelAnalyzer();
        SineSource sine = new SineSource(hz, 1);
        byte[] buffer = new byte[4096 * 4];
        AudioLevels last = null;
        int n;
        while ((n = sine.read(buffer, 0, buffer.length)) > 0) {
            AudioLevels l = analyzer.process(buffer, 0, n, sine.format());
            if (l != null) last = l;
        }
        assertNotNull(last);
        assertEquals(AudioLevels.BANDS, last.bands().length);
        return last.bands();
    }

    @Test
    void toneControlBoostsBass() {
        ToneControl tone = new ToneControl();
        assertTrue(tone.isFlat());
        assertEquals(peak(tone, 100), peak(tone, 100));
        int flat = peak(tone, 100);
        tone.set(12, 0);
        assertFalse(tone.isFlat());
        int boosted = peak(new ToneControlHolder(12, 0).tone, 100);
        assertTrue(boosted > flat * 1.8, "bass +12 dB: " + flat + " -> " + boosted);
        int trebleUntouched = peak(new ToneControlHolder(12, 0).tone, 10000);
        assertTrue(trebleUntouched < flat * 1.3, "treble stays: " + trebleUntouched);
        tone.set(99, -99);
        // clamped to +-12 dB, no exception
    }

    private record ToneControlHolder(ToneControl tone) {
        ToneControlHolder(double bass, double treble) {
            this(new ToneControl());
            tone.set(bass, treble);
        }
    }

    private static int peak(ToneControl tone, double hz) {
        SineSource s = new SineSource(hz, 0.5);
        byte[] b = new byte[(int) s.frameLength() * 4];
        int n = s.read(b, 0, b.length);
        // quieter input so the boost does not clip
        AudioChannel.applyGain(b, 0, n, 0.25f);
        tone.process(b, 0, n, 44100f, 2);
        int max = 0;
        for (int i = n / 2; i + 1 < n; i += 2) max = Math.max(max, Math.abs((short) ((b[i] & 0xFF) | (b[i + 1] << 8))));
        return max;
    }
}

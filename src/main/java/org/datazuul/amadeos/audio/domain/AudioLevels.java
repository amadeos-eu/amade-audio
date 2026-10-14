package org.datazuul.amadeos.audio.domain;

/**
 * Output level for the meters.
 *
 * @param left  peak of the left channel, 0 (silence or below -40 dB) to 1 (full scale)
 * @param right peak of the right channel
 * @param bands spectrum, 0..1 per band from low to high frequencies
 */
public record AudioLevels(float left, float right, float[] bands) {
    public static final int BANDS = 16;
    public static final AudioLevels SILENT = new AudioLevels(0, 0, new float[BANDS]);
}

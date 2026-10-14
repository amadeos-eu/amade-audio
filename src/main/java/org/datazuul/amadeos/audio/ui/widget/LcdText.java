package org.datazuul.amadeos.audio.ui.widget;

/** Text for the seven-segment displays. */
public final class LcdText {
    private LcdText() {
    }

    /** {@code mm:ss}, minutes up to 99; blank digits ({@code "  :  "}) for a negative value. */
    public static String time(long micros) {
        if (micros < 0) return "  :  ";
        long seconds = micros / 1_000_000;
        long minutes = Math.min(99, seconds / 60);
        return String.format("%02d:%02d", minutes, seconds % 60);
    }

    /** Two digits, blank for values below 1. */
    public static String number(int value) {
        return value < 1 ? "  " : String.format("%02d", Math.min(99, value));
    }
}

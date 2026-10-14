package org.datazuul.amadeos.audio.domain;

/** Shuffle and repeat of a playlist or CD. */
public record PlaybackOptions(boolean shuffle, boolean repeat) {
    public static final PlaybackOptions NORMAL = new PlaybackOptions(false, false);

    public PlaybackOptions withShuffle(boolean value) {
        return new PlaybackOptions(value, repeat);
    }

    public PlaybackOptions withRepeat(boolean value) {
        return new PlaybackOptions(shuffle, value);
    }
}

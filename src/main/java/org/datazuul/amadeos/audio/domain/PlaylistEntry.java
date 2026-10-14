package org.datazuul.amadeos.audio.domain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * A file in the player's playlist with the tags read from it.
 *
 * @param length {@link Duration#ZERO} if unknown
 */
public record PlaylistEntry(Path file, String title, String artist, String album, String year, Duration length,
                            String format) {
    public PlaylistEntry {
        Objects.requireNonNull(file, "file");
        title = blankToNull(title);
        artist = blankToNull(artist);
        album = blankToNull(album);
        year = blankToNull(year);
        length = length == null ? Duration.ZERO : length;
        format = format == null || format.isBlank() ? formatOf(file) : format;
    }

    /** An entry with only what the file name tells. */
    public static PlaylistEntry of(Path file) {
        return new PlaylistEntry(file, null, null, null, null, Duration.ZERO, null);
    }

    /** The title tag, or the file name without extension. */
    public String displayTitle() {
        if (title != null) return title;
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public boolean isMidi() {
        return "MIDI".equals(format);
    }

    /** Upper-case format name from the file extension, e.g. {@code FLAC}; {@code MIDI} for .mid/.midi/.kar. */
    public static String formatOf(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "mid", "midi", "kar", "rmi" -> "MIDI";
            case "oga", "ogg" -> "OGG";
            case "m4a", "aac", "mp4" -> "AAC";
            case "wave", "wav" -> "WAV";
            case "aif", "aiff" -> "AIFF";
            case "" -> "?";
            default -> ext.toUpperCase(Locale.ROOT);
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

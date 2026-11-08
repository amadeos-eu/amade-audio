package eu.amadeos.apps.audio.domain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A file in the player's playlist with the tags read from it.
 *
 * @param length {@link Duration#ZERO} if unknown
 */
public record PlaylistEntry(Path file, String title, String artist, String album, String year, Duration length,
                            String format) {
    /**
     * Extensions of tracker modules, played with libopenmpt: the classic four (MOD, XM, S3M, IT) and the
     * older or rarer ones libopenmpt also reads.
     */
    public static final Set<String> MODULE_EXTENSIONS = Set.of("mod", "xm", "s3m", "it", "mptm", "mtm", "669",
            "stm", "med", "okt", "far", "ult", "umx");

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

    /** Whether the file is a tracker module, by its extension or an Amiga-style {@code mod.} prefix. */
    public static boolean isModule(Path file) {
        String ext = extensionOf(file);
        return MODULE_EXTENSIONS.contains(ext) || (!ext.isEmpty() && hasAmigaModPrefix(file));
    }

    /**
     * Amiga collections name modules {@code mod.title} instead of {@code title.mod}. Only names with this one
     * dot count, so {@code mod.mix.mp3} stays an MP3.
     */
    private static boolean hasAmigaModPrefix(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("mod.") && name.length() > 4 && name.indexOf('.', 4) < 0;
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Upper-case format name from the file extension, e.g. {@code FLAC}; {@code MIDI} for .mid/.midi/.kar,
     * {@code MOD} for an Amiga-style {@code mod.title}.
     */
    public static String formatOf(Path file) {
        String ext = extensionOf(file);
        if (!MODULE_EXTENSIONS.contains(ext) && !ext.isEmpty() && hasAmigaModPrefix(file)) return "MOD";
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

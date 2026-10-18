package eu.amadeos.audio.service.recording;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** File names of recordings from the pattern in the settings. */
final class RecordingNames {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH-mm-ss");

    private RecordingNames() {
    }

    /** Splits {@code Artist - Title}; without a separator everything is the title. */
    static String[] artistAndTitle(String streamTitle) {
        if (streamTitle == null || streamTitle.isBlank()) return new String[]{"", ""};
        int dash = streamTitle.indexOf(" - ");
        if (dash < 0) return new String[]{"", streamTitle.strip()};
        return new String[]{streamTitle.substring(0, dash).strip(), streamTitle.substring(dash + 3).strip()};
    }

    /** Applies the pattern and removes what an unknown artist or title leaves behind. */
    static String name(String pattern, String station, LocalDateTime when, String streamTitle) {
        String[] at = artistAndTitle(streamTitle);
        String name = pattern
                .replace("{station}", safe(station))
                .replace("{date}", DATE.format(when))
                .replace("{time}", TIME.format(when))
                .replace("{artist}", safe(at[0]))
                .replace("{title}", safe(at[1]));
        name = name.replaceAll("\\s+-\\s*$", "").replaceAll("^\\s*-\\s+", "")
                .replaceAll("\\s+-\\s+-\\s+", " - ").replaceAll("\\s{2,}", " ").strip();
        if (name.endsWith(" -")) name = name.substring(0, name.length() - 2).strip();
        return name.isEmpty() ? safe(station) + " " + DATE.format(when) + " " + TIME.format(when) : name;
    }

    /** Removes characters that are not allowed or awkward in file names. */
    static String safe(String s) {
        if (s == null) return "";
        String cleaned = s.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_").strip();
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        return cleaned.length() > 120 ? cleaned.substring(0, 120).strip() : cleaned;
    }

    /** {@code name.ext}, or {@code name (2).ext} etc. if taken. */
    static Path unique(Path directory, String name, String extension) {
        Path candidate = directory.resolve(name + "." + extension);
        for (int i = 2; Files.exists(candidate) || Files.exists(part(candidate)); i++) {
            candidate = directory.resolve(name + " (" + i + ")." + extension);
        }
        return candidate;
    }

    static Path part(Path file) {
        return file.resolveSibling(file.getFileName() + ".part");
    }
}

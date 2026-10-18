package eu.amadeos.audio.domain;

import java.nio.file.Path;

/**
 * Options for recording internet radio.
 *
 * @param directory    base directory; recordings go into a sub-directory per station
 * @param pattern      file name without extension, with {@code {station}}, {@code {date}}, {@code {time}},
 *                     {@code {artist}} and {@code {title}}
 * @param splitByTitle start a new file when the station announces a new title
 * @param maxMinutes   stop after this many minutes, 0 for no limit
 */
public record RecordingSettings(Path directory, String pattern, boolean splitByTitle, int maxMinutes) {
    public static final String DEFAULT_PATTERN = "{date} {time} {artist} - {title}";

    public RecordingSettings {
        pattern = pattern == null || pattern.isBlank() ? DEFAULT_PATTERN : pattern.strip();
        maxMinutes = Math.max(0, maxMinutes);
    }

    public static RecordingSettings defaults(Path home) {
        return new RecordingSettings(home.resolve("Music").resolve("Radio"), DEFAULT_PATTERN, false, 120);
    }
}

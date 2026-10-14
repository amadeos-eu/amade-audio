package org.datazuul.amadeos.audio.repository;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.AudioPreferences;
import org.datazuul.amadeos.audio.domain.RecordingSettings;
import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.commons.config.AppDirectories;
import org.datazuul.amadeos.commons.config.PropertiesFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** {@link AudioPreferences} in {@code ~/.config/amadeos/audio/audio.conf}. */
public final class PreferencesRepository {
    private static final Logger LOG = LoggerFactory.getLogger(PreferencesRepository.class);
    static final String FILE = "audio.conf";

    private final Path file;
    private final Path home;

    @Inject
    public PreferencesRepository(AppDirectories directories) {
        this(directories.configFile(FILE), Path.of(System.getProperty("user.home")));
    }

    PreferencesRepository(Path file, Path home) {
        this.file = file;
        this.home = home;
    }

    public AudioPreferences load() {
        AudioPreferences defaults = AudioPreferences.defaults(home);
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            LOG.warn("Cannot read {}: {}", file, e.toString());
            return defaults;
        }
        Map<SourceId, Float> volumes = new EnumMap<>(SourceId.class);
        for (SourceId id : SourceId.values()) {
            volumes.put(id, (float) number(p, "volume." + key(id), defaults.volume(id)));
        }
        RecordingSettings rd = defaults.recording();
        RecordingSettings recording = new RecordingSettings(
                path(p, "recording.directory", rd.directory()),
                p.getProperty("recording.pattern", rd.pattern()),
                Boolean.parseBoolean(p.getProperty("recording.splitByTitle", String.valueOf(rd.splitByTitle()))),
                (int) number(p, "recording.maxMinutes", rd.maxMinutes()));
        Set<String> hidden = Arrays.stream(p.getProperty("racks.hidden", "").split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        return new AudioPreferences(volumes, number(p, "tone.bass", 0), number(p, "tone.treble", 0), hidden,
                recording, p.getProperty("lcd.color", "theme"),
                Boolean.parseBoolean(p.getProperty("recording.noticeShown", "false")),
                path(p, "player.lastFolder", null));
    }

    public void save(AudioPreferences prefs) {
        Properties p = new Properties();
        for (SourceId id : SourceId.values()) p.setProperty("volume." + key(id), fmt(prefs.volume(id)));
        p.setProperty("tone.bass", fmt(prefs.bassDb()));
        p.setProperty("tone.treble", fmt(prefs.trebleDb()));
        p.setProperty("racks.hidden", String.join(",", prefs.hiddenRacks().stream().sorted().toList()));
        p.setProperty("recording.directory", prefs.recording().directory().toString());
        p.setProperty("recording.pattern", prefs.recording().pattern());
        p.setProperty("recording.splitByTitle", String.valueOf(prefs.recording().splitByTitle()));
        p.setProperty("recording.maxMinutes", String.valueOf(prefs.recording().maxMinutes()));
        p.setProperty("recording.noticeShown", String.valueOf(prefs.recordingNoticeShown()));
        p.setProperty("lcd.color", prefs.lcdColor());
        if (prefs.lastFolder() != null) p.setProperty("player.lastFolder", prefs.lastFolder().toString());
        try {
            PropertiesFile.store(file, p, "amade-audio settings");
        } catch (RuntimeException e) {
            LOG.warn("Cannot save settings: {}", e.toString());
        }
    }

    private static String key(SourceId id) {
        return id.name().toLowerCase(Locale.ROOT);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static double number(Properties p, String key, double fallback) {
        try {
            String v = p.getProperty(key);
            return v == null ? fallback : Double.parseDouble(v.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Path path(Properties p, String key, Path fallback) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Path.of(v.strip());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}

package eu.amadeos.apps.audio.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.audio.domain.AudioPreferences;
import eu.amadeos.apps.audio.domain.RecordingSettings;
import eu.amadeos.apps.audio.domain.SourceId;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.commons.net.ProxySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
                path(p, "player.lastFolder", null),
                (int) number(p, "racks.size", AudioPreferences.DEFAULT_SIZE));
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
        p.setProperty("racks.size", String.valueOf(prefs.size()));
        if (prefs.lastFolder() != null) p.setProperty("player.lastFolder", prefs.lastFolder().toString());
        try {
            PropertiesFile.store(file, p, "amade-audio settings");
        } catch (RuntimeException e) {
            LOG.warn("Cannot save settings: {}", e.toString());
        }
    }

    /**
     * The proxy of earlier versions, which had a Network page of their own, for moving it to Settings &gt; Network
     * once; empty if none was set (mode "system") or the settings were saved since, which drops it.
     */
    public Optional<ProxySettings> legacyProxy() {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String mode = p.getProperty("proxy.mode", "system").strip().toLowerCase(Locale.ROOT);
        ProxySettings manual = ProxySettings.manual(p.getProperty("proxy.host", ""),
                (int) number(p, "proxy.port", ProxySettings.DEFAULT_PORT), p.getProperty("proxy.user", ""),
                p.getProperty("proxy.noProxy", ProxySettings.DEFAULT_NO_PROXY));
        return switch (mode) {
            case "manual" -> Optional.of(manual);
            case "none" -> Optional.of(manual.withMode(ProxySettings.Mode.NONE));
            default -> Optional.empty();
        };
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

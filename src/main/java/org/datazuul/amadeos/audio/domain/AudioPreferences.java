package org.datazuul.amadeos.audio.domain;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Settings the user changes in the window: fader positions, tone, which racks are shown, recording
 * options and the LCD colour.
 *
 * @param volumes     fader position 0..1 per device
 * @param hiddenRacks rack ids hidden with the rack buttons: {@code mixer}, {@code cd}, {@code player}, {@code webradio}
 * @param lcdColor    {@code theme} or a fixed colour name, see the LCD palette
 * @param lastFolder  last folder opened in the player, may be null
 * @param proxy       network proxy for streams and web services
 * @param size        size of the racks in percent: 60, 80 or 100
 */
public record AudioPreferences(Map<SourceId, Float> volumes, double bassDb, double trebleDb, Set<String> hiddenRacks,
                               RecordingSettings recording, String lcdColor, boolean recordingNoticeShown,
                               Path lastFolder, ProxySettings proxy, int size) {
    public AudioPreferences {
        Map<SourceId, Float> v = new EnumMap<>(SourceId.class);
        for (SourceId id : SourceId.values()) v.put(id, 0.8f);
        if (volumes != null) v.putAll(volumes);
        volumes = Map.copyOf(v);
        hiddenRacks = hiddenRacks == null ? Set.of() : Set.copyOf(hiddenRacks);
        lcdColor = lcdColor == null || lcdColor.isBlank() ? "theme" : lcdColor;
        proxy = proxy == null ? ProxySettings.DEFAULT : proxy;
        size = normalizeSize(size);
    }

    public static AudioPreferences defaults(Path home) {
        return new AudioPreferences(null, 0, 0, Set.of(), RecordingSettings.defaults(home), "theme", false, null,
                ProxySettings.DEFAULT, DEFAULT_SIZE);
    }

    /** Sizes of the racks offered in the settings, in percent. */
    public static final int[] SIZES = {60, 80, 100};
    public static final int DEFAULT_SIZE = 60;

    /** The nearest of {@link #SIZES}. */
    public static int normalizeSize(int percent) {
        int best = SIZES[0];
        for (int s : SIZES) if (Math.abs(s - percent) < Math.abs(best - percent)) best = s;
        return best;
    }

    public float volume(SourceId id) {
        return volumes.getOrDefault(id, 0.8f);
    }

    public AudioPreferences withVolume(SourceId id, float value) {
        Map<SourceId, Float> v = new EnumMap<>(volumes);
        v.put(id, value);
        return new AudioPreferences(v, bassDb, trebleDb, hiddenRacks, recording, lcdColor, recordingNoticeShown, lastFolder, proxy, size);
    }

    public AudioPreferences withTone(double bass, double treble) {
        return new AudioPreferences(volumes, bass, treble, hiddenRacks, recording, lcdColor, recordingNoticeShown, lastFolder, proxy, size);
    }

    public AudioPreferences withHiddenRacks(Set<String> racks) {
        return new AudioPreferences(volumes, bassDb, trebleDb, racks, recording, lcdColor, recordingNoticeShown, lastFolder, proxy, size);
    }

    public AudioPreferences withRecording(RecordingSettings settings) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, settings, lcdColor, recordingNoticeShown, lastFolder, proxy, size);
    }

    public AudioPreferences withLcdColor(String color) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, color, recordingNoticeShown, lastFolder, proxy, size);
    }

    public AudioPreferences withRecordingNoticeShown() {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, lcdColor, true, lastFolder, proxy, size);
    }

    public AudioPreferences withLastFolder(Path folder) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, lcdColor, recordingNoticeShown, folder, proxy, size);
    }

    public AudioPreferences withSize(int percent) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, lcdColor, recordingNoticeShown, lastFolder, proxy, percent);
    }

    public AudioPreferences withProxy(ProxySettings value) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, lcdColor, recordingNoticeShown, lastFolder, value, size);
    }
}

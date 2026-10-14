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
 */
public record AudioPreferences(Map<SourceId, Float> volumes, double bassDb, double trebleDb, Set<String> hiddenRacks,
                               RecordingSettings recording, String lcdColor, boolean recordingNoticeShown,
                               Path lastFolder, ProxySettings proxy) {
    public AudioPreferences {
        Map<SourceId, Float> v = new EnumMap<>(SourceId.class);
        for (SourceId id : SourceId.values()) v.put(id, 0.8f);
        if (volumes != null) v.putAll(volumes);
        volumes = Map.copyOf(v);
        hiddenRacks = hiddenRacks == null ? Set.of() : Set.copyOf(hiddenRacks);
        lcdColor = lcdColor == null || lcdColor.isBlank() ? "theme" : lcdColor;
        proxy = proxy == null ? ProxySettings.DEFAULT : proxy;
    }

    public static AudioPreferences defaults(Path home) {
        return new AudioPreferences(null, 0, 0, Set.of(), RecordingSettings.defaults(home), "theme", false, null,
                ProxySettings.DEFAULT);
    }

    public float volume(SourceId id) {
        return volumes.getOrDefault(id, 0.8f);
    }

    public AudioPreferences withVolume(SourceId id, float value) {
        Map<SourceId, Float> v = new EnumMap<>(volumes);
        v.put(id, value);
        return new AudioPreferences(v, bassDb, trebleDb, hiddenRacks, recording, lcdColor, recordingNoticeShown, lastFolder, proxy);
    }

    public AudioPreferences withTone(double bass, double treble) {
        return new AudioPreferences(volumes, bass, treble, hiddenRacks, recording, lcdColor, recordingNoticeShown, lastFolder, proxy);
    }

    public AudioPreferences withHiddenRacks(Set<String> racks) {
        return new AudioPreferences(volumes, bassDb, trebleDb, racks, recording, lcdColor, recordingNoticeShown, lastFolder, proxy);
    }

    public AudioPreferences withRecording(RecordingSettings settings) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, settings, lcdColor, recordingNoticeShown, lastFolder, proxy);
    }

    public AudioPreferences withLcdColor(String color) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, color, recordingNoticeShown, lastFolder, proxy);
    }

    public AudioPreferences withRecordingNoticeShown() {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, lcdColor, true, lastFolder, proxy);
    }

    public AudioPreferences withLastFolder(Path folder) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, lcdColor, recordingNoticeShown, folder, proxy);
    }

    public AudioPreferences withProxy(ProxySettings value) {
        return new AudioPreferences(volumes, bassDb, trebleDb, hiddenRacks, recording, lcdColor, recordingNoticeShown, lastFolder, value);
    }
}

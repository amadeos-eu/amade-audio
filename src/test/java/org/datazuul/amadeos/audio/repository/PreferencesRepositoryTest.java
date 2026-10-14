package org.datazuul.amadeos.audio.repository;

import org.datazuul.amadeos.audio.domain.AudioPreferences;
import org.datazuul.amadeos.audio.domain.RecordingSettings;
import org.datazuul.amadeos.audio.domain.SourceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PreferencesRepositoryTest {
    @TempDir
    Path dir;

    @Test
    void defaultsWithoutAFile() {
        AudioPreferences p = new PreferencesRepository(dir.resolve("audio.conf"), dir).load();
        assertEquals(0.8f, p.volume(SourceId.CD), 0.001f);
        assertEquals(dir.resolve("Music/Radio"), p.recording().directory());
        assertEquals("theme", p.lcdColor());
        assertFalse(p.recordingNoticeShown());
    }

    @Test
    void roundTrip() {
        PreferencesRepository repo = new PreferencesRepository(dir.resolve("audio.conf"), dir);
        AudioPreferences p = AudioPreferences.defaults(dir)
                .withVolume(SourceId.WEBRADIO, 0.25f)
                .withTone(6, -3)
                .withHiddenRacks(Set.of("mixer", "cd"))
                .withRecording(new RecordingSettings(dir.resolve("rec"), "{title}", true, 30))
                .withLcdColor("amber")
                .withRecordingNoticeShown()
                .withLastFolder(dir.resolve("music"));
        repo.save(p);
        AudioPreferences loaded = repo.load();
        assertEquals(0.25f, loaded.volume(SourceId.WEBRADIO), 0.001f);
        assertEquals(6, loaded.bassDb(), 0.001);
        assertEquals(-3, loaded.trebleDb(), 0.001);
        assertEquals(Set.of("mixer", "cd"), loaded.hiddenRacks());
        assertEquals(p.recording(), loaded.recording());
        assertEquals("amber", loaded.lcdColor());
        assertTrue(loaded.recordingNoticeShown());
        assertEquals(dir.resolve("music"), loaded.lastFolder());
    }
}

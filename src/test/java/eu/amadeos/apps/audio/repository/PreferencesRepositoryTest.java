package eu.amadeos.apps.audio.repository;

import eu.amadeos.apps.audio.domain.AudioPreferences;
import eu.amadeos.commons.net.ProxySettings;
import eu.amadeos.apps.audio.domain.RecordingSettings;
import eu.amadeos.apps.audio.domain.SourceId;
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
        assertEquals(AudioPreferences.AUTO, p.size());
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
                .withLastFolder(dir.resolve("music"))
                .withSize(80);
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
        assertEquals(80, loaded.size());
    }

    @Test
    void theProxyOfEarlierVersionsIsReadOnceForSettings() throws Exception {
        Path file = dir.resolve("audio.conf");
        PreferencesRepository repo = new PreferencesRepository(file, dir);
        assertTrue(repo.legacyProxy().isEmpty());
        java.nio.file.Files.writeString(file, "lcd.color=amber\nproxy.mode=manual\nproxy.host=proxy.example\n"
                + "proxy.port=8080\nproxy.user=ralf\nproxy.noProxy=localhost, .lan\n");
        assertEquals(java.util.Optional.of(ProxySettings.manual("proxy.example", 8080, "ralf", "localhost, .lan")),
                repo.legacyProxy());
        repo.save(repo.load());
        assertTrue(repo.legacyProxy().isEmpty(), "saving drops it");
        assertEquals("amber", repo.load().lcdColor());
    }

    @Test
    void sizeIsOneOfTheOfferedOnes() {
        assertEquals(100, AudioPreferences.defaults(dir).withSize(140).size());
        assertEquals(80, AudioPreferences.defaults(dir).withSize(75).size());
        assertEquals(60, AudioPreferences.defaults(dir).withSize(10).size());
        assertEquals(AudioPreferences.AUTO, AudioPreferences.defaults(dir).withSize(0).size());
    }
}

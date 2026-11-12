package eu.amadeos.apps.audio.ui.rack.player;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRackTest {
    @Test
    void everyPlayableFormatLightsABadge() {
        assertEquals("MOD", PlayerRack.badgeFor("MOD"));
        assertEquals("MOD", PlayerRack.badgeFor("XM"));
        assertEquals("MOD", PlayerRack.badgeFor("S3M"));
        assertEquals("MOD", PlayerRack.badgeFor("IT"));
        assertEquals("WAV", PlayerRack.badgeFor("AIFF"));
        assertEquals("FLAC", PlayerRack.badgeFor("FLAC"));
        assertEquals("", PlayerRack.badgeFor(null));
        for (String f : List.of("MP3", "OGG", "FLAC", "WAV", "AIFF", "AU", "AAC", "MIDI", "MOD", "XM", "S3M", "IT")) {
            assertTrue(List.of(PlayerRack.FORMATS).contains(PlayerRack.badgeFor(f)), f);
        }
    }
}

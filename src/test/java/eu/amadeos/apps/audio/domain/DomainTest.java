package eu.amadeos.apps.audio.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    @Test
    void formatComesFromTheExtension() {
        assertEquals("MIDI", PlaylistEntry.formatOf(Path.of("song.MID")));
        assertEquals("OGG", PlaylistEntry.formatOf(Path.of("a.oga")));
        assertEquals("AAC", PlaylistEntry.formatOf(Path.of("a.m4a")));
        assertEquals("FLAC", PlaylistEntry.formatOf(Path.of("/music/a.flac")));
        assertEquals("?", PlaylistEntry.formatOf(Path.of("README")));
    }

    @Test
    void displayTitleFallsBackToTheFileName() {
        assertEquals("01 Intro", PlaylistEntry.of(Path.of("/m/01 Intro.mp3")).displayTitle());
        PlaylistEntry tagged = new PlaylistEntry(Path.of("/m/x.mp3"), " Intro ", "", null, null, null, null);
        assertEquals("Intro", tagged.displayTitle());
        assertNull(tagged.artist());
        assertEquals(Duration.ZERO, tagged.length());
        assertEquals("MP3", tagged.format());
    }

    @Test
    void stationGenreAndIdentity() {
        Station a = new Station("u1", "Jazz FM", "http://a", "de", "jazz,smooth", "mp3", 128, null);
        assertEquals("Jazz", a.genre());
        assertEquals("DE", a.country());
        assertEquals("MP3 128 kbit/s", a.streamInfo());
        assertTrue(a.sameAs(new Station("u1", "Other", "http://b", "", "", "", 0, "")));
        assertFalse(a.sameAs(new Station("u2", "Jazz FM", "http://a", "", "", "", 0, "")));
        Station byHand = new Station("", "", "http://x", "", "", "", 0, "");
        assertEquals("http://x", byHand.name());
        assertTrue(byHand.sameAs(new Station("", "X", "http://x", "", "", "", 0, "")));
        assertEquals("", byHand.genre());
    }

    @Test
    void tocTracksAndLengths() {
        CdToc toc = CdToc.of(1, new int[]{150, 7650, 15150}, 22650, new boolean[]{true, true, false});
        assertEquals(3, toc.tracks().size());
        assertEquals(7500, toc.tracks().get(0).sectors());
        assertEquals(Duration.ofSeconds(100), toc.tracks().get(0).length());
        assertEquals(2, toc.audioTracks().size());
        assertEquals(Duration.ofSeconds(200), toc.length());
        assertEquals(3, toc.lastTrack());
        assertTrue(toc.track(2).isPresent());
    }

    @Test
    void albumInfoNamesUnknownTracks() {
        AlbumInfo unknown = AlbumInfo.unknown(2);
        assertFalse(unknown.isKnown());
        assertEquals("Track 02", unknown.title(1));
        AlbumInfo known = new AlbumInfo("A", "Band", "1999", "", java.util.List.of("One", ""),
                java.util.List.of("", "Guest"), "", "", "x");
        assertTrue(known.isKnown());
        assertEquals("Band", known.artist(0));
        assertEquals("Guest", known.artist(1));
        assertEquals("Track 02", known.title(1));
    }
}

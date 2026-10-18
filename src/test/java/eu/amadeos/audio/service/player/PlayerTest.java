package eu.amadeos.audio.service.player;

import eu.amadeos.audio.domain.PlaybackOptions;
import eu.amadeos.audio.domain.PlaylistEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    @TempDir
    Path dir;

    private static List<PlaylistEntry> entries(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> PlaylistEntry.of(Path.of("/m/" + i + ".mp3"))).toList();
    }

    @Test
    void nextAndPreviousInOrder() {
        Playlist p = new Playlist();
        p.addAll(entries(3));
        assertEquals(0, p.first());
        p.setCurrent(1);
        assertEquals(2, p.next());
        assertEquals(0, p.previous());
        p.setCurrent(2);
        assertEquals(-1, p.next());
        p.setOptions(PlaybackOptions.NORMAL.withRepeat(true));
        assertEquals(0, p.next());
        p.setCurrent(0);
        assertEquals(2, p.previous());
    }

    @Test
    void shufflePlaysEveryEntryOnce() {
        Playlist p = new Playlist(new Random(7));
        p.addAll(entries(10));
        p.setOptions(new PlaybackOptions(true, false));
        Set<Integer> seen = new HashSet<>();
        int i = p.first();
        while (i >= 0) {
            assertTrue(seen.add(i), "played twice: " + i);
            p.setCurrent(i);
            i = p.next();
        }
        assertEquals(10, seen.size());
    }

    @Test
    void removingKeepsTheCurrentEntry() {
        Playlist p = new Playlist();
        p.addAll(entries(5));
        p.setCurrent(3);
        p.remove(new int[]{0, 1});
        assertEquals(1, p.currentIndex());
        assertEquals(Path.of("/m/3.mp3"), p.current().file());
        p.remove(new int[]{1});
        assertEquals(-1, p.currentIndex());
        p.replace(0, new PlaylistEntry(Path.of("/m/2.mp3"), "T", null, null, null, Duration.ofSeconds(90), null));
        assertEquals(Duration.ofSeconds(90), p.totalLength());
        p.clear();
        assertTrue(p.isEmpty());
    }

    @Test
    void m3uRoundTrip() throws Exception {
        Path music = Files.createDirectories(dir.resolve("music"));
        Path a = Files.createFile(music.resolve("a.mp3"));
        Path b = Files.createFile(dir.resolve("b.flac"));
        Path list = music.resolve("list.m3u");
        M3uPlaylist.write(list, List.of(new PlaylistEntry(a, "A", "X", null, null, Duration.ofSeconds(61), null), PlaylistEntry.of(b)));
        String text = Files.readString(list);
        assertTrue(text.startsWith("#EXTM3U\n#EXTINF:61,X - A\na.mp3\n"), text);
        assertEquals(List.of(a, b), M3uPlaylist.read(list));
        Files.writeString(list, "#EXTM3U\nhttp://radio.example/stream\nsub/c.ogg\n");
        assertEquals(List.of(music.resolve("sub/c.ogg")), M3uPlaylist.read(list));
    }

    @Test
    void findsAudioFilesInFolders() throws Exception {
        Files.createDirectories(dir.resolve("x/y"));
        Files.createFile(dir.resolve("x/1.mp3"));
        Files.createFile(dir.resolve("x/y/2.MID"));
        Files.createFile(dir.resolve("x/cover.jpg"));
        assertEquals(List.of(dir.resolve("x/1.mp3"), dir.resolve("x/y/2.MID")), AudioFiles.inFolder(dir.resolve("x")));
        assertTrue(AudioFiles.isPlaylist(Path.of("a.M3U8")));
    }
}

package eu.amadeos.apps.audio.service.webradio;

import eu.amadeos.apps.audio.domain.Station;
import eu.amadeos.apps.audio.service.decode.DecoderFactory.StreamCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebRadioTest {
    @TempDir
    Path dir;

    @Test
    void icyStreamRemovesMetadataAndReportsTitles() throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] audio1 = "AAAAAAAA".getBytes();
        byte[] audio2 = "BBBBBBBB".getBytes();
        raw.write(audio1);
        byte[] meta = "StreamTitle='Miles Davis - So What';StreamUrl='';".getBytes(StandardCharsets.UTF_8);
        int blocks = (meta.length + 15) / 16;
        raw.write(blocks);
        raw.write(meta);
        raw.write(new byte[blocks * 16 - meta.length]);
        raw.write(audio2);
        raw.write(0); // empty metadata block
        raw.write("CC".getBytes());
        List<String> titles = new ArrayList<>();
        try (IcyInputStream in = new IcyInputStream(new ByteArrayInputStream(raw.toByteArray()), 8, titles::add)) {
            assertEquals("AAAAAAAABBBBBBBBCC", new String(in.readAllBytes()));
        }
        assertEquals(List.of("Miles Davis - So What"), titles);
        assertNull(IcyInputStream.streamTitle("StreamTitle='';"));
    }

    @Test
    void parsesPlsAndM3u() {
        String pls = "[playlist]\nNumberOfEntries=2\nFile1=http://a.example/stream\nTitle1=A\nFile2=https://b.example/x\n";
        assertEquals(List.of("http://a.example/stream", "https://b.example/x"), PlaylistParser.parse(pls, "audio/x-scpls", "/x.pls"));
        String m3u = "#EXTM3U\n#EXTINF:-1,Station\nhttp://c.example/live.mp3\n\nhttp://c.example/live.mp3\n";
        assertEquals(List.of("http://c.example/live.mp3"), PlaylistParser.parse(m3u, "audio/x-mpegurl", "/x.m3u"));
        assertTrue(PlaylistParser.isPlaylistUrl("http://x.example/listen.pls?id=1"));
        assertFalse(PlaylistParser.isPlaylistUrl("http://x.example/stream"));
    }

    @Test
    void sniffsCodecs() {
        byte[] mp3 = {0x00, 0x11, (byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x64, 0, 0};
        StreamSniffer.Detection d = StreamSniffer.detect(mp3, mp3.length, "");
        assertEquals(StreamCodec.MP3, d.codec());
        assertEquals(2, d.start());

        byte[] adts = {0x00, (byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80, 0, 0};
        assertEquals(StreamCodec.AAC, StreamSniffer.detect(adts, adts.length, "").codec());

        byte[] ogg = "OggS....".getBytes();
        assertEquals(StreamCodec.OGG, StreamSniffer.detect(ogg, ogg.length, "").codec());

        byte[] id3 = new byte[30];
        id3[0] = 'I';
        id3[1] = 'D';
        id3[2] = '3';
        id3[9] = 5; // tag body of 5 bytes
        id3[15] = (byte) 0xFF;
        id3[16] = (byte) 0xFB;
        id3[17] = (byte) 0x90;
        StreamSniffer.Detection afterTag = StreamSniffer.detect(id3, id3.length, "");
        assertEquals(15, afterTag.start());

        byte[] noise = new byte[64];
        assertEquals(StreamCodec.AAC, StreamSniffer.detect(noise, noise.length, "audio/aacp").codec());
        assertEquals(StreamCodec.MP3, StreamSniffer.detect(noise, noise.length, "").codec());
    }

    @Test
    void bufferPrebuffersAndEnds() throws Exception {
        BufferedRadioInputStream buffer = new BufferedRadioInputStream(2);
        assertTrue(buffer.push("ab".getBytes()));
        assertTrue(buffer.push("cd".getBytes()));
        buffer.markFinished();
        assertEquals("abcd", new String(buffer.readAllBytes()));
        assertEquals(-1, buffer.read());
        buffer.close();
        assertFalse(buffer.push("x".getBytes()));
        assertTrue(buffer.isClosed());
    }

    @Test
    void favoritesTakeOverDzRadio() throws Exception {
        Path dz = dir.resolve("channels.json");
        Files.writeString(dz, """
                [{"name":"Antenne Bayern","url":"http://stream.antenne.de/antenne","codec":"MP3","bitrate":128,
                  "stationuuid":"961c0282","favicon":null}]""");
        StationRepository repo = new StationRepository(dir.resolve("audio/stations.json"), dz);
        assertEquals(1, repo.favorites().size());
        assertEquals("961c0282", repo.favorites().get(0).uuid());
        assertTrue(Files.exists(dir.resolve("audio/stations.json")));

        Station jazz = new Station("u2", "Jazz", "http://j", "FR", "jazz", "AAC", 64, "");
        assertTrue(repo.add(jazz));
        assertFalse(repo.add(jazz));
        repo.move(jazz, -1);
        StationRepository reloaded = new StationRepository(dir.resolve("audio/stations.json"), dz);
        assertEquals("Jazz", reloaded.favorites().get(0).name());
        assertEquals("FR", reloaded.favorites().get(0).country());
        reloaded.remove(jazz);
        assertFalse(reloaded.isFavorite(jazz));
    }

    @Test
    void defaultFavoritesWithoutAnyFile() {
        StationRepository repo = new StationRepository(dir.resolve("none.json"), dir.resolve("no-dz.json"));
        assertFalse(repo.favorites().isEmpty());
    }

    @Test
    void readsRadioBrowserJson() {
        String json = """
                [{"stationuuid":"s1","name":" Klassik ","url":"http://x/pls","url_resolved":"http://x/stream",
                  "countrycode":"DE","tags":"classical,orchestra","codec":"AAC","bitrate":128,"favicon":"http://x/i.png"},
                 {"stationuuid":"s2","name":"Broken","url":"","url_resolved":""}]""";
        List<Station> stations = RadioBrowserClient.stations(json);
        assertEquals(1, stations.size());
        assertEquals("http://x/stream", stations.get(0).url());
        assertEquals("Klassik", stations.get(0).name());
        assertEquals("Classical", stations.get(0).genre());
        assertEquals(java.util.Map.of("DE", 5000), RadioBrowserClient.counts("[{\"name\":\"DE\",\"stationcount\":5000}]"));
    }
}

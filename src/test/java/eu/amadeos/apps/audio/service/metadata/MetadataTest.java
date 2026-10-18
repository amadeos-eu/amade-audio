package eu.amadeos.apps.audio.service.metadata;

import eu.amadeos.apps.audio.domain.AlbumInfo;
import eu.amadeos.apps.audio.domain.Artwork;
import eu.amadeos.apps.audio.domain.CdToc;
import eu.amadeos.apps.audio.domain.Station;
import eu.amadeos.apps.audio.service.cd.CdDisc;
import eu.amadeos.apps.audio.service.cd.CdText;
import eu.amadeos.apps.audio.service.cd.DiscId;
import eu.amadeos.apps.audio.service.player.TagReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MetadataTest {
    @TempDir
    Path dir;

    private static final CdToc TOC = CdToc.of(1, new int[]{150, 10000, 20000}, 30000, null);
    private static final String DISC = DiscId.of(TOC);

    private static String release(String id, String title, int mediaCount, String discOnMedium) {
        StringBuilder media = new StringBuilder();
        for (int m = 1; m <= mediaCount; m++) {
            if (m > 1) media.append(',');
            String discs = ("" + m).equals(discOnMedium) ? "[{\"id\":\"" + DISC + "\"}]" : "[]";
            media.append("""
                    {"position": %d, "track-count": 3, "discs": %s, "tracks": [
                      {"title": "One %d", "artist-credit": [{"name": "Band", "joinphrase": ""}]},
                      {"title": "Two %d", "artist-credit": [{"name": "Band", "joinphrase": " feat. "}, {"name": "Guest", "joinphrase": ""}]},
                      {"title": "Three %d", "artist-credit": [{"name": "Band", "joinphrase": ""}]}]}""".formatted(m, discs, m, m, m));
        }
        return """
                {"id": "%s", "title": "%s", "date": "1999-05-01", "country": "DE",
                 "artist-credit": [{"name": "Band", "joinphrase": ""}],
                 "label-info": [{"label": {"name": "Label X"}}],
                 "release-group": {"id": "rg-1"},
                 "media": [%s]}""".formatted(id, title, media);
    }

    @Test
    void parsesTheMediumWithTheDisc() {
        String json = "{\"releases\": [" + release("r1", "Album", 2, "2") + "]}";
        List<AlbumInfo> infos = MusicBrainzClient.parse(json, DISC, 3);
        assertEquals(1, infos.size());
        AlbumInfo a = infos.get(0);
        assertEquals("Album (CD 2)", a.album());
        assertEquals("Band", a.artist());
        assertEquals("1999", a.year());
        assertEquals("Label X", a.label());
        assertEquals(List.of("One 2", "Two 2", "Three 2"), a.titles());
        assertEquals("", a.artists().get(0));
        assertEquals("Band feat. Guest", a.artists().get(1));
        assertEquals("r1", a.releaseId());
        assertEquals("rg-1", a.releaseGroupId());
        assertEquals("MusicBrainz · DE", a.source());
    }

    @Test
    void fallsBackToTheTocLookup() throws Exception {
        List<URI> asked = new ArrayList<>();
        HttpFetcher http = (uri, accept) -> {
            asked.add(uri);
            if (uri.toString().contains("toc=")) {
                return Optional.of(("{\"releases\": [" + release("r2", "Fuzzy", 1, "none") + "]}").getBytes());
            }
            return Optional.empty();
        };
        List<AlbumInfo> found = new MusicBrainzClient(http).lookup(DISC, TOC);
        assertEquals(2, asked.size());
        assertTrue(asked.get(1).toString().contains("toc=1+3+30000+150+10000+20000"), asked.get(1).toString());
        assertEquals("Fuzzy", found.get(0).album());
    }

    @Test
    void cdTextThenCacheThenOnline() throws Exception {
        MetadataCache cache = new MetadataCache(dir);
        Map<String, Integer> calls = new HashMap<>();
        HttpFetcher http = (uri, accept) -> {
            calls.merge("mb", 1, Integer::sum);
            return Optional.of(("{\"releases\": [" + release("r1", "Online", 1, "1") + "]}").getBytes());
        };
        CdMetadataService service = new CdMetadataService(new MusicBrainzClient(http), cache);
        CdDisc withText = new CdDisc(TOC, new CdText("Text Album", "Band", List.of("A", "B", "C"), List.of("Band", "Band", "Guest")), DISC);
        AlbumInfo offline = service.offline(withText);
        assertEquals("Text Album", offline.album());
        assertEquals("CD-Text", offline.source());
        assertEquals("Guest", offline.artist(2));
        assertEquals("Band", offline.artist(0));
        assertFalse(service.isCached(withText));

        List<AlbumInfo> online = service.lookup(withText);
        service.remember(withText, online.get(0).withTitles(List.of("x", "y", "z"), "Edited"));
        assertTrue(service.isCached(withText));
        AlbumInfo cached = service.offline(withText);
        assertEquals("Online", cached.album());
        assertEquals(List.of("x", "y", "z"), cached.titles());
        assertEquals("Edited", cached.source());
        assertEquals("rg-1", cached.releaseGroupId());

        CdDisc plain = new CdDisc(CdToc.of(1, new int[]{150}, 5000, null), CdText.NONE, DiscId.of(CdToc.of(1, new int[]{150}, 5000, null)));
        assertFalse(service.offline(plain).isKnown());
    }

    @Test
    void coverFromTheArchiveWithReleaseGroupFallbackAndCache() {
        List<String> asked = new ArrayList<>();
        HttpFetcher http = (uri, accept) -> {
            asked.add(uri.toString());
            return uri.toString().contains("release-group/rg-1") ? Optional.of(new byte[]{1, 2, 3}) : Optional.empty();
        };
        MetadataCache cache = new MetadataCache(dir);
        ArtworkService artwork = new ArtworkService(http, cache, new TagReader());
        AlbumInfo album = new AlbumInfo("A", "B", "", "", List.of(), List.of(), "r1", "rg-1", "MusicBrainz");
        Optional<Artwork> art = artwork.forCd(DISC, album);
        assertArrayEquals(new byte[]{1, 2, 3}, art.orElseThrow().data());
        assertEquals(List.of(ArtworkService.COVER_ART_ARCHIVE + "release/r1/front-500",
                ArtworkService.COVER_ART_ARCHIVE + "release-group/rg-1/front-500"), asked);
        assertTrue(artwork.forCd(DISC, album).isPresent());
        assertEquals(2, asked.size(), "second time from the cache");
        assertTrue(artwork.forCd(DiscId.of(CdToc.of(1, new int[]{150}, 9000, null)), AlbumInfo.unknown(1)).isEmpty());
    }

    @Test
    void coverImageInTheFolder() throws Exception {
        Path album = Files.createDirectories(dir.resolve("album"));
        Path song = Files.createFile(album.resolve("01.mp3"));
        Files.write(album.resolve("back.jpg"), new byte[]{9});
        ArtworkService artwork = new ArtworkService((u, a) -> Optional.empty(), new MetadataCache(dir.resolve("c")), new TagReader());
        assertTrue(artwork.forFile(song).isEmpty());
        Files.write(album.resolve("Folder.JPG"), new byte[]{7});
        Files.write(album.resolve("cover.png"), new byte[]{8});
        Artwork art = artwork.forFile(song).orElseThrow();
        assertArrayEquals(new byte[]{8}, art.data());
        assertEquals("cover.png", art.source());
    }

    @Test
    void stationLogosAreCached() {
        int[] calls = {0};
        HttpFetcher http = (uri, accept) -> {
            calls[0]++;
            return Optional.of(new byte[]{5});
        };
        ArtworkService artwork = new ArtworkService(http, new MetadataCache(dir), new TagReader());
        Station s = new Station("u", "S", "http://s", "", "", "", 0, "https://s.example/logo.png");
        assertTrue(artwork.forStation(s).isPresent());
        assertTrue(artwork.forStation(s).isPresent());
        assertEquals(1, calls[0]);
        assertTrue(artwork.forStation(new Station("u", "S", "http://s", "", "", "", 0, "file:///etc/passwd")).isEmpty());
    }

    @Test
    void cacheRejectsStrangeDiscIds() {
        MetadataCache cache = new MetadataCache(dir);
        assertThrows(IllegalArgumentException.class, () -> cache.album("../../etc"));
    }
}

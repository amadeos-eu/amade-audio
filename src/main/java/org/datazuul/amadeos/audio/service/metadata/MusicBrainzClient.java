package org.datazuul.amadeos.audio.service.metadata;

import org.datazuul.amadeos.audio.domain.AlbumInfo;
import org.datazuul.amadeos.audio.domain.CdToc;
import org.datazuul.amadeos.audio.service.Json;
import org.datazuul.amadeos.audio.service.cd.DiscId;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Looks up CDs on <a href="https://musicbrainz.org">MusicBrainz</a> by disc ID, falling back to a fuzzy
 * TOC lookup. Keeps to MusicBrainz' limit of one request per second.
 */
public final class MusicBrainzClient {
    static final String BASE = "https://musicbrainz.org/ws/2/discid/";
    private static final String INC = "inc=recordings+artist-credits+labels+release-groups&fmt=json";

    private final HttpFetcher http;
    private long lastRequest;

    @com.google.inject.Inject
    public MusicBrainzClient(HttpFetcher http) {
        this.http = http;
    }

    /** Releases with this disc, each with the titles of the medium that has it; empty if unknown. */
    public List<AlbumInfo> lookup(String discId, CdToc toc) throws IOException {
        int audioTracks = toc.audioTracks().size();
        Optional<byte[]> body = request(URI.create(BASE + discId + "?" + INC));
        if (body.isEmpty()) {
            body = request(URI.create(BASE + "-?toc=" + DiscId.tocString(toc) + "&" + INC));
        }
        if (body.isEmpty()) return List.of();
        return parse(new String(body.get(), StandardCharsets.UTF_8), discId, audioTracks);
    }

    private synchronized Optional<byte[]> request(URI uri) throws IOException {
        long wait = lastRequest + 1100 - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }
        try {
            return http.get(uri, "application/json");
        } finally {
            lastRequest = System.currentTimeMillis();
        }
    }

    static List<AlbumInfo> parse(String json, String discId, int audioTracks) {
        Object root = Json.parse(json);
        List<Object> releases = Json.list(root, "releases");
        List<AlbumInfo> result = new ArrayList<>();
        for (Object release : releases) {
            Object medium = medium(release, discId, audioTracks);
            if (medium == null) continue;
            String albumArtist = credit(release);
            List<String> titles = new ArrayList<>();
            List<String> artists = new ArrayList<>();
            for (Object track : Json.list(medium, "tracks")) {
                titles.add(Json.str(track, "title"));
                String artist = credit(track);
                if (artist.isEmpty()) artist = credit(Json.member(track, "recording"));
                artists.add(artist.equals(albumArtist) ? "" : artist);
            }
            while (titles.size() < audioTracks) {
                titles.add("");
                artists.add("");
            }
            String date = Json.str(release, "date");
            String label = "";
            List<Object> labels = Json.list(release, "label-info");
            if (!labels.isEmpty()) label = Json.str(Json.member(labels.get(0), "label"), "name");
            String title = Json.str(release, "title");
            int discs = Json.list(release, "media").size();
            if (discs > 1) title = title + " (CD " + Json.integer(medium, "position") + ")";
            result.add(new AlbumInfo(title, albumArtist, date.length() >= 4 ? date.substring(0, 4) : date, label,
                    titles.subList(0, audioTracks), artists.subList(0, audioTracks), Json.str(release, "id"),
                    Json.str(Json.member(release, "release-group"), "id"), "MusicBrainz"
                            + (Json.str(release, "country").isEmpty() ? "" : " · " + Json.str(release, "country"))));
        }
        return result;
    }

    /** The medium that carries the disc ID, else the first with the right number of tracks. */
    private static Object medium(Object release, String discId, int audioTracks) {
        List<Object> media = Json.list(release, "media");
        for (Object m : media) {
            for (Object d : Json.list(m, "discs")) if (discId.equals(Json.str(d, "id"))) return m;
        }
        for (Object m : media) {
            int count = Json.integer(m, "track-count");
            if (count == 0) count = Json.list(m, "tracks").size();
            if (count == audioTracks) return m;
        }
        return null;
    }

    /** Joined artist credit, e.g. {@code Simon & Garfunkel}. */
    private static String credit(Object entity) {
        StringBuilder sb = new StringBuilder();
        for (Object c : Json.list(entity, "artist-credit")) {
            sb.append(Json.str(c, "name")).append(Json.str(c, "joinphrase"));
        }
        return sb.toString().strip();
    }
}

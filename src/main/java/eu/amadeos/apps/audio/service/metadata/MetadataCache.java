package eu.amadeos.apps.audio.service.metadata;

import eu.amadeos.apps.audio.domain.AlbumInfo;
import eu.amadeos.apps.audio.service.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Album data and covers of known discs in {@code ~/.cache/amadeos/audio/discs/<disc-id>/}
 * ({@code album.json}, {@code cover.jpg}), and station logos in {@code logos/}.
 */
public final class MetadataCache {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataCache.class);
    private final Path root;

    public MetadataCache(Path root) {
        this.root = root;
    }

    public Optional<AlbumInfo> album(String discId) {
        Path file = discDir(discId).resolve("album.json");
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            Object o = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            List<String> titles = strings(Json.list(o, "titles"));
            List<String> artists = strings(Json.list(o, "artists"));
            while (artists.size() < titles.size()) artists.add("");
            return Optional.of(new AlbumInfo(Json.str(o, "album"), Json.str(o, "artist"), Json.str(o, "year"),
                    Json.str(o, "label"), titles, artists, Json.str(o, "releaseId"), Json.str(o, "releaseGroupId"),
                    Json.str(o, "source")));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Ignoring broken cache entry {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    public void storeAlbum(String discId, AlbumInfo info) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("album", info.album());
        m.put("artist", info.artist());
        m.put("year", info.year());
        m.put("label", info.label());
        m.put("titles", info.titles());
        m.put("artists", info.artists());
        m.put("releaseId", info.releaseId());
        m.put("releaseGroupId", info.releaseGroupId());
        m.put("source", info.source());
        write(discDir(discId).resolve("album.json"), Json.write(m).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<byte[]> cover(String discId) {
        return read(discDir(discId).resolve("cover.jpg"));
    }

    public void storeCover(String discId, byte[] image) {
        write(discDir(discId).resolve("cover.jpg"), image);
    }

    /** Station logo by a key derived from its URL. */
    public Optional<byte[]> logo(String key) {
        return read(root.resolve("logos").resolve(key));
    }

    public void storeLogo(String key, byte[] image) {
        write(root.resolve("logos").resolve(key), image);
    }

    private Path discDir(String discId) {
        // Disc IDs use [A-Za-z0-9._-] only; anything else would be a path trick.
        if (!discId.matches("[A-Za-z0-9._-]{1,64}") || discId.startsWith(".")) {
            throw new IllegalArgumentException("Invalid disc ID: " + discId);
        }
        return root.resolve("discs").resolve(discId);
    }

    private static Optional<byte[]> read(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void write(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            Files.write(temp, data);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("Cannot write cache file {}: {}", file, e.toString());
        }
    }

    private static List<String> strings(List<Object> list) {
        List<String> out = new ArrayList<>();
        for (Object o : list) out.add(o == null ? "" : o.toString());
        return out;
    }
}

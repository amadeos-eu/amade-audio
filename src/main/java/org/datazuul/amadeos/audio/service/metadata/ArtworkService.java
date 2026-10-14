package org.datazuul.amadeos.audio.service.metadata;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.AlbumInfo;
import org.datazuul.amadeos.audio.domain.Artwork;
import org.datazuul.amadeos.audio.domain.Station;
import org.datazuul.amadeos.audio.service.player.TagReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Cover art per device: CDs from the Cover Art Archive (cached), files from their tags or a cover image
 * in their folder, stations their logo. Methods may block on the network; call from a background thread.
 */
public final class ArtworkService {
    private static final Logger LOG = LoggerFactory.getLogger(ArtworkService.class);
    static final String COVER_ART_ARCHIVE = "https://coverartarchive.org/";
    private static final List<String> FOLDER_IMAGES = List.of("cover", "folder", "front", "album", "albumart");
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final HttpFetcher http;
    private final MetadataCache cache;
    private final TagReader tags;

    @Inject
    public ArtworkService(HttpFetcher http, MetadataCache cache, TagReader tags) {
        this.http = http;
        this.cache = cache;
        this.tags = tags;
    }

    /** Front cover of a CD: from the cache, or downloaded for the release (or its release group). */
    public Optional<Artwork> forCd(String discId, AlbumInfo album) {
        Optional<byte[]> cached = cache.cover(discId);
        if (cached.isPresent()) return Optional.of(new Artwork(cached.get(), "Cover Art Archive"));
        if (album.releaseId().isEmpty()) return Optional.empty();
        Optional<byte[]> image = download("release/" + album.releaseId() + "/front-500");
        if (image.isEmpty() && !album.releaseGroupId().isEmpty()) {
            image = download("release-group/" + album.releaseGroupId() + "/front-500");
        }
        image.ifPresent(data -> cache.storeCover(discId, data));
        return image.map(data -> new Artwork(data, "Cover Art Archive"));
    }

    /** Cover of a local file: embedded in its tags, else an image like {@code folder.jpg} next to it. */
    public Optional<Artwork> forFile(Path file) {
        Optional<Artwork> embedded = tags.embeddedArtwork(file);
        if (embedded.isPresent()) return embedded;
        Path dir = file.toAbsolutePath().getParent();
        if (dir == null) return Optional.empty();
        try (Stream<Path> list = Files.list(dir)) {
            Optional<Path> image = list.filter(Files::isRegularFile).filter(ArtworkService::isFolderImage)
                    .min((a, b) -> Integer.compare(rank(a), rank(b)));
            if (image.isPresent() && Files.size(image.get()) <= MAX_IMAGE_BYTES) {
                return Optional.of(new Artwork(Files.readAllBytes(image.get()), image.get().getFileName().toString()));
            }
        } catch (IOException e) {
            LOG.debug("Cannot look for cover images in {}: {}", dir, e.toString());
        }
        return Optional.empty();
    }

    /** Logo of a station (its favicon from radio-browser.info), cached. */
    public Optional<Artwork> forStation(Station station) {
        if (station.favicon().isBlank()) return Optional.empty();
        String key = hash(station.favicon());
        Optional<byte[]> cached = cache.logo(key);
        if (cached.isPresent()) return Optional.of(new Artwork(cached.get(), "Station logo"));
        try {
            URI uri = URI.create(station.favicon());
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) return Optional.empty();
            Optional<byte[]> data = http.get(uri, "image/*").filter(d -> d.length > 0 && d.length <= MAX_IMAGE_BYTES);
            data.ifPresent(d -> cache.storeLogo(key, d));
            return data.map(d -> new Artwork(d, "Station logo"));
        } catch (IOException | IllegalArgumentException e) {
            LOG.debug("No logo for {}: {}", station.name(), e.toString());
            return Optional.empty();
        }
    }

    private Optional<byte[]> download(String path) {
        try {
            return http.get(URI.create(COVER_ART_ARCHIVE + path), "image/*");
        } catch (IOException e) {
            LOG.info("Cover Art Archive: {}", e.toString());
            return Optional.empty();
        }
    }

    static boolean isFolderImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) return false;
        return rank(file) < FOLDER_IMAGES.size();
    }

    private static int rank(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        for (int i = 0; i < FOLDER_IMAGES.size(); i++) {
            if (stem.equals(FOLDER_IMAGES.get(i)) || stem.startsWith(FOLDER_IMAGES.get(i) + "_")
                    || stem.startsWith(FOLDER_IMAGES.get(i) + " ")) return i;
        }
        return FOLDER_IMAGES.size();
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}

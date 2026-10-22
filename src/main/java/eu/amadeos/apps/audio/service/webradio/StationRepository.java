package eu.amadeos.apps.audio.service.webradio;

import com.google.inject.Inject;
import eu.amadeos.apps.audio.domain.Station;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Favourite stations in {@code ~/.config/amadeos/audio/stations.json}. On first use the favourites of
 * dz-radio ({@code ~/.dz-apps/dz-radio/channels.json}) are taken over if present.
 */
public final class StationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(StationRepository.class);
    static final String FILE = "stations.json";

    private final Path file;
    private final Path dzRadioFile;
    private List<Station> favorites;

    @Inject
    public StationRepository(AppDirectories directories) {
        this(directories.configFile(FILE), Path.of(System.getProperty("user.home"), ".dz-apps", "dz-radio", "channels.json"));
    }

    StationRepository(Path file, Path dzRadioFile) {
        this.file = file;
        this.dzRadioFile = dzRadioFile;
    }

    public synchronized List<Station> favorites() {
        if (favorites == null) favorites = load();
        return List.copyOf(favorites);
    }

    /** Adds the station unless it is already a favourite; returns false if it was. */
    public synchronized boolean add(Station station) {
        List<Station> list = new ArrayList<>(favorites());
        if (list.stream().anyMatch(s -> s.sameAs(station))) return false;
        list.add(station);
        save(list);
        return true;
    }

    public synchronized void remove(Station station) {
        List<Station> list = new ArrayList<>(favorites());
        if (list.removeIf(s -> s.sameAs(station))) save(list);
    }

    /** Moves a favourite up (-1) or down (+1). */
    public synchronized void move(Station station, int delta) {
        List<Station> list = new ArrayList<>(favorites());
        int i = -1;
        for (int k = 0; k < list.size(); k++) if (list.get(k).sameAs(station)) i = k;
        int j = i + delta;
        if (i < 0 || j < 0 || j >= list.size()) return;
        list.add(j, list.remove(i));
        save(list);
    }

    public synchronized boolean isFavorite(Station station) {
        return favorites().stream().anyMatch(s -> s.sameAs(station));
    }

    private List<Station> load() {
        try {
            if (Files.isRegularFile(file)) return parse(Files.readString(file, StandardCharsets.UTF_8));
            if (Files.isRegularFile(dzRadioFile)) {
                List<Station> imported = parse(Files.readString(dzRadioFile, StandardCharsets.UTF_8));
                LOG.info("Took over {} favourite stations from dz-radio", imported.size());
                save(imported);
                return imported;
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot read favourite stations from {}: {}", file, e.toString());
            return new ArrayList<>();
        }
        List<Station> defaults = List.of(
                new Station("9615a133-0601-11e8-ae97-52543be0d452", "Radio Paradise (Mellow Mix)",
                        "http://stream.radioparadise.com/mellow-128", "US", "eclectic,rock", "MP3", 128, ""),
                new Station("961c0282-0601-11e8-ae97-52543be0d452", "Antenne Bayern",
                        "http://stream.antenne.de/antenne", "DE", "pop", "MP3", 128, ""));
        return new ArrayList<>(defaults);
    }

    /** Reads our own format and dz-radio's (which uses {@code stationuuid}). */
    static List<Station> parse(String json) {
        List<Station> result = new ArrayList<>();
        for (Object o : Json.parseArray(json)) {
            String url = Json.str(o, "url");
            if (url.isBlank()) continue;
            String uuid = Json.str(o, "uuid");
            if (uuid.isBlank()) uuid = Json.str(o, "stationuuid");
            result.add(new Station(uuid, Json.str(o, "name"), url, Json.str(o, "country"), Json.str(o, "tags"),
                    Json.str(o, "codec"), Json.integer(o, "bitrate"), Json.str(o, "favicon")));
        }
        return result;
    }

    static String format(List<Station> stations) {
        List<Object> list = new ArrayList<>();
        for (Station s : stations) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", s.uuid());
            m.put("name", s.name());
            m.put("url", s.url());
            m.put("country", s.country());
            m.put("tags", s.tags());
            m.put("codec", s.codec());
            m.put("bitrate", s.bitrate());
            m.put("favicon", s.favicon());
            list.add(m);
        }
        return Json.write(list);
    }

    private void save(List<Station> stations) {
        favorites = new ArrayList<>(stations);
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), FILE, ".tmp");
            Files.writeString(temp, format(stations), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save favourite stations to " + file, e);
        }
    }
}

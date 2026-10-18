package eu.amadeos.apps.audio.service.webradio;

import eu.amadeos.apps.audio.domain.Station;
import eu.amadeos.apps.audio.service.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Station directory of <a href="https://www.radio-browser.info">radio-browser.info</a>. Switches to
 * another mirror when a server fails, as dz-radio does; the mirror list comes from
 * {@code all.api.radio-browser.info/json/servers}. Methods block; call them from a background thread.
 */
public final class RadioBrowserClient {
    private static final Logger LOG = LoggerFactory.getLogger(RadioBrowserClient.class);
    private static final List<String> FALLBACK_SERVERS = List.of(
            "https://de1.api.radio-browser.info", "https://de2.api.radio-browser.info",
            "https://fi1.api.radio-browser.info", "https://nl1.api.radio-browser.info");

    private final HttpClient http = eu.amadeos.apps.audio.service.net.NetworkProxy.INSTANCE.httpClient()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final List<String> servers = new CopyOnWriteArrayList<>(FALLBACK_SERVERS);
    private volatile boolean discovered;

    /** Stations whose name, tag and country (ISO code) match; blank criteria are ignored. */
    public List<Station> search(String name, String tag, String countryCode, int limit) throws IOException {
        StringBuilder q = new StringBuilder("/json/stations/search?order=clickcount&reverse=true&hidebroken=true&limit=")
                .append(limit);
        if (name != null && !name.isBlank()) q.append("&name=").append(enc(name.strip()));
        if (tag != null && !tag.isBlank()) q.append("&tag=").append(enc(tag.strip()));
        if (countryCode != null && !countryCode.isBlank()) q.append("&countrycode=").append(enc(countryCode.strip()));
        return stations(get(q.toString()));
    }

    public List<Station> byTag(String tag, int limit) throws IOException {
        List<Station> exact = stations(get("/json/stations/bytagexact/" + enc(tag.strip().toLowerCase())
                + "?order=clickcount&reverse=true&hidebroken=true&limit=" + limit));
        if (!exact.isEmpty()) return exact;
        return stations(get("/json/stations/bytag/" + enc(tag.strip()) + "?order=clickcount&reverse=true&hidebroken=true&limit=" + limit));
    }

    public List<Station> byCountry(String countryCode, int limit) throws IOException {
        return stations(get("/json/stations/bycountrycodeexact/" + enc(countryCode.strip().toUpperCase())
                + "?order=clickcount&reverse=true&hidebroken=true&limit=" + limit));
    }

    /** Most used tags with their station counts. */
    public Map<String, Integer> topTags(int limit) throws IOException {
        return counts(get("/json/tags?order=stationcount&reverse=true&hidebroken=true&limit=" + limit));
    }

    /** ISO country codes with their station counts, most stations first. */
    public Map<String, Integer> countries() throws IOException {
        return counts(get("/json/countrycodes?order=stationcount&reverse=true&hidebroken=true"));
    }

    /** Tells the directory a station was played (helps its popularity ranking); errors are ignored. */
    public void click(Station station) {
        if (station.uuid().isEmpty()) return;
        try {
            get("/json/url/" + enc(station.uuid()));
        } catch (IOException e) {
            LOG.debug("Click report failed: {}", e.toString());
        }
    }

    static List<Station> stations(String json) {
        List<Station> result = new ArrayList<>();
        for (Object o : Json.parseArray(json)) {
            String url = Json.str(o, "url_resolved");
            if (url.isBlank()) url = Json.str(o, "url");
            if (url.isBlank()) continue;
            result.add(new Station(Json.str(o, "stationuuid"), Json.str(o, "name").strip(), url.strip(),
                    Json.str(o, "countrycode"), Json.str(o, "tags"), Json.str(o, "codec"), Json.integer(o, "bitrate"),
                    Json.str(o, "favicon")));
        }
        return result;
    }

    static Map<String, Integer> counts(String json) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object o : Json.parseArray(json)) {
            String name = Json.str(o, "name").strip();
            if (!name.isEmpty()) result.put(name, Json.integer(o, "stationcount"));
        }
        return result;
    }

    private String get(String pathAndQuery) throws IOException {
        discover();
        IOException last = null;
        for (String server : List.copyOf(servers)) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(server + pathAndQuery))
                        .header("User-Agent", RadioSession.USER_AGENT)
                        .timeout(Duration.ofSeconds(8))
                        .GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    if (!servers.get(0).equals(server)) {
                        servers.remove(server);
                        servers.add(0, server);
                    }
                    return response.body();
                }
                last = new IOException("HTTP " + response.statusCode() + " from " + server);
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
            LOG.info("radio-browser server {} failed, trying the next one", server);
        }
        throw last != null ? last : new IOException("No radio-browser server reachable");
    }

    /** Fetches the current mirror list once. */
    private void discover() {
        if (discovered) return;
        discovered = true;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://all.api.radio-browser.info/json/servers"))
                    .header("User-Agent", RadioSession.USER_AGENT).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;
            List<String> found = new ArrayList<>();
            for (Object o : Json.parseArray(response.body())) {
                String name = Json.str(o, "name");
                if (!name.isBlank() && !found.contains("https://" + name)) found.add("https://" + name);
            }
            if (!found.isEmpty()) {
                java.util.Collections.shuffle(found);
                servers.clear();
                servers.addAll(found);
            }
        } catch (IOException | RuntimeException e) {
            LOG.debug("radio-browser server discovery failed: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

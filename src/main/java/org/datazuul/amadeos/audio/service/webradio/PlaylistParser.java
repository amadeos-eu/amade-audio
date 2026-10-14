package org.datazuul.amadeos.audio.service.webradio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds the stream URLs in .pls and .m3u playlists of radio stations. Ported from dz-radio.
 */
public final class PlaylistParser {
    static final int MAX_LINES = 200;

    private PlaylistParser() {
    }

    /** True if the URL looks like a playlist rather than a stream. */
    public static boolean isPlaylistUrl(String url) {
        String path = URI.create(url).getPath();
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pls") || lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.endsWith(".asx");
    }

    /** Downloads the playlist and returns its stream URLs, the first being the preferred one. */
    public static List<String> fetch(String playlistUrl, String userAgent) throws IOException {
        URL url = URI.create(playlistUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("User-Agent", userAgent);
        try (InputStream in = connection.getInputStream()) {
            byte[] body = in.readNBytes(256 * 1024);
            return parse(new String(body, StandardCharsets.UTF_8), connection.getContentType(), url.getPath());
        } finally {
            connection.disconnect();
        }
    }

    /** Extracts http(s) URLs from a PLS ({@code FileN=...}) or M3U (one URL per line) playlist. */
    public static List<String> parse(String body, String contentType, String path) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        boolean pls = type.contains("scpls") || lowerPath.endsWith(".pls") || body.contains("[playlist]");
        List<String> urls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(body))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count++ < MAX_LINES) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String candidate = line;
                if (pls) {
                    if (!line.toLowerCase(Locale.ROOT).startsWith("file")) continue;
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    candidate = line.substring(eq + 1).strip();
                }
                String lower = candidate.toLowerCase(Locale.ROOT);
                if ((lower.startsWith("http://") || lower.startsWith("https://")) && !urls.contains(candidate)) {
                    urls.add(candidate);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // cannot happen with a StringReader
        }
        return urls;
    }

    /** Used by tests with an in-memory body. */
    static List<String> parse(InputStream body, String contentType, String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return parse(sb.toString(), contentType, path);
    }
}

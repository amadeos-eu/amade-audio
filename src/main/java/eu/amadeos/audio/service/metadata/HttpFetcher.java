package eu.amadeos.audio.service.metadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** HTTP GET for the metadata services; replaced by a fake in tests. */
public interface HttpFetcher {
    String USER_AGENT = "amade-audio/0.1.0 ( https://github.com/datazuul/amade-audio )";

    /** The body, or empty for 404; other errors throw. */
    Optional<byte[]> get(URI uri, String accept) throws IOException;

    static HttpFetcher system() {
        HttpClient client = eu.amadeos.audio.service.net.NetworkProxy.INSTANCE.httpClient()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return (uri, accept) -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", accept)
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            try {
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int code = response.statusCode();
                if (code == 404) return Optional.empty();
                if (code != 200) throw new IOException("HTTP " + code + " from " + uri.getHost());
                return Optional.of(response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        };
    }
}

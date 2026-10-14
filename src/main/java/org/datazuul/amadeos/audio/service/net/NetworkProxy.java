package org.datazuul.amadeos.audio.service.net;

import static org.datazuul.amadeos.audio.i18n.Texts.tr;

import org.datazuul.amadeos.audio.domain.ProxySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The proxy for every connection of amade-audio. Installed as the JVM's default {@link ProxySelector}
 * and {@link Authenticator}, so radio streams ({@code HttpURLConnection}) use it, and handed to every
 * {@link HttpClient} (which ignores the default otherwise). Settings can change while the application runs.
 */
public final class NetworkProxy extends ProxySelector {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkProxy.class);
    /** The instance the application uses. */
    public static final NetworkProxy INSTANCE = new NetworkProxy(System.getenv());

    /** A proxy from the settings or the environment. */
    public record Endpoint(String host, int port, String user, String password) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private final Map<String, String> environment;
    private final Authenticator authenticator = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() != RequestorType.PROXY) return null;
            Optional<Endpoint> e = endpoint(getRequestingURL() != null && "https".equals(getRequestingURL().getProtocol()));
            if (e.isEmpty() || e.get().user().isEmpty()) return null;
            return new PasswordAuthentication(e.get().user(), e.get().password().toCharArray());
        }
    };
    private volatile ProxySettings settings = ProxySettings.DEFAULT;
    private volatile String password = "";

    public NetworkProxy(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    /**
     * Makes this the JVM default and allows Basic authentication at proxies, which the JDK switches off
     * for HTTPS tunnels by default. Call once at start-up, before the first connection.
     */
    public void install() {
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
        ProxySelector.setDefault(this);
        Authenticator.setDefault(authenticator);
    }

    public void configure(ProxySettings newSettings, String newPassword) {
        settings = newSettings == null ? ProxySettings.DEFAULT : newSettings;
        password = newPassword == null ? "" : newPassword;
        LOG.info("Network proxy: {}", describe());
    }

    public ProxySettings settings() {
        return settings;
    }

    public Authenticator authenticator() {
        return authenticator;
    }

    /** An HTTP client that uses this proxy. */
    public HttpClient.Builder httpClient() {
        return HttpClient.newBuilder().proxy(this).authenticator(authenticator);
    }

    /** E.g. {@code proxy.example:3128 (from https_proxy)} or {@code direct connection}. */
    public String describe() {
        Optional<Endpoint> e = endpoint(true);
        if (e.isEmpty()) return tr("proxy.direct");
        return settings.mode() == ProxySettings.Mode.SYSTEM ? tr("proxy.from-environment", e.get().toString())
                : e.get().toString();
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (uri == null || uri.getHost() == null) return List.of(Proxy.NO_PROXY);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.startsWith("http") && !scheme.equals("socket")) return List.of(Proxy.NO_PROXY);
        if (bypass(uri.getHost())) return List.of(Proxy.NO_PROXY);
        Optional<Endpoint> e = endpoint(scheme.equals("https") || scheme.equals("socket"));
        return e.<List<Proxy>>map(p -> List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(p.host(), p.port()))))
                .orElse(List.of(Proxy.NO_PROXY));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException e) {
        LOG.warn("Cannot connect to the proxy {} for {}: {}", address, uri.getHost(), e.toString());
    }

    /** The proxy for HTTPS (or plain HTTP) connections, if any. */
    public Optional<Endpoint> endpoint(boolean https) {
        ProxySettings s = settings;
        return switch (s.mode()) {
            case NONE -> Optional.empty();
            case MANUAL -> s.host().isEmpty() ? Optional.empty() : Optional.of(new Endpoint(s.host(), s.port(), s.user(), password));
            case SYSTEM -> fromEnvironment(https);
        };
    }

    /** True if the host is reached directly: loopback, or listed in the no-proxy list. */
    boolean bypass(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.startsWith("127.") || h.equals("::1") || h.equals("[::1]")) return true;
        List<String> rules = settings.mode() == ProxySettings.Mode.SYSTEM
                ? split(env("no_proxy")) : settings.noProxyHosts();
        for (String rule : rules) {
            String r = rule.toLowerCase(Locale.ROOT);
            if (r.equals("*")) return true;
            if (r.startsWith("*.")) r = r.substring(1);
            if (r.startsWith(".") ? h.endsWith(r) || h.equals(r.substring(1)) : h.equals(r) || h.endsWith("." + r)) return true;
        }
        return false;
    }

    private Optional<Endpoint> fromEnvironment(boolean https) {
        String value = https ? env("https_proxy") : env("http_proxy");
        if (value.isEmpty()) value = env("all_proxy");
        if (value.isEmpty() && https) value = env("http_proxy");
        return parse(value);
    }

    /** {@code http://user:pass@host:port/}, {@code host:port} or {@code host}. */
    static Optional<Endpoint> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String v = value.strip();
        if (!v.contains("://")) v = "http://" + v;
        try {
            URI uri = URI.create(v);
            if (uri.getHost() == null) return Optional.empty();
            String user = "";
            String pass = "";
            if (uri.getRawUserInfo() != null) {
                String[] up = uri.getRawUserInfo().split(":", 2);
                user = URLDecoder.decode(up[0], StandardCharsets.UTF_8);
                pass = up.length > 1 ? URLDecoder.decode(up[1], StandardCharsets.UTF_8) : "";
            }
            int port = uri.getPort() > 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
            return Optional.of(new Endpoint(uri.getHost(), port, user, pass));
        } catch (IllegalArgumentException e) {
            LOG.warn("Ignoring invalid proxy setting {}", value);
            return Optional.empty();
        }
    }

    private String env(String name) {
        String v = environment.get(name);
        if (v == null || v.isBlank()) v = environment.get(name.toUpperCase(Locale.ROOT));
        return v == null ? "" : v.strip();
    }

    private static List<String> split(String list) {
        return java.util.Arrays.stream(list.split("[,\\s]+")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Tries the given settings (not yet saved) by asking radio-browser.info for its servers; returns a
     * message for the user. Blocks; call from a background thread.
     */
    public String test(ProxySettings candidate, String candidatePassword) {
        NetworkProxy probe = new NetworkProxy(environment);
        probe.configure(candidate, candidatePassword);
        HttpClient client = probe.httpClient().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://all.api.radio-browser.info/json/servers"))
                .header("User-Agent", "amade-audio/0.1").timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 407) return tr("proxy.test.credentials-needed");
            if (response.statusCode() >= 400) return tr("proxy.test.http-error", String.valueOf(response.statusCode()));
            return tr("proxy.test.ok", probe.describe());
        } catch (IOException e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            if (m.contains("407")) return tr("proxy.test.credentials-refused");
            return tr("proxy.test.failed", probe.describe(), m);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return tr("proxy.test.interrupted");
        }
    }
}

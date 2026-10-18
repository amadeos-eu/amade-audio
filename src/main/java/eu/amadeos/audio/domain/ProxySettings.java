package eu.amadeos.audio.domain;

import java.util.Arrays;
import java.util.List;

/**
 * How amade-audio reaches the internet (radio streams, radio-browser.info, MusicBrainz, cover art).
 * The proxy password is not part of the settings; it lives in the keyring.
 *
 * @param host    manual proxy host, used for HTTP and HTTPS
 * @param noProxy comma-separated hosts or domains reached directly, e.g. {@code localhost, .intranet.example}
 */
public record ProxySettings(Mode mode, String host, int port, String user, String noProxy) {
    public enum Mode {
        /** From the environment ({@code http_proxy}, {@code https_proxy}, {@code no_proxy}). */
        SYSTEM,
        /** Always connect directly. */
        NONE,
        /** The proxy entered in the settings. */
        MANUAL
    }

    public static final ProxySettings DEFAULT = new ProxySettings(Mode.SYSTEM, "", 3128, "", "localhost, 127.0.0.1");

    public ProxySettings {
        mode = mode == null ? Mode.SYSTEM : mode;
        host = host == null ? "" : host.strip();
        port = port <= 0 || port > 65535 ? 3128 : port;
        user = user == null ? "" : user.strip();
        noProxy = noProxy == null ? "" : noProxy.strip();
    }

    public List<String> noProxyHosts() {
        return Arrays.stream(noProxy.split("[,\\s]+")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}

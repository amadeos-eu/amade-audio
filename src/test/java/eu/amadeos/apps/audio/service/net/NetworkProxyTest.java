package eu.amadeos.apps.audio.service.net;

import eu.amadeos.apps.audio.domain.ProxySettings;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NetworkProxyTest {
    private static final URI STREAM = URI.create("http://stream.example.org/live.mp3");
    private static final URI API = URI.create("https://de1.api.radio-browser.info/json/stations");

    private static String target(List<Proxy> proxies) {
        Proxy p = proxies.get(0);
        if (p.type() == Proxy.Type.DIRECT) return "DIRECT";
        InetSocketAddress a = (InetSocketAddress) p.address();
        return a.getHostString() + ":" + a.getPort();
    }

    @Test
    void systemModeUsesTheEnvironment() {
        NetworkProxy proxy = new NetworkProxy(Map.of(
                "http_proxy", "http://plain.example:8080",
                "HTTPS_PROXY", "http://user:p%40ss@secure.example:3128/",
                "no_proxy", "localhost,.intranet.example, music.lan"));
        proxy.configure(ProxySettings.DEFAULT, "");
        assertEquals("plain.example:8080", target(proxy.select(STREAM)));
        assertEquals("secure.example:3128", target(proxy.select(API)));
        assertEquals("DIRECT", target(proxy.select(URI.create("http://radio.intranet.example/x"))));
        assertEquals("DIRECT", target(proxy.select(URI.create("http://intranet.example/x"))));
        assertEquals("DIRECT", target(proxy.select(URI.create("http://nas.music.lan/x"))));
        assertEquals("DIRECT", target(proxy.select(URI.create("http://127.0.0.1:8000/x"))));
        NetworkProxy.Endpoint e = proxy.endpoint(true).orElseThrow();
        assertEquals("user", e.user());
        assertEquals("p@ss", e.password());
        assertTrue(proxy.describe().contains("secure.example:3128"));
    }

    @Test
    void httpsFallsBackToHttpProxy() {
        NetworkProxy proxy = new NetworkProxy(Map.of("HTTP_PROXY", "proxy.corp:3128"));
        proxy.configure(ProxySettings.DEFAULT, "");
        assertEquals("proxy.corp:3128", target(proxy.select(API)));
        NetworkProxy none = new NetworkProxy(Map.of());
        none.configure(ProxySettings.DEFAULT, "");
        assertEquals("DIRECT", target(none.select(API)));
        assertEquals("direct connection", none.describe());
    }

    @Test
    void manualAndNoneModes() {
        NetworkProxy proxy = new NetworkProxy(Map.of("https_proxy", "http://env.example:1"));
        proxy.configure(new ProxySettings(ProxySettings.Mode.MANUAL, "proxy.kvb.example", 8080, "ralf", "*.local, nas"), "secret");
        assertEquals("proxy.kvb.example:8080", target(proxy.select(STREAM)));
        assertEquals("proxy.kvb.example:8080", target(proxy.select(API)));
        assertEquals("DIRECT", target(proxy.select(URI.create("http://box.local/stream"))));
        assertEquals("DIRECT", target(proxy.select(URI.create("http://nas/stream"))));
        assertEquals("secret", proxy.endpoint(false).orElseThrow().password());

        proxy.configure(new ProxySettings(ProxySettings.Mode.NONE, "x", 1, "", ""), "");
        assertEquals("DIRECT", target(proxy.select(API)));

        proxy.configure(new ProxySettings(ProxySettings.Mode.MANUAL, "", 8080, "", ""), "");
        assertEquals("DIRECT", target(proxy.select(API)), "manual without a host means direct");
    }

    @Test
    void parsesProxyValues() {
        assertEquals("h:80", NetworkProxy.parse("h").orElseThrow().toString());
        assertEquals("h:3128", NetworkProxy.parse(" http://h:3128 ").orElseThrow().toString());
        assertTrue(NetworkProxy.parse("").isEmpty());
        assertTrue(NetworkProxy.parse("http://").isEmpty());
    }
}

package eu.amadeos.apps.audio.service.webradio;

import eu.amadeos.apps.audio.service.decode.DecoderFactory.StreamCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads a radio stream on a virtual thread: follows redirects, removes ICY metadata, detects the
 * codec, fills the {@link BufferedRadioInputStream} and hands every audio chunk to the recording tap.
 * Reconnects up to five times when the connection drops. Ported from dz-radio's playback thread.
 */
public final class RadioSession implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(RadioSession.class);
    static final String USER_AGENT = "amade-audio/0.1 (amadeOS; +https://github.com/amadeos-eu/amade-audio)";
    private static final int MAX_RETRIES = 5;

    /** Events from the downloader thread. */
    public interface Events {
        void title(String streamTitle);

        /** Connection problems and reconnects, e.g. {@code Reconnecting (2/5)...}. */
        void message(String text);

        /** Gave up; the decoder will see the end of the stream. */
        void failed(String reason);
    }

    /** Receives the raw audio bytes for recording. */
    public interface Tap {
        void write(byte[] data, int offset, int length);
    }

    private final String url;
    private final String codecHint;
    private final Events events;
    private final BufferedRadioInputStream buffer = new BufferedRadioInputStream(16);
    private final CompletableFuture<StreamCodec> codec = new CompletableFuture<>();
    private volatile Tap tap;
    private volatile boolean running = true;
    private volatile HttpURLConnection connection;
    private volatile String contentType = "";
    private volatile int bitrate;
    private volatile String lastTitle;

    public RadioSession(String url, String codecHint, Events events) {
        this.url = url;
        this.codecHint = codecHint == null ? "" : codecHint;
        this.events = events;
    }

    public void start() {
        Thread.ofVirtual().name("amade-radio-download").start(this::download);
    }

    /** Completes when the first bytes were inspected. */
    public CompletableFuture<StreamCodec> codec() {
        return codec;
    }

    public InputStream audio() {
        return buffer;
    }

    public double bufferFill() {
        return buffer.fillLevel();
    }

    public String contentType() {
        return contentType;
    }

    /** kbit/s from the {@code icy-br} header, 0 if unknown. */
    public int bitrate() {
        return bitrate;
    }

    public String lastTitle() {
        return lastTitle;
    }

    public void setTap(Tap value) {
        tap = value;
    }

    @Override
    public void close() {
        running = false;
        buffer.close();
        HttpURLConnection c = connection;
        if (c != null) c.disconnect();
        codec.completeExceptionally(new IOException("Stopped"));
    }

    private void download() {
        int retries = 0;
        while (running && retries <= MAX_RETRIES) {
            HttpURLConnection conn = null;
            try {
                conn = connect();
                connection = conn;
                contentType = conn.getContentType() == null ? "" : conn.getContentType();
                bitrate = parseInt(conn.getHeaderField("icy-br"));
                int metaint = parseInt(conn.getHeaderField("icy-metaint"));
                try (InputStream in = new IcyInputStream(new BufferedInputStream(conn.getInputStream()), metaint,
                        this::title)) {
                    byte[] head = in.readNBytes(StreamSniffer.SNIFF_BYTES);
                    if (head.length == 0) throw new IOException("The station sent no data");
                    StreamSniffer.Detection detection = StreamSniffer.detect(head, head.length, contentType + " " + codecHint);
                    if (!codec.isDone()) LOG.info("Stream {}: {} ({})", url, detection.codec(), contentType);
                    codec.complete(detection.codec());
                    if (!deliver(head, detection.start(), head.length - detection.start())) break;
                    retries = 0;
                    byte[] chunk = new byte[4096];
                    int n;
                    while (running && (n = in.read(chunk)) >= 0) {
                        if (n > 0 && !deliver(chunk, 0, n)) break;
                    }
                }
                if (!running) break;
                throw new IOException("The station closed the connection");
            } catch (IOException | RuntimeException e) {
                if (!running) break;
                retries++;
                LOG.warn("Radio stream {} (attempt {}/{}): {}", url, retries, MAX_RETRIES, e.toString());
                if (retries > MAX_RETRIES || !codec.isDone() && retries > 2) {
                    String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                    events.failed(reason);
                    codec.completeExceptionally(e);
                    break;
                }
                events.message("Reconnecting (" + retries + "/" + MAX_RETRIES + ")...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        buffer.markFinished();
    }

    // --8<-- [start:deliver]
    private boolean deliver(byte[] data, int offset, int length) {
        if (length <= 0) return true;
        Tap t = tap;
        if (t != null) t.write(data, offset, length);
        byte[] copy = new byte[length];
        System.arraycopy(data, offset, copy, 0, length);
        // The decoder may fall behind for a while (e.g. paused): keep waiting rather than dropping the stream.
        while (running && !buffer.isClosed()) {
            if (buffer.push(copy)) return true;
        }
        return false;
    }
    // --8<-- [end:deliver]

    private void title(String title) {
        lastTitle = title;
        events.title(title);
    }

    private HttpURLConnection connect() throws IOException {
        URL target = URI.create(url).toURL();
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection conn = (HttpURLConnection) target.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Icy-MetaData", "1");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(false);
            connection = conn;
            if (!running) throw new IOException("Stopped");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400 && conn.getHeaderField("Location") != null) {
                target = URI.create(target.toString()).resolve(conn.getHeaderField("Location")).toURL();
                conn.disconnect();
                continue;
            }
            if (code >= 400) throw new IOException("HTTP " + code + " from " + target.getHost());
            return conn;
        }
        throw new IOException("Too many redirects");
    }

    private static int parseInt(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.split(",")[0].strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

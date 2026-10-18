package eu.amadeos.audio.service.webradio;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Removes the Shoutcast/Icecast (ICY) metadata blocks from a stream and reports the stream title.
 * Ported from dz-radio.
 */
public final class IcyInputStream extends FilterInputStream {
    private final int metaint;
    private final Consumer<String> titleListener;
    private int bytesUntilMetadata;
    private String lastTitle;

    /**
     * @param metaint value of the {@code icy-metaint} header; 0 or less if the stream has no metadata
     */
    public IcyInputStream(InputStream in, int metaint, Consumer<String> titleListener) {
        super(in);
        this.metaint = metaint;
        this.titleListener = titleListener;
        this.bytesUntilMetadata = metaint;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (metaint <= 0) return super.read(b, off, len);
        if (bytesUntilMetadata == 0) {
            readMetadata();
            bytesUntilMetadata = metaint;
        }
        int read = super.read(b, off, Math.min(len, bytesUntilMetadata));
        if (read > 0) bytesUntilMetadata -= read;
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        byte[] scratch = new byte[(int) Math.min(n, 8192)];
        long skipped = 0;
        while (skipped < n) {
            int r = read(scratch, 0, (int) Math.min(scratch.length, n - skipped));
            if (r < 0) break;
            skipped += r;
        }
        return skipped;
    }

    private void readMetadata() throws IOException {
        int lengthByte = in.read();
        if (lengthByte <= 0) return;
        int length = lengthByte * 16;
        byte[] meta = new byte[length];
        int read = 0;
        while (read < length) {
            int r = in.read(meta, read, length - read);
            if (r < 0) break;
            read += r;
        }
        String title = streamTitle(new String(meta, 0, read, StandardCharsets.UTF_8));
        if (title != null && !title.equals(lastTitle)) {
            lastTitle = title;
            if (titleListener != null) titleListener.accept(title);
        }
    }

    /** {@code StreamTitle='Artist - Title';StreamUrl='';} gives {@code Artist - Title}; null if none. */
    static String streamTitle(String metadata) {
        String key = "StreamTitle='";
        int start = metadata.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = metadata.indexOf("';", start);
        if (end < 0) end = metadata.lastIndexOf('\'');
        if (end < start) return null;
        String title = metadata.substring(start, end).strip();
        return title.isEmpty() ? null : title;
    }
}

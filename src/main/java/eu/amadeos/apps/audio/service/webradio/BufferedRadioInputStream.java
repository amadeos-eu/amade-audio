package eu.amadeos.apps.audio.service.webradio;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Cushions a network stream: the downloader pushes chunks, the decoder reads them after an initial
 * pre-buffer. Ported from dz-radio.
 */
public final class BufferedRadioInputStream extends InputStream {
    /** 128 chunks of about 4 KB: some 30 s of a 128 kbit/s stream. */
    static final int CAPACITY = 128;
    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(CAPACITY);
    private final int prebufferChunks;
    private byte[] current;
    private int index;
    private volatile boolean finished;
    private volatile boolean closed;
    private volatile boolean prebuffering = true;

    public BufferedRadioInputStream(int prebufferChunks) {
        this.prebufferChunks = prebufferChunks;
    }

    /** Adds a chunk; waits up to 5 s while the buffer is full. False if closed or still full. */
    public boolean push(byte[] chunk) {
        if (closed) return false;
        try {
            return queue.offer(chunk, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** No more chunks will come: the reader gets the end of the stream once the buffer is empty. */
    public void markFinished() {
        finished = true;
    }

    /** Fill level 0..1. */
    public double fillLevel() {
        return (double) queue.size() / CAPACITY;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isPrebuffering() {
        return prebuffering;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (len == 0) return 0;
        try {
            if (prebuffering) {
                while (queue.size() < prebufferChunks && !finished && !closed) Thread.sleep(50);
                prebuffering = false;
            }
            while (current == null || index >= current.length) {
                current = null;
                index = 0;
                if (closed) return -1;
                if (finished && queue.isEmpty()) return -1;
                current = queue.poll(100, TimeUnit.MILLISECONDS);
            }
            int n = Math.min(len, current.length - index);
            System.arraycopy(current, index, b, off, n);
            index += n;
            return n;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while buffering", e);
        }
    }

    @Override
    public int available() {
        int n = current == null ? 0 : current.length - index;
        return n > 0 ? n : (queue.isEmpty() ? 0 : 1);
    }

    @Override
    public void close() {
        closed = true;
        queue.clear();
    }
}

package eu.amadeos.apps.audio.service.cd;

import eu.amadeos.apps.audio.domain.CdTrack;
import eu.amadeos.apps.audio.service.engine.PcmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Audio from a range of CD sectors: 44.1 kHz, 16-bit, stereo, little-endian as on the disc.
 *
 * <p>A reader thread of its own reads ahead into a queue of a few seconds, so a slow read (a drive spinning up,
 * a retried sector, a drive passed through to a virtual machine) does not stop the music. Only that thread
 * touches the {@link SectorReader}: {@link #close()} asks it to stop and it closes the reader after its current
 * read, so native code is never freed while it is still reading.
 */
final class CdPcmSource implements PcmSource {
    private static final Logger LOG = LoggerFactory.getLogger(CdPcmSource.class);
    static final AudioFormat FORMAT = PcmSource.pcm16(44100f, 2);
    static final int FRAMES_PER_SECTOR = CdTrack.BYTES_PER_SECTOR / 4;
    /** Sectors per drive read: about a third of a second. */
    static final int CHUNK = 26;
    /** Chunks read ahead: about five seconds. */
    static final int AHEAD = 16;
    /** Chunks read before the first sample is handed out: about one second. */
    static final int PREROLL = 3;

    /** Reads consecutive sectors; {@code lba} includes the 150-sector lead-in. */
    interface SectorReader extends Closeable {
        void read(int lba, int sectors, byte[] destination) throws IOException;
    }

    /** One chunk from the reader thread; {@code error} or {@code end} mark the last one. */
    private record Chunk(byte[] data, int length, IOException error, boolean end) {
    }

    private final SectorReader reader;
    private final int startLba;
    private final int endLba;
    private final BlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(AHEAD);
    private final Thread thread;
    private volatile boolean closed;
    private boolean started;
    private Chunk chunk;
    private int chunkPos;

    CdPcmSource(SectorReader reader, int startLba, int endLba) {
        this.reader = reader;
        this.startLba = startLba;
        this.endLba = endLba;
        // A platform thread: the native reads would pin a virtual one anyway.
        this.thread = Thread.ofPlatform().daemon().name("amade-audio-cd-reader").start(this::readAhead);
    }

    @Override
    public AudioFormat format() {
        return FORMAT;
    }

    // --8<-- [start:read-ahead]
    private void readAhead() {
        try {
            int lba = startLba;
            while (!closed && lba < endLba) {
                int sectors = Math.min(CHUNK, endLba - lba);
                byte[] data = new byte[sectors * CdTrack.BYTES_PER_SECTOR];
                try {
                    reader.read(lba, sectors, data);
                } catch (IOException e) {
                    offer(new Chunk(null, 0, e, true));
                    return;
                }
                lba += sectors;
                offer(new Chunk(data, data.length, null, false));
            }
            offer(new Chunk(null, 0, null, true));
        } catch (RuntimeException e) {
            offer(new Chunk(null, 0, new IOException(e.toString(), e), true));
        } finally {
            try {
                reader.close();
            } catch (IOException | RuntimeException e) {
                LOG.debug("Closing the CD reader failed", e);
            }
        }
    }

    /** Waits for room in the queue; gives up when the source is closed. */
    private void offer(Chunk c) {
        try {
            while (!closed && !queue.offer(c, 100, TimeUnit.MILLISECONDS)) {
                // wait for the player to take a chunk
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    // --8<-- [end:read-ahead]

    // --8<-- [start:read]
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (!started) {
            started = true;
            awaitPreroll();
        }
        if (chunk == null || chunkPos >= chunk.length()) {
            if (chunk != null && chunk.end()) return -1;
            chunk = take();
            chunkPos = 0;
            if (chunk == null) return -1;
            if (chunk.error() != null) throw chunk.error();
            if (chunk.end()) return -1;
        }
        int n = Math.min(length - length % 4, chunk.length() - chunkPos);
        System.arraycopy(chunk.data(), chunkPos, buffer, offset, n);
        chunkPos += n;
        return n;
    }
    // --8<-- [end:read]

    /** Lets the reader get a second ahead, so playback does not stutter right at the start. */
    private void awaitPreroll() throws IOException {
        try {
            while (!closed && queue.size() < PREROLL && thread.isAlive()
                    && queue.stream().noneMatch(Chunk::end)) {
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    /** The next chunk, or null when the source was closed or the reader thread is gone. */
    private Chunk take() throws IOException {
        try {
            while (!closed) {
                Chunk c = queue.poll(100, TimeUnit.MILLISECONDS);
                if (c != null) return c;
                if (!thread.isAlive() && queue.isEmpty()) return null;
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    @Override
    public long frameLength() {
        return (long) (endLba - startLba) * FRAMES_PER_SECTOR;
    }

    /** Safe from any thread and more than once; the reader thread closes the drive after its current read. */
    @Override
    public void close() {
        closed = true;
        queue.clear();
    }

    /** For tests: waits until the reader thread has closed the drive. */
    boolean awaitClosed(long millis) throws InterruptedException {
        return thread.join(java.time.Duration.ofMillis(millis));
    }
}

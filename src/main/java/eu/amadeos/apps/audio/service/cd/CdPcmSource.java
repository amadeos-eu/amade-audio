package eu.amadeos.apps.audio.service.cd;

import eu.amadeos.apps.audio.domain.CdTrack;
import eu.amadeos.apps.audio.service.engine.PcmSource;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;
import java.io.IOException;

/** Audio from a range of CD sectors: 44.1 kHz, 16-bit, stereo, little-endian as on the disc. */
final class CdPcmSource implements PcmSource {
    static final AudioFormat FORMAT = PcmSource.pcm16(44100f, 2);
    static final int FRAMES_PER_SECTOR = CdTrack.BYTES_PER_SECTOR / 4;
    /** Sectors per drive read: about a third of a second. */
    private static final int CHUNK = 26;

    /** Reads consecutive sectors; {@code lba} includes the 150-sector lead-in. */
    interface SectorReader extends Closeable {
        void read(int lba, int sectors, byte[] destination) throws IOException;
    }

    private final SectorReader reader;
    private final int startLba;
    private final int endLba;
    private final byte[] chunk = new byte[CHUNK * CdTrack.BYTES_PER_SECTOR];
    private int nextLba;
    private int chunkPos;
    private int chunkLength;

    CdPcmSource(SectorReader reader, int startLba, int endLba) {
        this.reader = reader;
        this.startLba = startLba;
        this.endLba = endLba;
        this.nextLba = startLba;
    }

    @Override
    public AudioFormat format() {
        return FORMAT;
    }

    // --8<-- [start:read]
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (chunkPos >= chunkLength) {
            if (nextLba >= endLba) return -1;
            int sectors = Math.min(CHUNK, endLba - nextLba);
            reader.read(nextLba, sectors, chunk);
            nextLba += sectors;
            chunkPos = 0;
            chunkLength = sectors * CdTrack.BYTES_PER_SECTOR;
        }
        int n = Math.min(length - length % 4, chunkLength - chunkPos);
        System.arraycopy(chunk, chunkPos, buffer, offset, n);
        chunkPos += n;
        return n;
    }
    // --8<-- [end:read]

    @Override
    public long frameLength() {
        return (long) (endLba - startLba) * FRAMES_PER_SECTOR;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}

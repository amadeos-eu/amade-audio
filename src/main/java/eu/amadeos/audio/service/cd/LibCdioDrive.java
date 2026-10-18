package eu.amadeos.audio.service.cd;

import com.google.inject.Inject;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import eu.amadeos.audio.domain.CdToc;
import eu.amadeos.audio.domain.CdTrack;
import eu.amadeos.audio.service.engine.PcmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The CD drive through libcdio (via JNA). Audio is read with libcdio-paranoia for error correction
 * when it is installed, otherwise with plain sector reads.
 */
public final class LibCdioDrive implements CdDrive {
    private static final Logger LOG = LoggerFactory.getLogger(LibCdioDrive.class);
    private static final int LEAD_IN = 150;

    private final String device;
    private final LibCdio cdio;
    private final LibCdioParanoia paranoia;
    private final LibCdioParanoia.Cdda cdda;

    @Inject
    public LibCdioDrive() {
        this(defaultDevice());
    }

    public LibCdioDrive(String device) {
        this.device = device;
        this.cdio = load(LibCdio.class, "cdio", "libcdio.so.19", "libcdio.so.18");
        LibCdioParanoia.Cdda d = load(LibCdioParanoia.Cdda.class, "cdio_cdda", "libcdio_cdda.so.2");
        LibCdioParanoia p = d == null ? null : load(LibCdioParanoia.class, "cdio_paranoia", "libcdio_paranoia.so.2");
        this.cdda = p == null ? null : d;
        this.paranoia = p;
        if (cdio == null) LOG.info("libcdio is not installed: the CD rack stays empty");
        else if (paranoia == null) LOG.info("libcdio-paranoia is not installed: reading CDs without error correction");
    }

    static String defaultDevice() {
        for (String candidate : List.of("/dev/cdrom", "/dev/sr0", "/dev/sr1")) {
            if (Files.exists(Path.of(candidate))) return candidate;
        }
        return "/dev/sr0";
    }

    private static <T extends com.sun.jna.Library> T load(Class<T> type, String... names) {
        for (String name : names) {
            try {
                return Native.load(name, type);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                LOG.debug("Cannot load {}: {}", name, e.getMessage());
            }
        }
        return null;
    }

    @Override
    public String device() {
        return device;
    }

    @Override
    public boolean isAvailable() {
        return cdio != null && Files.exists(Path.of(device));
    }

    @Override
    public synchronized Optional<CdDisc> readDisc() {
        if (!isAvailable()) return Optional.empty();
        Pointer handle = cdio.cdio_open(device, LibCdio.DRIVER_UNKNOWN);
        if (handle == null) return Optional.empty();
        try {
            int count = cdio.cdio_get_num_tracks(handle) & 0xFF;
            int first = cdio.cdio_get_first_track_num(handle) & 0xFF;
            if (count == 0 || count == 0xFF || first == 0 || first == 0xFF) return Optional.empty();
            int[] lbas = new int[count];
            boolean[] audio = new boolean[count];
            boolean anyAudio = false;
            for (int i = 0; i < count; i++) {
                byte track = (byte) (first + i);
                lbas[i] = cdio.cdio_get_track_lba(handle, track);
                audio[i] = cdio.cdio_get_track_format(handle, track) == LibCdio.TRACK_FORMAT_AUDIO;
                anyAudio |= audio[i];
            }
            if (!anyAudio) return Optional.empty();
            int leadOut = cdio.cdio_get_track_lba(handle, LibCdio.LEADOUT_TRACK);
            if (leadOut <= 0 || Arrays.stream(lbas).anyMatch(l -> l < 0)) return Optional.empty();
            CdToc toc = CdToc.of(first, lbas, leadOut, audio);
            return Optional.of(new CdDisc(toc, cdText(handle, toc), DiscId.of(toc)));
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the CD in {}: {}", device, e.toString());
            return Optional.empty();
        } finally {
            cdio.cdio_destroy(handle);
        }
    }

    private CdText cdText(Pointer handle, CdToc toc) {
        try {
            Pointer text = cdio.cdio_get_cdtext(handle);
            if (text == null) return CdText.NONE;
            List<String> titles = new ArrayList<>();
            List<String> performers = new ArrayList<>();
            for (CdTrack t : toc.audioTracks()) {
                titles.add(nn(cdio.cdtext_get_const(text, LibCdio.CDTEXT_FIELD_TITLE, (byte) t.number())));
                performers.add(nn(cdio.cdtext_get_const(text, LibCdio.CDTEXT_FIELD_PERFORMER, (byte) t.number())));
            }
            return new CdText(nn(cdio.cdtext_get_const(text, LibCdio.CDTEXT_FIELD_TITLE, (byte) 0)),
                    nn(cdio.cdtext_get_const(text, LibCdio.CDTEXT_FIELD_PERFORMER, (byte) 0)), titles, performers);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            LOG.debug("No CD-Text: {}", e.toString());
            return CdText.NONE;
        }
    }

    @Override
    public PcmSource open(CdTrack track, int sectorOffset) throws IOException {
        if (!isAvailable()) throw new IOException("No CD drive");
        int start = track.lba() + Math.max(0, Math.min(sectorOffset, track.sectors()));
        int end = track.lba() + track.sectors();
        CdPcmSource.SectorReader reader = null;
        if (paranoia != null) {
            try {
                reader = new ParanoiaReader();
            } catch (IOException | RuntimeException | UnsatisfiedLinkError e) {
                LOG.warn("libcdio-paranoia failed, reading without error correction: {}", e.toString());
            }
        }
        if (reader == null) reader = new PlainReader();
        return new CdPcmSource(reader, start, end);
    }

    @Override
    public void eject() throws IOException {
        if (cdio != null) {
            try {
                if (cdio.cdio_eject_media_drive(device) == 0) return;
            } catch (RuntimeException | UnsatisfiedLinkError e) {
                LOG.debug("libcdio eject failed: {}", e.toString());
            }
        }
        try {
            Process p = new ProcessBuilder("eject", device).redirectErrorStream(true).start();
            if (p.waitFor() != 0) throw new IOException("eject: " + new String(p.getInputStream().readAllBytes()).strip());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static String nn(String s) {
        return s == null ? "" : s.strip();
    }

    /** Plain reads with libcdio, retried three times; unreadable sectors play as silence. */
    private final class PlainReader implements CdPcmSource.SectorReader {
        private final Pointer handle;
        private Memory memory;

        PlainReader() throws IOException {
            handle = cdio.cdio_open(device, LibCdio.DRIVER_UNKNOWN);
            if (handle == null) throw new IOException("Cannot open " + device);
        }

        @Override
        public void read(int lba, int sectors, byte[] destination) throws IOException {
            int bytes = sectors * CdTrack.BYTES_PER_SECTOR;
            if (memory == null || memory.size() < bytes) memory = new Memory(bytes);
            for (int attempt = 0; attempt < 3; attempt++) {
                if (cdio.cdio_read_audio_sectors(handle, memory, lba - LEAD_IN, sectors) == 0) {
                    memory.read(0, destination, 0, bytes);
                    return;
                }
            }
            LOG.warn("Cannot read sectors {}..{}; playing silence", lba, lba + sectors - 1);
            Arrays.fill(destination, 0, bytes, (byte) 0);
        }

        @Override
        public void close() {
            cdio.cdio_destroy(handle);
        }
    }

    /** Error-correcting reads with libcdio-paranoia, one sector at a time. */
    private final class ParanoiaReader implements CdPcmSource.SectorReader {
        private final Pointer drive;
        private final Pointer state;
        private int position = -1;

        ParanoiaReader() throws IOException {
            drive = cdda.cdio_cddap_identify(device, LibCdioParanoia.Cdda.MESSAGE_FORGETIT, null);
            if (drive == null) throw new IOException("libcdio-paranoia cannot identify " + device);
            if (cdda.cdio_cddap_open(drive) != 0) {
                cdda.cdio_cddap_close(drive);
                throw new IOException("libcdio-paranoia cannot open " + device);
            }
            state = paranoia.cdio_paranoia_init(drive);
            if (state == null) {
                cdda.cdio_cddap_close(drive);
                throw new IOException("libcdio-paranoia init failed");
            }
            paranoia.cdio_paranoia_modeset(state, LibCdioParanoia.MODE_DEFAULT);
        }

        @Override
        public void read(int lba, int sectors, byte[] destination) throws IOException {
            int lsn = lba - LEAD_IN;
            if (lsn != position) {
                paranoia.cdio_paranoia_seek(state, lsn, LibCdioParanoia.SEEK_SET);
                position = lsn;
            }
            boolean swap = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
            for (int i = 0; i < sectors; i++) {
                Pointer data = paranoia.cdio_paranoia_read(state, null);
                int at = i * CdTrack.BYTES_PER_SECTOR;
                if (data == null) {
                    Arrays.fill(destination, at, at + CdTrack.BYTES_PER_SECTOR, (byte) 0);
                } else {
                    data.read(0, destination, at, CdTrack.BYTES_PER_SECTOR);
                    if (swap) {
                        for (int k = at; k < at + CdTrack.BYTES_PER_SECTOR; k += 2) {
                            byte b = destination[k];
                            destination[k] = destination[k + 1];
                            destination[k + 1] = b;
                        }
                    }
                }
                position++;
            }
        }

        @Override
        public void close() {
            paranoia.cdio_paranoia_free(state);
            cdda.cdio_cddap_close(drive);
        }
    }
}

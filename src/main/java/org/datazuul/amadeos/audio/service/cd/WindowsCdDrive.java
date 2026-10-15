package org.datazuul.amadeos.audio.service.cd;

import com.google.inject.Inject;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import org.datazuul.amadeos.audio.domain.CdToc;
import org.datazuul.amadeos.audio.domain.CdTrack;
import org.datazuul.amadeos.audio.service.engine.PcmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The CD drive on Windows, through the CD-ROM class driver's IOCTLs (kernel32 via JNA): no extra
 * libraries needed. Reads audio without paranoia-style error correction; unreadable sectors are retried
 * one by one and otherwise play as silence.
 */
public final class WindowsCdDrive implements CdDrive {
    private static final Logger LOG = LoggerFactory.getLogger(WindowsCdDrive.class);
    private static final int LEAD_IN = 150;
    /** IOCTL_CDROM_RAW_READ addresses the disc in 2048-byte data sectors, whatever the track type. */
    private static final int COOKED_SECTOR_SIZE = 2048;
    private static final int TRACK_MODE_CDDA = 2;
    private static final int TOC_FORMAT_TOC = 0x0;
    private static final int TOC_FORMAT_CDTEXT = 0x5;
    private static final int TOC_MSF = 0x80;
    private static final int LEADOUT_TRACK = 0xAA;
    private static final int TOC_SIZE = 4 + 100 * 8;
    private static final int CDTEXT_PACK_SIZE = 18;
    /** Up to 8 blocks of 256 packs. */
    private static final int CDTEXT_SIZE = 4 + 8 * 256 * CDTEXT_PACK_SIZE;
    private static final int PACK_TITLE = 0x80;
    private static final int PACK_PERFORMER = 0x81;

    private final Kernel32 kernel32;
    private final boolean fixedDevice;
    private volatile String device;
    /** Media change count of the cached disc; -1 = nothing cached. */
    private int cachedChangeCount = -1;
    private Optional<CdDisc> cachedDisc = Optional.empty();

    @Inject
    public WindowsCdDrive() {
        this(null);
    }

    /** @param device drive letter like {@code D:}, or null for the first CD drive */
    public WindowsCdDrive(String device) {
        this.kernel32 = load();
        this.fixedDevice = device != null;
        this.device = device != null ? device : firstCdDrive().orElse("D:");
        if (kernel32 == null) LOG.info("kernel32 is not available: the CD rack stays empty");
    }

    private static Kernel32 load() {
        try {
            return Native.load("kernel32", Kernel32.class);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            LOG.debug("Cannot load kernel32: {}", e.getMessage());
            return null;
        }
    }

    private Optional<String> firstCdDrive() {
        if (kernel32 == null) return Optional.empty();
        int drives = kernel32.GetLogicalDrives();
        for (int i = 0; i < 26; i++) {
            String letter = (char) ('A' + i) + ":";
            if ((drives & (1 << i)) != 0 && isCdDrive(letter)) return Optional.of(letter);
        }
        return Optional.empty();
    }

    private boolean isCdDrive(String letter) {
        return kernel32.GetDriveTypeW(new WString(letter + "\\")) == Kernel32.DRIVE_CDROM;
    }

    @Override
    public String device() {
        return device;
    }

    @Override
    public boolean isAvailable() {
        if (kernel32 == null) return false;
        if (isCdDrive(device)) return true;
        if (fixedDevice) return false;
        // A USB drive may have been plugged in since
        Optional<String> found = firstCdDrive();
        found.ifPresent(d -> device = d);
        return found.isPresent();
    }

    @Override
    public synchronized Optional<CdDisc> readDisc() {
        if (!isAvailable()) return Optional.empty();
        Pointer handle = openHandle();
        if (handle == null) return Optional.empty();
        try {
            OptionalInt changeCount = mediaChangeCount(handle);
            if (changeCount.isEmpty()) {
                cachedChangeCount = -1;
                return Optional.empty();
            }
            // The TOC is only read again after a disc change, not on every poll
            if (changeCount.getAsInt() == cachedChangeCount) return cachedDisc;
            byte[] tocData = readTocEx(handle, TOC_FORMAT_TOC | TOC_MSF, 1, TOC_SIZE);
            Optional<CdToc> toc = tocData == null ? Optional.empty() : parseToc(tocData);
            Optional<CdDisc> disc = toc.map(t -> new CdDisc(t, cdText(handle, t), DiscId.of(t)));
            cachedChangeCount = changeCount.getAsInt();
            cachedDisc = disc;
            return disc;
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the CD in {}: {}", device, e.toString());
            return Optional.empty();
        } finally {
            kernel32.CloseHandle(handle);
        }
    }

    /** The media change count, or empty without a readable disc (no disc, tray open, spinning up). */
    private OptionalInt mediaChangeCount(Pointer handle) {
        Memory count = new Memory(4);
        IntByReference returned = new IntByReference();
        if (!kernel32.DeviceIoControl(handle, Kernel32.IOCTL_STORAGE_CHECK_VERIFY2, null, 0, count, 4, returned, null)) {
            return OptionalInt.empty();
        }
        // A driver that does not report the count gets -2: never equal to the cache, so the TOC is read each time
        return OptionalInt.of(returned.getValue() >= 4 ? count.getInt(0) : -2);
    }

    private byte[] readTocEx(Pointer handle, int formatAndMsf, int sessionTrack, int size) {
        Memory request = new Memory(4);
        request.clear();
        request.setByte(0, (byte) formatAndMsf);
        request.setByte(1, (byte) sessionTrack);
        Memory response = new Memory(size);
        response.clear();
        IntByReference returned = new IntByReference();
        if (!kernel32.DeviceIoControl(handle, Kernel32.IOCTL_CDROM_READ_TOC_EX, request, 4, response, size, returned,
                null)) {
            LOG.debug("READ_TOC_EX format {} failed: error {}", formatAndMsf & 0x0F, Native.getLastError());
            return null;
        }
        return response.getByteArray(0, Math.min(size, returned.getValue()));
    }

    private CdText cdText(Pointer handle, CdToc toc) {
        try {
            byte[] data = readTocEx(handle, TOC_FORMAT_CDTEXT, 0, CDTEXT_SIZE);
            return data == null ? CdText.NONE : parseCdText(data, toc);
        } catch (RuntimeException e) {
            LOG.debug("No CD-Text: {}", e.toString());
            return CdText.NONE;
        }
    }

    @Override
    public PcmSource open(CdTrack track, int sectorOffset) throws IOException {
        if (!isAvailable()) throw new IOException("No CD drive");
        int start = track.lba() + Math.max(0, Math.min(sectorOffset, track.sectors()));
        int end = track.lba() + track.sectors();
        return new CdPcmSource(new RawReader(), start, end);
    }

    @Override
    public synchronized void eject() throws IOException {
        if (kernel32 == null) throw new IOException("No CD drive");
        Pointer handle = openHandle();
        if (handle == null) throw new IOException("Cannot open " + device + " (error " + Native.getLastError() + ")");
        try {
            if (!kernel32.DeviceIoControl(handle, Kernel32.IOCTL_STORAGE_EJECT_MEDIA, null, 0, null, 0,
                    new IntByReference(), null)) {
                throw new IOException("Cannot eject " + device + " (error " + Native.getLastError() + ")");
            }
            cachedChangeCount = -1;
        } finally {
            kernel32.CloseHandle(handle);
        }
    }

    /** Opens {@code \\.\D:}; null on failure. */
    private Pointer openHandle() {
        Pointer handle = kernel32.CreateFileW(new WString("\\\\.\\" + device), Kernel32.GENERIC_READ,
                Kernel32.FILE_SHARE_READ | Kernel32.FILE_SHARE_WRITE, null, Kernel32.OPEN_EXISTING, 0, null);
        if (handle == null || Pointer.nativeValue(handle) == -1) {
            LOG.debug("Cannot open {}: error {}", device, Native.getLastError());
            return null;
        }
        return handle;
    }

    // ---- parsing (package-private for tests) --------------------------------------------------------

    /**
     * Parses a CDROM_TOC in MSF form: 2-byte big-endian length, first and last track, then 8-byte
     * TRACK_DATA entries (reserved, Control/Adr, track number, reserved, address as 0/M/S/F).
     */
    static Optional<CdToc> parseToc(byte[] data) {
        if (data.length < 4) return Optional.empty();
        int limit = Math.min(data.length, (((data[0] & 0xFF) << 8) | (data[1] & 0xFF)) + 2);
        int first = data[2] & 0xFF;
        int last = data[3] & 0xFF;
        if (first == 0 || last < first || last > 99) return Optional.empty();
        int count = last - first + 1;
        int[] lbas = new int[count];
        boolean[] audio = new boolean[count];
        Arrays.fill(lbas, -1);
        int leadOut = -1;
        for (int at = 4; at + 8 <= limit; at += 8) {
            int control = data[at + 1] & 0x0F;
            int number = data[at + 2] & 0xFF;
            int lba = ((data[at + 5] & 0xFF) * 60 + (data[at + 6] & 0xFF)) * CdTrack.SECTORS_PER_SECOND
                    + (data[at + 7] & 0xFF);
            if (number == LEADOUT_TRACK) {
                leadOut = lba;
            } else if (number >= first && number <= last) {
                lbas[number - first] = lba;
                audio[number - first] = (control & 0x04) == 0;
            }
        }
        if (leadOut <= 0 || Arrays.stream(lbas).anyMatch(l -> l < 0)) return Optional.empty();
        boolean anyAudio = false;
        for (boolean a : audio) anyAudio |= a;
        if (!anyAudio) return Optional.empty();
        return Optional.of(CdToc.of(first, lbas, leadOut, audio));
    }

    /**
     * Parses CDROM_TOC_CD_TEXT_DATA: a 4-byte header, then 18-byte packs (type, track, sequence,
     * block/character position, 12 text bytes, CRC). The texts of one type run on across packs as
     * NUL-terminated strings, one per track from the first pack's track; a lone TAB repeats the previous
     * one. Only block 0 in a single-byte character set (ISO 8859-1) is read.
     */
    static CdText parseCdText(byte[] data, CdToc toc) {
        if (data.length < 4) return CdText.NONE;
        int limit = Math.min(data.length, (((data[0] & 0xFF) << 8) | (data[1] & 0xFF)) + 2);
        Map<Integer, ByteArrayOutputStream> texts = new HashMap<>();
        Map<Integer, Integer> startTrack = new HashMap<>();
        for (int at = 4; at + CDTEXT_PACK_SIZE <= limit; at += CDTEXT_PACK_SIZE) {
            int type = data[at] & 0xFF;
            if (type != PACK_TITLE && type != PACK_PERFORMER) continue;
            boolean extension = (data[at + 1] & 0x80) != 0;
            int block = (data[at + 3] >> 4) & 0x07;
            boolean doubleByte = (data[at + 3] & 0x80) != 0;
            if (extension || block != 0 || doubleByte) continue;
            startTrack.putIfAbsent(type, data[at + 1] & 0x7F);
            texts.computeIfAbsent(type, t -> new ByteArrayOutputStream()).write(data, at + 4, 12);
        }
        if (texts.isEmpty()) return CdText.NONE;
        Map<Integer, String> titles = byTrack(texts.get(PACK_TITLE), startTrack.get(PACK_TITLE));
        Map<Integer, String> performers = byTrack(texts.get(PACK_PERFORMER), startTrack.get(PACK_PERFORMER));
        List<String> trackTitles = new ArrayList<>();
        List<String> trackPerformers = new ArrayList<>();
        for (CdTrack t : toc.audioTracks()) {
            trackTitles.add(titles.getOrDefault(t.number(), ""));
            trackPerformers.add(performers.getOrDefault(t.number(), ""));
        }
        return new CdText(titles.getOrDefault(0, ""), performers.getOrDefault(0, ""), trackTitles, trackPerformers);
    }

    private static Map<Integer, String> byTrack(ByteArrayOutputStream text, Integer firstTrack) {
        Map<Integer, String> result = new HashMap<>();
        if (text == null || firstTrack == null) return result;
        byte[] bytes = text.toByteArray();
        int track = firstTrack;
        int start = 0;
        String previous = "";
        // Every NUL ends the string of one track; the padding of the last pack only adds empty ones
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != 0) continue;
            String s = new String(bytes, start, i - start, StandardCharsets.ISO_8859_1);
            if (s.equals("\t")) s = previous;
            result.putIfAbsent(track++, s.strip());
            previous = s;
            start = i + 1;
        }
        return result;
    }

    /**
     * Raw CD-DA reads with IOCTL_CDROM_RAW_READ. A failed chunk is read again sector by sector, so one bad
     * sector or a drive with a smaller transfer limit does not silence the whole chunk.
     */
    private final class RawReader implements CdPcmSource.SectorReader {
        private final Pointer handle;
        private final Memory info = new Memory(16);
        private final IntByReference returned = new IntByReference();
        private Memory buffer;

        RawReader() throws IOException {
            handle = openHandle();
            if (handle == null) throw new IOException("Cannot open " + device + " (error " + Native.getLastError() + ")");
        }

        @Override
        public void read(int lba, int sectors, byte[] destination) {
            if (readRaw(lba, sectors, destination, 0)) return;
            for (int i = 0; i < sectors; i++) {
                int at = i * CdTrack.BYTES_PER_SECTOR;
                boolean ok = false;
                for (int attempt = 0; attempt < 3 && !ok; attempt++) ok = readRaw(lba + i, 1, destination, at);
                if (!ok) {
                    LOG.warn("Cannot read sector {} (error {}); playing silence", lba + i, Native.getLastError());
                    Arrays.fill(destination, at, at + CdTrack.BYTES_PER_SECTOR, (byte) 0);
                }
            }
        }

        private boolean readRaw(int lba, int sectors, byte[] destination, int at) {
            int bytes = sectors * CdTrack.BYTES_PER_SECTOR;
            if (buffer == null || buffer.size() < bytes) buffer = new Memory(bytes);
            // RAW_READ_INFO: LARGE_INTEGER DiskOffset, ULONG SectorCount, TRACK_MODE_TYPE TrackMode
            info.setLong(0, (long) (lba - LEAD_IN) * COOKED_SECTOR_SIZE);
            info.setInt(8, sectors);
            info.setInt(12, TRACK_MODE_CDDA);
            if (!kernel32.DeviceIoControl(handle, Kernel32.IOCTL_CDROM_RAW_READ, info, 16, buffer, bytes, returned,
                    null) || returned.getValue() < bytes) {
                return false;
            }
            buffer.read(0, destination, at, bytes);
            return true;
        }

        @Override
        public void close() {
            kernel32.CloseHandle(handle);
        }
    }
}

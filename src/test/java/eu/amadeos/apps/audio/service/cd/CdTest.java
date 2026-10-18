package eu.amadeos.apps.audio.service.cd;

import eu.amadeos.apps.audio.domain.CdToc;
import eu.amadeos.apps.audio.domain.CdTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class CdTest {
    private static final CdToc TOC = CdToc.of(1, new int[]{150, 15363, 32314, 46592, 63414}, 95462, null);

    @Test
    void discIdHasTheMusicBrainzForm() {
        String id = DiscId.of(TOC);
        assertEquals(28, id.length());
        assertTrue(id.matches("[A-Za-z0-9._]{27}-"), id);
        assertEquals(id, DiscId.of(CdToc.of(1, new int[]{150, 15363, 32314, 46592, 63414}, 95462, null)));
        assertNotEquals(id, DiscId.of(CdToc.of(1, new int[]{150, 15363, 32314, 46592, 63414}, 95463, null)));
        assertEquals("1+5+95462+150+15363+32314+46592+63414", DiscId.tocString(TOC));
    }

    @Test
    void enhancedCdsLeaveOutTheDataTrack() {
        CdToc enhanced = CdToc.of(1, new int[]{150, 15363, 32314, 46592, 63414, 120000}, 160000,
                new boolean[]{true, true, true, true, true, false});
        assertEquals("1+5+108600+150+15363+32314+46592+63414", DiscId.tocString(enhanced));
        assertEquals(DiscId.of(CdToc.of(1, new int[]{150, 15363, 32314, 46592, 63414}, 108600, null)), DiscId.of(enhanced));
    }

    @Test
    void cdSourceReadsTheSectorRange() throws Exception {
        List<int[]> reads = new ArrayList<>();
        CdPcmSource source = new CdPcmSource(new CdPcmSource.SectorReader() {
            @Override
            public void read(int lba, int sectors, byte[] destination) {
                reads.add(new int[]{lba, sectors});
                for (int i = 0; i < sectors * CdTrack.BYTES_PER_SECTOR; i++) destination[i] = (byte) (lba + i / CdTrack.BYTES_PER_SECTOR);
            }

            @Override
            public void close() {
            }
        }, 1000, 1030);
        assertEquals(30 * 588, source.frameLength());
        byte[] buffer = new byte[1000];
        long total = 0;
        int n;
        byte first = -1;
        while ((n = source.read(buffer, 0, buffer.length)) >= 0) {
            if (total == 0) first = buffer[0];
            total += n;
            assertEquals(0, n % 4);
        }
        assertEquals(30L * CdTrack.BYTES_PER_SECTOR, total);
        assertEquals((byte) 1000, first);
        assertEquals(1000, reads.get(0)[0]);
        assertEquals(1026, reads.get(1)[0]);
        assertEquals(4, reads.get(1)[1]);
    }

    @Test
    void driveWithoutLibcdioIsUnavailable() {
        LibCdioDrive drive = new LibCdioDrive("/dev/does-not-exist");
        assertFalse(drive.isAvailable());
        assertTrue(drive.readDisc().isEmpty());
        assertThrows(java.io.IOException.class, () -> drive.open(new CdTrack(1, 150, 100, true), 0));
    }

    @Test
    void windowsTocIsReadFromMsfAddresses() {
        byte[] data = windowsToc(1, new int[]{150, 15363, 32314, 46592, 63414}, 95462, 0);
        CdToc toc = WindowsCdDrive.parseToc(data).orElseThrow();
        assertEquals(TOC, toc);
        assertEquals(DiscId.of(TOC), DiscId.of(toc));
    }

    @Test
    void windowsTocMarksDataTracks() {
        byte[] data = windowsToc(1, new int[]{150, 15363, 120000}, 160000, 0b100);
        CdToc toc = WindowsCdDrive.parseToc(data).orElseThrow();
        assertEquals(List.of(true, true, false), toc.tracks().stream().map(CdTrack::audio).toList());
        assertTrue(WindowsCdDrive.parseToc(windowsToc(1, new int[]{150}, 9000, 0b1)).isEmpty(), "data disc");
        assertTrue(WindowsCdDrive.parseToc(new byte[]{0, 2, 0, 0}).isEmpty(), "no tracks");
    }

    @Test
    void windowsCdTextRunsAcrossPacks() {
        CdToc toc = CdToc.of(1, new int[]{150, 15363, 32314}, 46592, null);
        // Titles: album, then tracks 1..3; "\t" repeats the previous one. Performers only for the album.
        byte[] titles = "Album Title\0First Track\0Second\0\t\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] performer = "The Band\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        java.io.ByteArrayOutputStream packs = new java.io.ByteArrayOutputStream();
        int seq = 0;
        for (int at = 0; at < titles.length; at += 12) packs.writeBytes(pack(0x80, seq++, Arrays.copyOfRange(titles, at, at + 12)));
        packs.writeBytes(pack(0x81, seq++, Arrays.copyOf(performer, 12)));
        packs.writeBytes(pack(0x8F, seq, new byte[12])); // size information: ignored
        byte[] body = packs.toByteArray();
        byte[] data = new byte[4 + body.length];
        data[0] = (byte) ((body.length + 2) >> 8);
        data[1] = (byte) (body.length + 2);
        System.arraycopy(body, 0, data, 4, body.length);

        CdText text = WindowsCdDrive.parseCdText(data, toc);
        assertEquals("Album Title", text.album());
        assertEquals("The Band", text.performer());
        assertEquals(List.of("First Track", "Second", "Second"), text.titles());
        assertEquals(List.of("", "", ""), text.performers());
        assertSame(CdText.NONE, WindowsCdDrive.parseCdText(new byte[]{0, 2, 0, 0}, toc));
    }

    @Test
    void windowsDriveIsUnavailableElsewhere() {
        assumeFalse(System.getProperty("os.name", "").startsWith("Windows"));
        WindowsCdDrive drive = new WindowsCdDrive("D:");
        assertFalse(drive.isAvailable());
        assertTrue(drive.readDisc().isEmpty());
        assertThrows(java.io.IOException.class, () -> drive.open(new CdTrack(1, 150, 100, true), 0));
        assertThrows(java.io.IOException.class, drive::eject);
    }

    /** CDROM_TOC with MSF addresses; bit i of dataTracks marks track i+first as data. */
    private static byte[] windowsToc(int first, int[] lbas, int leadOut, int dataTracks) {
        int entries = lbas.length + 1;
        byte[] data = new byte[4 + entries * 8];
        int length = 2 + entries * 8;
        data[0] = (byte) (length >> 8);
        data[1] = (byte) length;
        data[2] = (byte) first;
        data[3] = (byte) (first + lbas.length - 1);
        for (int i = 0; i < entries; i++) {
            int at = 4 + i * 8;
            boolean leadOutEntry = i == lbas.length;
            int lba = leadOutEntry ? leadOut : lbas[i];
            boolean data_ = !leadOutEntry && (dataTracks & (1 << i)) != 0;
            data[at + 1] = (byte) (0x10 | (data_ ? 0x04 : 0x00)); // Adr 1 (high nibble), Control (low nibble)
            data[at + 2] = (byte) (leadOutEntry ? 0xAA : first + i);
            data[at + 5] = (byte) (lba / 75 / 60);
            data[at + 6] = (byte) (lba / 75 % 60);
            data[at + 7] = (byte) (lba % 75);
        }
        return data;
    }

    /** An 18-byte CD-Text pack of block 0 starting at track 0. */
    private static byte[] pack(int type, int sequence, byte[] text) {
        byte[] pack = new byte[18];
        pack[0] = (byte) type;
        pack[2] = (byte) sequence;
        System.arraycopy(text, 0, pack, 4, 12);
        return pack;
    }
}

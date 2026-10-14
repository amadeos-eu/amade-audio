package org.datazuul.amadeos.audio.service.cd;

import org.datazuul.amadeos.audio.domain.CdToc;
import org.datazuul.amadeos.audio.domain.CdTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}

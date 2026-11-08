package eu.amadeos.apps.audio.service.decode;

import eu.amadeos.apps.audio.domain.PlaylistEntry;
import eu.amadeos.apps.audio.service.engine.PcmSource;
import eu.amadeos.apps.audio.service.player.AudioFiles;
import eu.amadeos.apps.audio.service.player.TagReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Plays a small ProTracker module written by the test; needs libopenmpt (skipped without it). */
class ModuleRendererTest {
    /** One pattern of 64 rows at speed 6 and 125 BPM: 64 * 6 * 20 ms. */
    private static final double SONG_SECONDS = 7.68;

    @TempDir
    Path dir;
    Path mod;

    @BeforeEach
    void writeModule() throws IOException {
        assumeTrue(ModuleRenderer.available(), "libopenmpt is not installed");
        mod = dir.resolve("test.mod");
        Files.write(mod, proTrackerModule("amadeOS test"));
    }

    @Test
    void readsTitleAndLength() {
        PlaylistEntry entry = new TagReader().read(mod);
        assertEquals("amadeOS test", entry.title());
        assertEquals("MOD", entry.format());
        assertEquals(SONG_SECONDS, entry.length().toMillis() / 1000.0, 0.05);
        assertTrue(AudioFiles.isAudio(mod));
    }

    @Test
    void rendersTheWholeSongOnce() throws IOException {
        try (PcmSource source = new DecoderFactory().openFile(mod)) {
            assertTrue(PcmSource.pcm16(44100f, 2).matches(source.format()));
            assertEquals(SONG_SECONDS * 44100, source.frameLength(), 44100 * 0.05);
            byte[] buffer = new byte[16384];
            long frames = 0;
            int peak = 0;
            int n;
            while ((n = source.read(buffer, 0, buffer.length)) >= 0) {
                frames += n / 4;
                for (int i = 0; i < n; i += 2) {
                    peak = Math.max(peak, Math.abs((short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8))));
                }
            }
            assertEquals(source.frameLength(), frames, 44100 * 0.1);
            assertTrue(peak > 1000, "the note is audible, peak " + peak);
        }
    }

    @Test
    void seeksWithoutRendering() throws IOException {
        try (PcmSource source = new DecoderFactory().openFile(mod)) {
            long skipped = source.skipFrames(5 * 44100);
            assertEquals(5 * 44100, skipped, 44100 * 0.05);
            byte[] buffer = new byte[16384];
            long rest = 0;
            int n;
            while ((n = source.read(buffer, 0, buffer.length)) >= 0) rest += n / 4;
            assertEquals(source.frameLength() - skipped, rest, 44100 * 0.1);
        }
    }

    @Test
    void closingTwiceIsHarmless() throws IOException {
        PcmSource source = new DecoderFactory().openFile(mod);
        source.close();
        source.close();
        assertEquals(-1, source.read(new byte[400], 0, 400));
    }

    @Test
    void rejectsFilesThatAreNoModules() throws IOException {
        Path fake = dir.resolve("fake.xm");
        Files.writeString(fake, "this is not a module");
        IOException e = assertThrows(IOException.class, () -> new DecoderFactory().openFile(fake));
        assertTrue(e.getMessage().contains("fake.xm"), e.getMessage());
        assertNull(new TagReader().read(fake).title());
    }

    /** A 4-channel "M.K." module: a looped square wave, played once at row 0 of the only pattern. */
    static byte[] proTrackerModule(String title) {
        int sampleBytes = 64;
        byte[] data = new byte[20 + 31 * 30 + 2 + 128 + 4 + 1024 + sampleBytes];
        byte[] name = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, data, 0, Math.min(20, name.length));
        int s = 20; // sample 1
        data[s + 22] = 0;
        data[s + 23] = (byte) (sampleBytes / 2);  // length in words
        data[s + 25] = 64;                        // volume
        data[s + 29] = (byte) (sampleBytes / 2);  // loop the whole sample
        for (int i = 1; i < 31; i++) data[20 + i * 30 + 29] = 1; // no loop for the empty samples
        int order = 20 + 31 * 30;
        data[order] = 1;      // song length: one position
        data[order + 1] = 127;
        System.arraycopy("M.K.".getBytes(StandardCharsets.US_ASCII), 0, data, order + 2 + 128, 4);
        int pattern = order + 2 + 128 + 4;
        int period = 428;     // C-2
        data[pattern] = (byte) ((period >> 8) & 0x0f);
        data[pattern + 1] = (byte) period;
        data[pattern + 2] = 0x10;  // sample 1, no effect
        int sample = pattern + 1024;
        for (int i = 0; i < sampleBytes; i++) data[sample + i] = (byte) (i < sampleBytes / 2 ? 100 : -100);
        return data;
    }
}

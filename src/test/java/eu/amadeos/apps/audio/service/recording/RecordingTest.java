package eu.amadeos.apps.audio.service.recording;

import eu.amadeos.apps.audio.domain.RecordingSettings;
import eu.amadeos.apps.audio.service.decode.DecoderFactory.StreamCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RecordingTest {
    @TempDir
    Path dir;

    /** An MP3 frame header (MPEG-1 layer III, 128 kbit/s, 44.1 kHz) followed by filler. */
    private static byte[] frame(byte fill) {
        byte[] f = new byte[100];
        java.util.Arrays.fill(f, fill);
        f[0] = (byte) 0xFF;
        f[1] = (byte) 0xFB;
        f[2] = (byte) 0x90;
        f[3] = 0x64;
        return f;
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-14T18:30:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void namesFromThePattern() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 14, 20, 5, 9);
        assertEquals("2026-10-14 20-05-09 Miles Davis - So What",
                RecordingNames.name("{date} {time} {artist} - {title}", "Jazz FM", t, "Miles Davis - So What"));
        assertEquals("2026-10-14 20-05-09", RecordingNames.name("{date} {time} {artist} - {title}", "Jazz FM", t, null));
        assertEquals("Jazz FM News", RecordingNames.name("{station} {title}", "Jazz FM", t, "News"));
        assertEquals("AC_DC - Back", RecordingNames.name("{artist} - {title}", "x", t, "AC/DC - Back"));
        assertEquals("Jazz FM 2026-10-14 20-05-09", RecordingNames.name("{artist}", "Jazz FM", t, ""));
    }

    @Test
    void recordsToAPartFileAndRenamesIt() throws Exception {
        List<String[]> tagged = new ArrayList<>();
        MutableClock clock = new MutableClock();
        StreamRecorder recorder = new StreamRecorder(new RecordingSettings(dir, "{artist} - {title}", false, 0),
                "Jazz/FM", StreamCodec.MP3, clock, (file, artist, title, station) -> tagged.add(new String[]{file.toString(), artist, title, station}),
                listener());
        recorder.start("Miles Davis - So What");
        recorder.write(frame((byte) 1), 0, 100);
        Path stationDir = dir.resolve("Jazz_FM");
        assertTrue(Files.exists(stationDir.resolve("Miles Davis - So What.mp3.part")));
        recorder.titleChanged("Coltrane - Naima"); // no split: same file
        recorder.write(frame((byte) 2), 0, 100);
        List<Path> files = recorder.stop();
        assertEquals(List.of(stationDir.resolve("Miles Davis - So What.mp3")), files);
        assertEquals(200, Files.size(files.get(0)));
        assertFalse(Files.exists(stationDir.resolve("Miles Davis - So What.mp3.part")));
        assertEquals("So What", tagged.get(0)[2]);
        assertEquals("Jazz/FM", tagged.get(0)[3]);
        assertFalse(recorder.isRecording());
    }

    // --8<-- [start:split]
    @Test
    void splitsAtTheNextFrameAfterATitleChange() throws Exception {
        StreamRecorder recorder = new StreamRecorder(new RecordingSettings(dir, "{title}", true, 0), "S",
                StreamCodec.MP3, new MutableClock(), null, listener());
        recorder.start("A - One");
        recorder.write(frame((byte) 1), 0, 100);
        recorder.titleChanged("B - Two");
        byte[] chunk = new byte[150];
        System.arraycopy(frame((byte) 1), 50, chunk, 0, 50); // rest of a frame of the old title
        System.arraycopy(frame((byte) 2), 0, chunk, 50, 100);
        recorder.write(chunk, 0, chunk.length);
        List<Path> files = recorder.stop();
        assertEquals(2, files.size());
        assertEquals(150, Files.size(dir.resolve("S/One.mp3")));
        byte[] second = Files.readAllBytes(dir.resolve("S/Two.mp3"));
        assertEquals(100, second.length);
        assertEquals((byte) 0xFF, second[0]);
    }
    // --8<-- [end:split]

    @Test
    void oggIsNeverSplit() throws Exception {
        StreamRecorder recorder = new StreamRecorder(new RecordingSettings(dir, "{title}", true, 0), "S",
                StreamCodec.OGG, new MutableClock(), null, listener());
        recorder.start("A - One");
        recorder.write(new byte[10], 0, 10);
        recorder.titleChanged("B - Two");
        recorder.write(frame((byte) 1), 0, 100);
        assertEquals(1, recorder.stop().size());
        try (Stream<Path> s = Files.list(dir.resolve("S"))) {
            assertEquals(List.of("One.ogg"), s.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void stopsAfterTheMaximumDuration() throws Exception {
        MutableClock clock = new MutableClock();
        List<String> reasons = new ArrayList<>();
        StreamRecorder recorder = new StreamRecorder(new RecordingSettings(dir, "{title}", false, 1), "S",
                StreamCodec.AAC, clock, null, new StreamRecorder.Listener() {
            @Override
            public void status(StreamRecorder.Status status) {
            }

            @Override
            public void stopped(String reason, List<Path> files) {
                reasons.add(reason);
            }
        });
        recorder.start(null);
        recorder.write(new byte[]{1, 2, 3}, 0, 3);
        clock.now = clock.now.plus(Duration.ofSeconds(61));
        recorder.write(new byte[]{4}, 0, 1);
        assertFalse(recorder.isRecording());
        assertEquals(1, reasons.size());
        recorder.write(new byte[]{5}, 0, 1); // ignored
        try (Stream<Path> s = Files.list(dir.resolve("S"))) {
            Path file = s.findFirst().orElseThrow();
            assertTrue(file.toString().endsWith(".aac"));
            assertEquals(4, Files.size(file));
        }
    }

    @Test
    void uniqueNames() throws Exception {
        Files.createFile(dir.resolve("x.mp3"));
        assertEquals(dir.resolve("x (2).mp3"), RecordingNames.unique(dir, "x", "mp3"));
        assertEquals("_a_b", RecordingNames.safe("../a:b"));
    }

    @Test
    void frameSync() {
        byte[] data = {0, (byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x64, (byte) 0xFF, (byte) 0xF1, 0x50, 0};
        assertEquals(1, FrameSync.find(data, 0, data.length, StreamCodec.MP3));
        assertEquals(5, FrameSync.find(data, 0, data.length, StreamCodec.AAC));
        assertEquals(-1, FrameSync.find(data, 0, data.length, StreamCodec.OGG));
    }

    private static StreamRecorder.Listener listener() {
        return new StreamRecorder.Listener() {
            @Override
            public void status(StreamRecorder.Status status) {
            }

            @Override
            public void stopped(String reason, List<Path> files) {
            }
        };
    }
}

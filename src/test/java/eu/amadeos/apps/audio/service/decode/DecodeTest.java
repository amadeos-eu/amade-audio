package eu.amadeos.apps.audio.service.decode;

import eu.amadeos.apps.audio.service.engine.PcmSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecodeTest {
    @TempDir
    Path dir;

    @Test
    void decodesWav() throws Exception {
        Path wav = dir.resolve("tone.wav");
        byte[] pcm = new byte[44100 * 2]; // 1 s of 16-bit mono
        for (int i = 0; i < pcm.length; i += 2) pcm[i + 1] = (byte) (i % 100);
        var format = PcmSource.pcm16(44100f, 1);
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / 2),
                AudioFileFormat.Type.WAVE, wav.toFile());
        try (PcmSource source = new DecoderFactory().openFile(wav)) {
            assertEquals(44100f, source.format().getSampleRate(), 0.1f);
            assertEquals(1, source.format().getChannels());
            assertEquals(44100, source.frameLength());
            long total = 0;
            byte[] buffer = new byte[4000];
            int n;
            while ((n = source.read(buffer, 0, buffer.length)) >= 0) total += n;
            assertEquals(pcm.length, total);
        }
    }

    // --8<-- [start:flac24]
    /** Found by the screenshots: Java Sound has no direct way from 24-bit FLAC to 16-bit PCM. */
    @Test
    void decodesHighResolutionFlac() throws Exception {
        Path flac = dir.resolve("tone-24bit.flac");
        try (var in = DecodeTest.class.getResourceAsStream("tone-24bit.flac")) {
            Files.copy(in, flac);
        }
        try (PcmSource source = new DecoderFactory().openFile(flac)) {
            assertTrue(PcmSource.pcm16(44100f, 1).matches(source.format()), source.format().toString());
            byte[] buffer = new byte[44100];
            int peak = 0;
            long total = 0;
            int n;
            while ((n = source.read(buffer, 0, buffer.length)) >= 0) {
                total += n;
                for (int i = 0; i + 1 < n; i += 2) peak = Math.max(peak, Math.abs((short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8))));
            }
            assertEquals(11025 * 2, total);  // a quarter of a second
            assertEquals(2048, peak, 50);    // the 1 kHz tone at 1/16 of full scale
        }
    }
    // --8<-- [end:flac24]

    @Test
    void unsupportedFilesFailClearly() throws Exception {
        Path junk = dir.resolve("junk.mp3");
        Files.write(junk, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertThrows(java.io.IOException.class, () -> new DecoderFactory().openFile(junk));
    }

    @Test
    void tempoMapFollowsTempoChanges() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, 480);
        Track t = seq.createTrack();
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 480)); // 0.5 s at 120 BPM
        MetaMessage tempo = new MetaMessage(0x51, new byte[]{0x0F, 0x42, 0x40}, 3); // 1 000 000 us/quarter = 60 BPM
        t.add(new MidiEvent(tempo, 480));
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 960)); // +1 s
        List<TempoMap.TimedEvent> timed = TempoMap.timed(seq);
        long noteOn = timed.stream().filter(e -> e.event().getMessage() instanceof ShortMessage s && s.getCommand() == ShortMessage.NOTE_ON)
                .findFirst().orElseThrow().micros();
        long noteOff = timed.stream().filter(e -> e.event().getMessage() instanceof ShortMessage s && s.getCommand() == ShortMessage.NOTE_OFF)
                .findFirst().orElseThrow().micros();
        assertEquals(500_000, noteOn);
        assertEquals(1_500_000, noteOff);
    }

    @Test
    void rendersMidiToPcm() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, 480);
        Track t = seq.createTrack();
        for (int i = 0; i < 4; i++) {
            t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60 + i * 4, 110), i * 240L));
            t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60 + i * 4, 0), i * 240L + 200));
        }
        Path mid = dir.resolve("arpeggio.mid");
        MidiSystem.write(seq, 0, mid.toFile());
        try (PcmSource source = new MidiRenderer(List.of()).open(mid)) {
            assertEquals(44100f, source.format().getSampleRate(), 0.1f);
            // Last note off at tick 920 (0.958 s at 120 BPM) plus 2 s for the notes to ring out.
            long expectedFrames = (long) ((920 / 480.0 * 0.5 + 2.0) * 44100);
            assertEquals(expectedFrames, source.frameLength(), 44100 / 50);
            byte[] buffer = new byte[8192];
            int max = 0;
            long total = 0;
            int n;
            while ((n = source.read(buffer, 0, buffer.length)) >= 0) {
                total += n;
                for (int i = 0; i + 1 < n; i += 2) max = Math.max(max, Math.abs((short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8))));
            }
            assertEquals(source.frameLength() * 4, total);
            assertTrue(max > 1000, "the notes are audible: " + max);
        }
    }
}

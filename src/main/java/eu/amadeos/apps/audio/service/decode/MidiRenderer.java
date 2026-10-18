package eu.amadeos.apps.audio.service.decode;

import eu.amadeos.apps.audio.service.engine.AudioInputStreamSource;
import eu.amadeos.apps.audio.service.engine.PcmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Renders MIDI files to PCM with the JDK's software synthesizer (Gervill), so MIDI goes through the same
 * mixer channel, meters and recording as every other format. Uses a General MIDI SoundFont from the
 * system if there is one (Debian {@code fluid-soundfont-gm}), otherwise Gervill's built-in sounds.
 *
 * <p>Gervill's streaming interface {@code com.sun.media.sound.AudioSynthesizer} is not exported by
 * {@code java.desktop}; the launcher and the jar manifest export it.
 */
public final class MidiRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(MidiRenderer.class);
    static final AudioFormat FORMAT = PcmSource.pcm16(44100f, 2);
    /** Let the last notes ring out. */
    private static final long TAIL_MICROS = 2_000_000;
    private static final List<Path> SOUNDFONTS = List.of(
            Path.of("/usr/share/sounds/sf2/FluidR3_GM.sf2"),
            Path.of("/usr/share/sounds/sf2/default-GM.sf2"),
            Path.of("/usr/share/soundfonts/default.sf2"),
            Path.of("/usr/share/sounds/sf2/TimGM6mb.sf2"));

    private final List<Path> soundfonts;
    private volatile Soundbank cachedBank;
    private volatile boolean bankLoaded;

    public MidiRenderer() {
        this(SOUNDFONTS);
    }

    MidiRenderer(List<Path> soundfonts) {
        this.soundfonts = soundfonts;
    }

    public PcmSource open(Path file) throws IOException {
        Sequence sequence;
        try {
            sequence = MidiSystem.getSequence(file.toFile());
        } catch (InvalidMidiDataException e) {
            throw new IOException("Not a valid MIDI file: " + file.getFileName(), e);
        }
        List<TempoMap.TimedEvent> events = TempoMap.timed(sequence);
        long endMicros = (events.isEmpty() ? 0 : events.get(events.size() - 1).micros()) + TAIL_MICROS;
        Synthesizer synth;
        AudioInputStream stream;
        try {
            synth = MidiSystem.getSynthesizer();
            stream = openStream(synth);
        } catch (MidiUnavailableException e) {
            throw new IOException("No MIDI synthesizer: " + e.getMessage(), e);
        }
        Soundbank bank = soundbank();
        if (bank != null) {
            Soundbank builtIn = synth.getDefaultSoundbank();
            if (builtIn != null) synth.unloadAllInstruments(builtIn);
            synth.loadAllInstruments(bank);
        }
        try {
            Receiver receiver = synth.getReceiver();
            for (TempoMap.TimedEvent timed : events) {
                MidiMessage message = timed.event().getMessage();
                if (message instanceof MetaMessage) continue;
                receiver.send(message, timed.micros());
            }
        } catch (MidiUnavailableException e) {
            synth.close();
            throw new IOException("MIDI synthesizer not available: " + e.getMessage(), e);
        }
        long frames = endMicros * (long) FORMAT.getSampleRate() / 1_000_000L;
        AudioInputStream limited = new AudioInputStream(stream, FORMAT, frames);
        return new AudioInputStreamSource(limited, FORMAT, frames) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    synth.close();
                }
            }
        };
    }

    /** Opens Gervill in streaming mode through its non-exported interface. */
    private static AudioInputStream openStream(Synthesizer synth) throws IOException, MidiUnavailableException {
        try {
            Class<?> audioSynthesizer = Class.forName("com.sun.media.sound.AudioSynthesizer");
            if (!audioSynthesizer.isInstance(synth)) {
                throw new IOException("The MIDI synthesizer " + synth.getDeviceInfo().getName() + " cannot render to a stream");
            }
            Method open = audioSynthesizer.getMethod("openStream", AudioFormat.class, Map.class);
            return (AudioInputStream) open.invoke(synth, FORMAT, null);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof MidiUnavailableException mue) throw mue;
            throw new IOException("Cannot start the MIDI synthesizer: " + e.getCause(), e);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException("Cannot use the MIDI synthesizer (start with "
                    + "--add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED): " + e, e);
        }
    }

    private Soundbank soundbank() {
        if (bankLoaded) return cachedBank;
        synchronized (this) {
            if (!bankLoaded) {
                for (Path candidate : soundfonts) {
                    if (!Files.isRegularFile(candidate)) continue;
                    try {
                        cachedBank = MidiSystem.getSoundbank(candidate.toFile());
                        LOG.info("MIDI SoundFont: {}", candidate);
                        break;
                    } catch (InvalidMidiDataException | IOException e) {
                        LOG.warn("Cannot load SoundFont {}: {}", candidate, e.getMessage());
                    }
                }
                bankLoaded = true;
            }
            return cachedBank;
        }
    }
}

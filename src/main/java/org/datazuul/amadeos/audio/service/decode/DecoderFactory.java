package org.datazuul.amadeos.audio.service.decode;

import org.datazuul.amadeos.audio.domain.PlaylistEntry;
import org.datazuul.amadeos.audio.service.engine.AudioInputStreamSource;
import org.datazuul.amadeos.audio.service.engine.PcmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Opens files and network streams as 16-bit PCM. MP3, Ogg Vorbis, AAC and FLAC come from the Java Sound
 * service providers on the class path (mp3spi, vorbisspi, javasound-aac, jflac-codec), WAV/AIFF/AU from
 * the JDK, MIDI from {@link MidiRenderer}.
 */
public final class DecoderFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DecoderFactory.class);

    /** Container/codec of a network stream, as detected by {@link StreamSniffer}. */
    public enum StreamCodec {
        MP3("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader"),
        AAC("net.sourceforge.jaad.spi.javasound.AACAudioFileReader"),
        OGG("javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader");

        private final String readerClass;

        StreamCodec(String readerClass) {
            this.readerClass = readerClass;
        }

        /** File extension for recordings. */
        public String extension() {
            return name().toLowerCase();
        }
    }

    private final MidiRenderer midi;

    public DecoderFactory() {
        this(new MidiRenderer());
    }

    public DecoderFactory(MidiRenderer midi) {
        this.midi = midi;
    }

    /** Opens a local file; MIDI files are rendered with the software synthesizer. */
    public PcmSource openFile(Path file) throws IOException {
        withDecoderClassLoader();
        if (PlaylistEntry.formatOf(file).equals("MIDI")) return midi.open(file);
        try {
            AudioInputStream encoded = AudioSystem.getAudioInputStream(file.toFile());
            return new AudioInputStreamSource(toPcm16(encoded));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio format: " + file.getFileName(), e);
        }
    }

    /**
     * Decodes a network stream whose codec was detected beforehand.
     *
     * @param heAacHint the server or station says {@code aacp}: an AAC stream reported at 24 kHz or less
     *                  then plays at twice the rate (SBR), as dz-radio does
     */
    public PcmSource openStream(InputStream in, StreamCodec codec, boolean heAacHint) throws IOException {
        withDecoderClassLoader();
        AudioFileReader reader;
        try {
            reader = (AudioFileReader) Class.forName(codec.readerClass).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IOException("No decoder for " + codec + " streams", e);
        }
        try {
            AudioInputStream encoded = reader.getAudioInputStream(new BufferedInputStream(in, 65536));
            AudioInputStream decoded = toPcm16(encoded);
            AudioFormat format = decoded.getFormat();
            float rate = format.getSampleRate();
            if (codec == StreamCodec.AAC && (rate < 32000f || (heAacHint && rate <= 24000f))) {
                float corrected = rate * 2f;
                LOG.info("HE-AAC stream: playing at {} Hz instead of {} Hz", corrected, rate);
                return new AudioInputStreamSource(decoded, PcmSource.pcm16(corrected, format.getChannels()), -1);
            }
            return new AudioInputStreamSource(decoded);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Cannot decode the " + codec + " stream", e);
        }
    }

    /** Converts to signed 16-bit little-endian PCM at the source's rate and channel count. */
    static AudioInputStream toPcm16(AudioInputStream encoded) {
        AudioFormat base = encoded.getFormat();
        float rate = base.getSampleRate() > 0 ? base.getSampleRate() : 44100f;
        int channels = base.getChannels() > 0 ? base.getChannels() : 2;
        AudioFormat target = PcmSource.pcm16(rate, channels);
        if (base.matches(target)) return encoded;
        return AudioSystem.getAudioInputStream(target, encoded);
    }

    /** The service providers are found through the context class loader, which virtual threads may lack. */
    private static void withDecoderClassLoader() {
        Thread.currentThread().setContextClassLoader(DecoderFactory.class.getClassLoader());
    }
}

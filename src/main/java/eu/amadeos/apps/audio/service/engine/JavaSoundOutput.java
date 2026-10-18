package eu.amadeos.apps.audio.service.engine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;

/** {@link PcmOutput} on a Java Sound {@link SourceDataLine} of the default mixer (PipeWire/PulseAudio via ALSA). */
public final class JavaSoundOutput implements PcmOutput {
    /** About 0.2 s at 44.1 kHz stereo: short enough for responsive meters, long enough to avoid drop-outs. */
    private static final int BUFFER_BYTES = 32768;
    private SourceDataLine line;

    @Override
    public void open(AudioFormat format) throws IOException {
        try {
            line = defaultDeviceLine(format);
            line.open(format, Math.max(BUFFER_BYTES, format.getFrameSize() * 4096));
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IOException("No audio output for " + format + ": " + e.getMessage(), e);
        }
    }

    /**
     * The line of ALSA's {@code default} device, which leads to PipeWire or PulseAudio, so the system's
     * master volume and output selection apply; Java Sound's own default if there is none.
     */
    private static SourceDataLine defaultDeviceLine(AudioFormat format) throws LineUnavailableException {
        javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(SourceDataLine.class, format);
        for (javax.sound.sampled.Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            String name = mixerInfo.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.startsWith("default") && !name.contains("pipewire") && !name.contains("pulse")) continue;
            javax.sound.sampled.Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.isLineSupported(info)) return (SourceDataLine) mixer.getLine(info);
        }
        return AudioSystem.getSourceDataLine(format);
    }

    @Override
    public int write(byte[] buffer, int offset, int length) {
        return line.write(buffer, offset, length);
    }

    @Override
    public void start() {
        line.start();
    }

    @Override
    public void stop() {
        line.stop();
    }

    @Override
    public void flush() {
        line.flush();
    }

    @Override
    public void drain() {
        line.drain();
    }

    @Override
    public long playedMicros() {
        return line == null ? 0 : line.getMicrosecondPosition();
    }

    @Override
    public void close() {
        if (line != null) {
            line.close();
            line = null;
        }
    }
}

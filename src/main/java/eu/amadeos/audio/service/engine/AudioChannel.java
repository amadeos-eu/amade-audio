package eu.amadeos.audio.service.engine;

import eu.amadeos.audio.domain.AudioLevels;
import eu.amadeos.audio.domain.SourceId;
import eu.amadeos.audio.domain.TransportState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * The output path of one device: plays a {@link PcmSource} on its own virtual thread and applies the
 * device's fader, the mixer's mute and tone control on the way. Listeners are called on the audio
 * thread; Swing code must switch to the EDT itself.
 */
public final class AudioChannel {
    private static final Logger LOG = LoggerFactory.getLogger(AudioChannel.class);

    /** Events of the channel. */
    public interface Listener {
        default void stateChanged(SourceId source, TransportState state, String message) {
        }

        /** The source ended by itself (not by {@link #stop()}); e.g. go to the next track. */
        default void finished(SourceId source) {
        }

        default void levels(SourceId source, AudioLevels levels) {
        }
    }

    private final SourceId id;
    private final PcmOutput.Factory outputs;
    private final ToneControl tone;
    private final BooleanSupplier muted;
    private final DoubleSupplier master;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile float volume = 0.8f;
    private Playback current;
    private volatile TransportState state = TransportState.STOPPED;

    AudioChannel(SourceId id, PcmOutput.Factory outputs, ToneControl tone, BooleanSupplier muted, DoubleSupplier master) {
        this.id = id;
        this.outputs = outputs;
        this.tone = tone;
        this.muted = muted;
        this.master = master;
    }

    public SourceId id() {
        return id;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Fader position 0..1. */
    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
    }

    public float volume() {
        return volume;
    }

    public TransportState state() {
        return state;
    }

    /**
     * Stops what is playing and plays the source; the channel closes it when done.
     *
     * @param startMicros position the source starts at, for the position display after seeking
     */
    public synchronized void play(PcmSource source, long startMicros) {
        stopCurrent();
        Playback playback = new Playback(source, startMicros);
        current = playback;
        fireState(playback, TransportState.LOADING, null);
        Thread.ofVirtual().name("amade-audio-" + id.name().toLowerCase()).start(playback);
    }

    /** Pauses; the output keeps its queued samples. */
    public synchronized void pause() {
        if (current != null) current.setPaused(true);
    }

    public synchronized void resume() {
        if (current != null) current.setPaused(false);
    }

    public synchronized boolean isPaused() {
        return current != null && current.paused;
    }

    public synchronized void stop() {
        Playback old = current;
        stopCurrent();
        if (old != null) {
            state = TransportState.STOPPED;
            for (Listener l : listeners) l.stateChanged(id, TransportState.STOPPED, null);
            for (Listener l : listeners) l.levels(id, AudioLevels.SILENT);
        }
    }

    /** Position of what is audible now, in microseconds from the start of the source. */
    public synchronized long positionMicros() {
        return current == null ? 0 : current.position();
    }

    private void stopCurrent() {
        if (current != null) {
            current.cancel();
            current = null;
        }
    }

    private synchronized boolean isCurrent(Playback playback) {
        return current == playback;
    }

    private void fireState(Playback playback, TransportState newState, String message) {
        if (!isCurrent(playback)) return;
        state = newState;
        for (Listener l : listeners) {
            try {
                l.stateChanged(id, newState, message);
            } catch (RuntimeException e) {
                LOG.warn("Listener failed", e);
            }
        }
    }

    private void fireLevels(Playback playback, AudioLevels levels) {
        if (!isCurrent(playback)) return;
        for (Listener l : listeners) l.levels(id, levels);
    }

    private void fireFinished(Playback playback) {
        synchronized (this) {
            if (current != playback) return;
            current = null;
            state = TransportState.STOPPED;
        }
        for (Listener l : listeners) l.stateChanged(id, TransportState.STOPPED, null);
        for (Listener l : listeners) l.levels(id, AudioLevels.SILENT);
        for (Listener l : listeners) {
            try {
                l.finished(id);
            } catch (RuntimeException e) {
                LOG.warn("Listener failed", e);
            }
        }
    }

    /** Amplitude factor for a fader position: roughly logarithmic, 0.5 is about -12 dB. */
    static float amplitude(float fader) {
        return fader * fader;
    }

    /** Multiplies 16-bit little-endian samples by the factor, clipping at full scale. */
    static void applyGain(byte[] buffer, int offset, int length, float factor) {
        if (factor >= 0.999f && factor <= 1.001f) return;
        for (int i = offset; i + 1 < offset + length; i += 2) {
            int s = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            int v = Math.round(s * factor);
            if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
            if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
            buffer[i] = (byte) v;
            buffer[i + 1] = (byte) (v >> 8);
        }
    }

    /** Keeps the first two channels of each frame; returns the new length. */
    static int downmixToStereo(byte[] in, int length, int channels, byte[] out) {
        int inFrame = channels * 2;
        int frames = length / inFrame;
        for (int f = 0; f < frames; f++) {
            System.arraycopy(in, f * inFrame, out, f * 4, 4);
        }
        return frames * 4;
    }

    private final class Playback implements Runnable {
        private final PcmSource source;
        private final long startMicros;
        private final Object lock = new Object();
        private final LevelAnalyzer analyzer = new LevelAnalyzer();
        private volatile PcmOutput output;
        private volatile boolean cancelled;
        private volatile boolean paused;

        Playback(PcmSource source, long startMicros) {
            this.source = source;
            this.startMicros = startMicros;
        }

        long position() {
            PcmOutput o = output;
            return startMicros + (o == null ? 0 : o.playedMicros());
        }

        void setPaused(boolean value) {
            synchronized (lock) {
                if (paused == value) return;
                paused = value;
                PcmOutput o = output;
                if (o != null) {
                    if (value) o.stop();
                    else o.start();
                }
                lock.notifyAll();
            }
            fireState(this, value ? TransportState.PAUSED : TransportState.PLAYING, null);
        }

        void cancel() {
            cancelled = true;
            synchronized (lock) {
                lock.notifyAll();
            }
            PcmOutput o = output;
            if (o != null) {
                try {
                    o.flush();
                    o.close(); // unblocks a write waiting for buffer space
                } catch (RuntimeException e) {
                    LOG.debug("Closing output failed", e);
                }
            }
            try {
                source.close(); // unblocks a network read
            } catch (IOException | RuntimeException e) {
                LOG.debug("Closing source failed", e);
            }
        }

        @Override
        public void run() {
            boolean ended = false;
            String error = null;
            PcmOutput out = outputs.create();
            try {
                AudioFormat in = source.format();
                int channels = in.getChannels();
                AudioFormat format = channels > 2 ? PcmSource.pcm16(in.getSampleRate(), 2) : in;
                if (cancelled) return;
                out.open(format);
                output = out;
                if (cancelled) return;
                out.start();
                fireState(this, TransportState.PLAYING, null);
                byte[] buffer = new byte[in.getFrameSize() * 2048];
                byte[] stereo = channels > 2 ? new byte[4 * 2048] : null;
                while (!cancelled) {
                    synchronized (lock) {
                        while (paused && !cancelled) lock.wait();
                    }
                    if (cancelled) break;
                    int n = source.read(buffer, 0, buffer.length);
                    if (n < 0) {
                        if (!cancelled) out.drain();
                        ended = !cancelled;
                        break;
                    }
                    if (n == 0) continue;
                    byte[] data = buffer;
                    int length = n;
                    if (stereo != null) {
                        length = downmixToStereo(buffer, n, channels, stereo);
                        data = stereo;
                    }
                    applyGain(data, 0, length, muted.getAsBoolean() ? 0f : amplitude(volume) * amplitude((float) master.getAsDouble()));
                    tone.process(data, 0, length, format.getSampleRate(), format.getChannels());
                    var levels = analyzer.process(data, 0, length, format);
                    if (levels != null) fireLevels(this, levels);
                    int written = 0;
                    while (written < length && !cancelled) {
                        int w = out.write(data, written, length - written);
                        if (w <= 0) break;
                        written += w;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                if (!cancelled) {
                    LOG.warn("{} playback failed", id, e);
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                }
            } finally {
                output = null;
                try {
                    out.close();
                } catch (RuntimeException e) {
                    LOG.debug("Closing output failed", e);
                }
                try {
                    source.close();
                } catch (IOException | RuntimeException e) {
                    LOG.debug("Closing source failed", e);
                }
            }
            if (error != null) {
                fireLevels(this, AudioLevels.SILENT);
                fireState(this, TransportState.ERROR, error);
                synchronized (AudioChannel.this) {
                    if (current == this) current = null;
                }
            } else if (ended) {
                fireFinished(this);
            }
        }
    }
}

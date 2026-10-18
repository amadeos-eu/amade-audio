package eu.amadeos.apps.audio.service.engine;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/** Where a channel's samples go; normally a Java Sound line. */
public interface PcmOutput extends AutoCloseable {
    void open(AudioFormat format) throws IOException;

    /** Blocks until the samples are queued; returns the bytes written. */
    int write(byte[] buffer, int offset, int length);

    void start();

    void stop();

    /** Discards queued samples, e.g. on stop or seek. */
    void flush();

    /** Waits until queued samples have been played. */
    void drain();

    /** Microseconds played since {@link #open}. */
    long playedMicros();

    @Override
    void close();

    /** Creates outputs; one per playback. */
    interface Factory {
        PcmOutput create();
    }
}

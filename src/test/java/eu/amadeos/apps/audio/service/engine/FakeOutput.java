package eu.amadeos.apps.audio.service.engine;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.util.function.BooleanSupplier;

/** Collects what a channel writes; optionally plays in real time (slowly) so tests can catch it playing. */
public final class FakeOutput implements PcmOutput {
    public final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private final boolean throttled;
    public AudioFormat format;
    private long frames;

    FakeOutput(boolean throttled) {
        this.throttled = throttled;
    }

    @Override
    public void open(AudioFormat f) {
        format = f;
    }

    @Override
    public synchronized int write(byte[] buffer, int offset, int length) {
        written.write(buffer, offset, length);
        frames += length / format.getFrameSize();
        if (throttled) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return length;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void flush() {
    }

    @Override
    public void drain() {
    }

    @Override
    public synchronized long playedMicros() {
        return format == null ? 0 : (long) (frames * 1_000_000L / format.getSampleRate());
    }

    @Override
    public void close() {
    }

    /** Remembers the outputs it created. */
    public static final class Factory implements PcmOutput.Factory {
        private final boolean throttled;
        public final java.util.List<FakeOutput> created = new java.util.concurrent.CopyOnWriteArrayList<>();

        public Factory(boolean throttled) {
            this.throttled = throttled;
        }

        @Override
        public PcmOutput create() {
            FakeOutput o = new FakeOutput(throttled);
            created.add(o);
            return o;
        }
    }

    /** Waits up to 5 s for the condition. */
    public static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) throw new AssertionError("Timed out");
            Thread.sleep(10);
        }
    }
}

package eu.amadeos.apps.audio.service.engine;

import eu.amadeos.apps.audio.domain.AudioLevels;
import eu.amadeos.apps.audio.domain.SourceId;
import eu.amadeos.apps.audio.domain.TransportState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AudioChannelTest {
    @Test
    void playsToTheEndAndReportsFinished() throws Exception {
        FakeOutput.Factory outputs = new FakeOutput.Factory(false);
        AudioEngine engine = new AudioEngine(outputs);
        AudioChannel channel = engine.channel(SourceId.PLAYER);
        channel.setVolume(1f);
        List<TransportState> states = new CopyOnWriteArrayList<>();
        AtomicInteger finished = new AtomicInteger();
        List<AudioLevels> levels = new CopyOnWriteArrayList<>();
        channel.addListener(new AudioChannel.Listener() {
            @Override
            public void stateChanged(SourceId source, TransportState state, String message) {
                states.add(state);
            }

            @Override
            public void finished(SourceId source) {
                finished.incrementAndGet();
            }

            @Override
            public void levels(SourceId source, AudioLevels l) {
                levels.add(l);
            }
        });
        SineSource sine = new SineSource(1000, 0.5);
        channel.play(sine, 0);
        FakeOutput.await(() -> finished.get() == 1);

        assertEquals(List.of(TransportState.LOADING, TransportState.PLAYING, TransportState.STOPPED), states);
        assertEquals(TransportState.STOPPED, channel.state());
        assertTrue(sine.closed);
        assertEquals(22050 * 4, outputs.created.get(0).written.size());
        AudioLevels loud = levels.stream().filter(l -> l.left() > 0).findFirst().orElseThrow();
        // -6 dBFS sine on a -40..0 dB scale.
        assertEquals(0.85, loud.left(), 0.03);
    }

    @Test
    void appliesTheFaderAndMute() throws Exception {
        FakeOutput.Factory outputs = new FakeOutput.Factory(false);
        AudioEngine engine = new AudioEngine(outputs);
        AudioChannel channel = engine.channel(SourceId.CD);
        channel.setVolume(0.5f);
        AtomicInteger finished = new AtomicInteger();
        channel.addListener(new AudioChannel.Listener() {
            @Override
            public void finished(SourceId source) {
                finished.incrementAndGet();
            }
        });
        channel.play(new SineSource(1000, 0.1), 0);
        FakeOutput.await(() -> finished.get() == 1);
        assertEquals(16384 / 4, maxSample(outputs.created.get(0).written.toByteArray()), 2);

        engine.setMasterVolume(0.5f);
        channel.setVolume(1f);
        channel.play(new SineSource(1000, 0.1), 0);
        FakeOutput.await(() -> finished.get() == 2);
        assertEquals(16384 / 4, maxSample(outputs.created.get(1).written.toByteArray()), 2);

        engine.setMasterVolume(1f);
        engine.setMuted(true);
        channel.play(new SineSource(1000, 0.1), 0);
        FakeOutput.await(() -> finished.get() == 3);
        assertEquals(0, maxSample(outputs.created.get(2).written.toByteArray()));
    }

    // --8<-- [start:stop-not-finished]
    @Test
    void stopIsNotFinished() throws Exception {
        FakeOutput.Factory outputs = new FakeOutput.Factory(true);
        AudioEngine engine = new AudioEngine(outputs);
        AudioChannel channel = engine.channel(SourceId.WEBRADIO);
        AtomicInteger finished = new AtomicInteger();
        channel.addListener(new AudioChannel.Listener() {
            @Override
            public void finished(SourceId source) {
                finished.incrementAndGet();
            }
        });
        SineSource sine = new SineSource(440, 60);
        channel.play(sine, 0);
        FakeOutput.await(() -> channel.state() == TransportState.PLAYING);
        channel.pause();
        assertTrue(channel.isPaused());
        assertEquals(TransportState.PAUSED, channel.state());
        channel.resume();
        channel.stop();
        FakeOutput.await(() -> sine.closed);
        Thread.sleep(100);
        assertEquals(0, finished.get());
        assertEquals(TransportState.STOPPED, channel.state());
    }
    // --8<-- [end:stop-not-finished]

    @Test
    void reportsErrors() throws Exception {
        AudioEngine engine = new AudioEngine(new FakeOutput.Factory(false));
        AudioChannel channel = engine.channel(SourceId.PLAYER);
        List<String> errors = new CopyOnWriteArrayList<>();
        channel.addListener(new AudioChannel.Listener() {
            @Override
            public void stateChanged(SourceId source, TransportState state, String message) {
                if (state == TransportState.ERROR) errors.add(message);
            }
        });
        channel.play(new PcmSource() {
            @Override
            public javax.sound.sampled.AudioFormat format() {
                return PcmSource.pcm16(44100, 2);
            }

            @Override
            public int read(byte[] b, int o, int l) throws java.io.IOException {
                throw new java.io.IOException("broken file");
            }

            @Override
            public void close() {
            }
        }, 0);
        FakeOutput.await(() -> !errors.isEmpty());
        assertEquals("broken file", errors.get(0));
    }

    @Test
    void downmixKeepsTheFirstTwoChannels() {
        byte[] in = {1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0};
        byte[] out = new byte[8];
        assertEquals(4, AudioChannel.downmixToStereo(in, 12, 6, out));
        assertEquals(1, out[0]);
        assertEquals(2, out[2]);
    }

    @Test
    void gainClips() {
        byte[] b = {(byte) 0xFF, 0x7F};
        AudioChannel.applyGain(b, 0, 2, 2f);
        assertEquals(Short.MAX_VALUE, (short) ((b[0] & 0xFF) | (b[1] << 8)));
        assertEquals(0.25f, AudioChannel.amplitude(0.5f), 0.0001f);
    }

    private static int maxSample(byte[] pcm) {
        int max = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) max = Math.max(max, Math.abs((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8))));
        return max;
    }
}

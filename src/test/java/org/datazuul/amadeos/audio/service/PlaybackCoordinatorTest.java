package org.datazuul.amadeos.audio.service;

import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.audio.domain.TransportState;
import org.datazuul.amadeos.audio.service.engine.AudioEngine;
import org.datazuul.amadeos.audio.service.engine.FakeOutput;
import org.datazuul.amadeos.audio.service.engine.SineSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackCoordinatorTest {
    @Test
    void activatingADeviceStopsTheOthers() throws Exception {
        FakeOutput.Factory outputs = new FakeOutput.Factory(true);
        AudioEngine engine = new AudioEngine(outputs);
        PlaybackCoordinator coordinator = new PlaybackCoordinator(engine);
        List<SourceId> changes = new ArrayList<>();
        coordinator.addListener(changes::add);

        engine.channel(SourceId.PLAYER).play(new SineSource(440, 60), 0);
        coordinator.activate(SourceId.PLAYER);
        FakeOutput.await(() -> engine.channel(SourceId.PLAYER).state() == TransportState.PLAYING);
        coordinator.activate(SourceId.WEBRADIO);

        assertEquals(TransportState.STOPPED, engine.channel(SourceId.PLAYER).state());
        assertEquals(SourceId.WEBRADIO, coordinator.active());
        assertEquals(List.of(SourceId.PLAYER, SourceId.WEBRADIO), changes);
    }

    @Test
    void selectDoesNotInterruptAPlayingDevice() throws Exception {
        AudioEngine engine = new AudioEngine(new FakeOutput.Factory(true));
        PlaybackCoordinator coordinator = new PlaybackCoordinator(engine);
        coordinator.select(SourceId.CD);
        assertEquals(SourceId.CD, coordinator.active());
        engine.channel(SourceId.CD).play(new SineSource(440, 60), 0);
        FakeOutput.await(() -> engine.channel(SourceId.CD).state() == TransportState.PLAYING);
        coordinator.select(SourceId.PLAYER);
        assertEquals(SourceId.CD, coordinator.active());
        coordinator.deactivate(SourceId.CD);
        assertNull(coordinator.active());
        engine.stopAll();
    }
}

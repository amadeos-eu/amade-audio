package org.datazuul.amadeos.audio.service;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.audio.service.engine.AudioEngine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Which device is active: the one that was last told to play. Activating a device stops the others,
 * and the playlist panel shows the active device's list.
 */
public final class PlaybackCoordinator {
    private final AudioEngine engine;
    private final List<Consumer<SourceId>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SourceId>> listRequests = new CopyOnWriteArrayList<>();
    private volatile SourceId active;

    @Inject
    public PlaybackCoordinator(AudioEngine engine) {
        this.engine = engine;
    }

    /** The active device, or null if none was used yet or it was switched off. */
    public SourceId active() {
        return active;
    }

    /** Called with the new active device (or null); on the thread that changed it. */
    public void addListener(Consumer<SourceId> listener) {
        listeners.add(listener);
    }

    /** Makes the device active and stops the others. */
    public void activate(SourceId id) {
        for (SourceId other : SourceId.values()) {
            if (other != id && engine.channel(other).state().isActive()) engine.channel(other).stop();
        }
        if (active != id) {
            active = id;
            listeners.forEach(l -> l.accept(id));
        }
    }

    /** Shows a device's list without playing, e.g. when a CD is inserted and nothing else plays. */
    public void select(SourceId id) {
        if (active != id && (active == null || !engine.channel(active).state().isActive())) {
            active = id;
            listeners.forEach(l -> l.accept(id));
        }
    }

    /** Called when a device asks to show its list without becoming active. */
    public void addListRequestListener(Consumer<SourceId> listener) {
        listRequests.add(listener);
    }

    /**
     * Shows the device's list in the playlist panel. If nothing plays, the device also becomes active;
     * otherwise the playing device stays active and keeps playing.
     */
    public void showList(SourceId id) {
        if (active == null || !engine.channel(active).state().isActive()) select(id);
        listRequests.forEach(l -> l.accept(id));
    }

    /** The device was switched off: if it was active, nothing is. */
    public void deactivate(SourceId id) {
        if (active == id) {
            active = null;
            listeners.forEach(l -> l.accept(null));
        }
    }
}

package eu.amadeos.audio.ui.rack.mixer;

import static eu.amadeos.audio.i18n.Texts.tr;

import com.google.inject.Inject;
import eu.amadeos.audio.domain.AudioPreferences;
import eu.amadeos.audio.domain.OutputDevice;
import eu.amadeos.audio.domain.SourceId;
import eu.amadeos.audio.repository.PreferencesStore;
import eu.amadeos.audio.service.engine.AudioEngine;
import eu.amadeos.audio.service.engine.ToneControl;
import eu.amadeos.audio.service.mixer.SoundServer;
import eu.amadeos.audio.ui.widget.Fader;
import eu.amadeos.audio.ui.widget.Ticker;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.*;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects the mixer rack: device faders and tone go to the audio engine (and are saved), master,
 * microphone and output to the sound server. The server's values are read again every few seconds,
 * so changes made elsewhere show up.
 */
public final class MixerPresenter {
    /** Master fader position until the sound server reports its volume (or for good, without one). */
    static final float DEFAULT_MASTER = 0.4f;

    private final AudioEngine engine;
    private final SoundServer server;
    private final PreferencesStore preferences;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final MixerRack rack = new MixerRack();
    private final AtomicBoolean polling = new AtomicBoolean();
    private List<OutputDevice> outputs = List.of();
    private volatile double pendingMaster = -1;
    private volatile double pendingMic = -1;
    /** Null until known: does the master fader control the sound server or only this application? */
    private volatile Boolean systemMaster;

    @Inject
    public MixerPresenter(AudioEngine engine, SoundServer server, PreferencesStore preferences, ExecutorService executor,
                          EdtExecutor edt) {
        this.engine = engine;
        this.server = server;
        this.preferences = preferences;
        this.executor = executor;
        this.edt = edt;
    }

    public JComponent rack() {
        return rack;
    }

    public void start() {
        AudioPreferences prefs = preferences.current();
        for (SourceId id : SourceId.values()) {
            Fader fader = rack.faders.get(id);
            fader.setValueQuietly(prefs.volume(id));
            engine.channel(id).setVolume(prefs.volume(id));
            fader.addChangeListener(e -> {
                engine.channel(id).setVolume(fader.value());
                if (!fader.isAdjusting()) preferences.update(p -> p.withVolume(id, fader.value()));
            });
        }
        rack.bass.setValueQuietly(toFader(prefs.bassDb()));
        rack.treble.setValueQuietly(toFader(prefs.trebleDb()));
        engine.tone().set(prefs.bassDb(), prefs.trebleDb());
        rack.bass.addChangeListener(e -> toneChanged());
        rack.treble.addChangeListener(e -> toneChanged());
        rack.mute.addActionListener(e -> engine.setMuted(rack.mute.isSelected()));
        rack.master.setValueQuietly(DEFAULT_MASTER);
        rack.master.addChangeListener(e -> {
            if (Boolean.FALSE.equals(systemMaster)) {
                engine.setMasterVolume(rack.master.value());
                return;
            }
            pendingMaster = rack.master.value();
            executor.execute(this::pushVolumes);
        });
        rack.microphone.addChangeListener(e -> {
            pendingMic = rack.microphone.value();
            executor.execute(this::pushVolumes);
        });
        poll();
        Ticker.every(3000, this::poll);
    }

    /** Master as a gain in the audio engine, for systems without wpctl/pactl. */
    private void useOwnMaster(float value) {
        systemMaster = false;
        edt.execute(() -> {
            rack.master.setToolTipText(tr("mixer.master.own"));
            float v = value >= 0 ? value : rack.master.value();
            engine.setMasterVolume(v);
        });
    }

    private void toneChanged() {
        double bass = toDb(rack.bass.value());
        double treble = toDb(rack.treble.value());
        engine.tone().set(bass, treble);
        if (!rack.bass.isAdjusting() && !rack.treble.isAdjusting()) preferences.update(p -> p.withTone(bass, treble));
    }

    /** Sends the latest master/microphone values; coalesces fast fader moves. */
    private synchronized void pushVolumes() {
        double m = pendingMaster;
        pendingMaster = -1;
        if (m >= 0 && !server.setMasterVolume(m)) {
            // The sound server cannot be controlled: the fader becomes this application's master.
            useOwnMaster((float) m);
        }
        double mic = pendingMic;
        pendingMic = -1;
        if (mic >= 0) server.setMicrophoneVolume(mic);
    }

    private void poll() {
        if (!polling.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                OptionalDouble master = server.masterVolume();
                OptionalDouble mic = server.microphoneVolume();
                List<OutputDevice> devices = server.outputs();
                if (master.isEmpty() && !Boolean.FALSE.equals(systemMaster)) {
                    useOwnMaster(-1);
                }
                edt.execute(() -> {
                    if (master.isPresent()) {
                        if (!Boolean.TRUE.equals(systemMaster)) {
                            systemMaster = true;
                            engine.setMasterVolume(1f);
                            rack.master.setToolTipText(tr("mixer.master.system", server.backend().name().toLowerCase()));
                        }
                        if (!rack.master.isAdjusting() && pendingMaster < 0) {
                            rack.master.setValueQuietly((float) master.getAsDouble());
                        }
                    }
                    if (mic.isPresent() && !rack.microphone.isAdjusting() && pendingMic < 0) {
                        rack.microphone.setValueQuietly((float) mic.getAsDouble());
                    }
                    rack.microphone.setEnabled(mic.isPresent());
                    showOutputs(devices);
                });
            } finally {
                polling.set(false);
            }
        });
    }

    private void showOutputs(List<OutputDevice> devices) {
        if (devices.equals(outputs)) return;
        outputs = devices;
        rack.outputs.removeAll();
        for (AbstractButton b : java.util.Collections.list(rack.outputGroup.getElements())) rack.outputGroup.remove(b);
        if (devices.isEmpty()) {
            rack.outputs.add(new JLabel(tr("mixer.no-sound-server")));
        }
        for (OutputDevice d : devices.subList(0, Math.min(4, devices.size()))) {
            String text = d.description().length() > 30 ? d.description().substring(0, 29) + "…" : d.description();
            JRadioButton b = new JRadioButton(text, d.isDefault());
            b.setToolTipText(d.description());
            b.setOpaque(false);
            b.addActionListener(e -> executor.execute(() -> {
                server.setDefaultOutput(d);
                poll();
            }));
            rack.outputGroup.add(b);
            rack.outputs.add(b);
        }
        rack.outputs.revalidate();
        rack.outputs.repaint();
    }

    static float toFader(double db) {
        return (float) ((db + ToneControl.MAX_DB) / (2 * ToneControl.MAX_DB));
    }

    static double toDb(float fader) {
        double db = fader * 2 * ToneControl.MAX_DB - ToneControl.MAX_DB;
        return Math.abs(db) < 0.75 ? 0 : db; // snap to the middle
    }
}

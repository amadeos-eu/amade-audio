package org.datazuul.amadeos.audio.service.engine;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.SourceId;

import java.util.EnumMap;
import java.util.Map;

/**
 * One {@link AudioChannel} per device, the mixer's mute and the tone control they share.
 */
public final class AudioEngine {
    private final Map<SourceId, AudioChannel> channels = new EnumMap<>(SourceId.class);
    private final ToneControl tone = new ToneControl();
    private volatile boolean muted;
    private volatile float master = 1f;

    @Inject
    public AudioEngine() {
        this(JavaSoundOutput::new);
    }

    public AudioEngine(PcmOutput.Factory outputs) {
        for (SourceId id : SourceId.values()) {
            channels.put(id, new AudioChannel(id, outputs, tone, () -> muted, () -> master));
        }
    }

    public AudioChannel channel(SourceId id) {
        return channels.get(id);
    }

    public ToneControl tone() {
        return tone;
    }

    public void setMuted(boolean value) {
        muted = value;
    }

    public boolean isMuted() {
        return muted;
    }

    /**
     * Master fader of the application (0..1), used when the sound server's master volume cannot be
     * controlled; 1 otherwise.
     */
    public void setMasterVolume(float value) {
        master = Math.max(0f, Math.min(1f, value));
    }

    public float masterVolume() {
        return master;
    }

    /** Stops every device, e.g. on exit. */
    public void stopAll() {
        channels.values().forEach(AudioChannel::stop);
    }
}

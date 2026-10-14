package org.datazuul.amadeos.audio.service.mixer;

import org.datazuul.amadeos.audio.domain.OutputDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Master volume, microphone level and output device of PipeWire (or PulseAudio), through {@code wpctl}
 * and {@code pactl}. Every method blocks for a few milliseconds; call from a background thread.
 * Missing tools make the methods report nothing instead of failing.
 */
public final class SoundServer {
    private static final Logger LOG = LoggerFactory.getLogger(SoundServer.class);
    static final String SINK = "@DEFAULT_AUDIO_SINK@";
    static final String SOURCE = "@DEFAULT_AUDIO_SOURCE@";

    private final CommandRunner runner;

    public SoundServer() {
        this(CommandRunner.system());
    }

    public SoundServer(CommandRunner runner) {
        this.runner = runner;
    }

    /** Master volume 0..1 (PipeWire allows more than 1; shown as 1). */
    public OptionalDouble masterVolume() {
        return volume(SINK);
    }

    public void setMasterVolume(double value) {
        setVolume(SINK, value);
    }

    /** Capture level of the default microphone, 0..1. */
    public OptionalDouble microphoneVolume() {
        return volume(SOURCE);
    }

    public void setMicrophoneVolume(double value) {
        setVolume(SOURCE, value);
    }

    /** The outputs of the sound server; the default one marked. Empty without {@code pactl}. */
    public List<OutputDevice> outputs() {
        try {
            String defaultSink = runner.run(List.of("pactl", "get-default-sink")).strip();
            return parseSinks(runner.run(List.of("pactl", "list", "sinks")), defaultSink);
        } catch (IOException e) {
            LOG.debug("Cannot list outputs: {}", e.toString());
            return List.of();
        }
    }

    /** Makes the output the default; PipeWire moves running streams there. */
    public void setDefaultOutput(OutputDevice device) {
        try {
            runner.run(List.of("pactl", "set-default-sink", device.name()));
        } catch (IOException e) {
            LOG.warn("Cannot switch output to {}: {}", device.description(), e.toString());
        }
    }

    private OptionalDouble volume(String target) {
        try {
            return parseVolume(runner.run(List.of("wpctl", "get-volume", target)));
        } catch (IOException e) {
            LOG.debug("Cannot read volume of {}: {}", target, e.toString());
            return OptionalDouble.empty();
        }
    }

    private void setVolume(String target, double value) {
        double v = Math.max(0, Math.min(1, value));
        try {
            runner.run(List.of("wpctl", "set-volume", target, String.format(Locale.ROOT, "%.3f", v)));
        } catch (IOException e) {
            LOG.debug("Cannot set volume of {}: {}", target, e.toString());
        }
    }

    /** {@code Volume: 0.40} or {@code Volume: 0.40 [MUTED]}. */
    static OptionalDouble parseVolume(String output) {
        String s = output.strip();
        int colon = s.indexOf(':');
        if (!s.startsWith("Volume") || colon < 0) return OptionalDouble.empty();
        String number = s.substring(colon + 1).strip().split("\\s+")[0];
        try {
            return OptionalDouble.of(Math.min(1.0, Double.parseDouble(number)));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /** Parses {@code pactl list sinks}: blocks starting with {@code Sink #n} with Name and Description lines. */
    static List<OutputDevice> parseSinks(String output, String defaultSink) {
        List<OutputDevice> devices = new ArrayList<>();
        String name = null;
        String description = null;
        for (String raw : output.split("\n")) {
            String line = raw.strip();
            if (line.startsWith("Sink #")) {
                if (name != null) devices.add(new OutputDevice(name, description, name.equals(defaultSink)));
                name = null;
                description = null;
            } else if (line.startsWith("Name:") && name == null) {
                name = line.substring(5).strip();
            } else if (line.startsWith("Description:") && description == null) {
                description = line.substring(12).strip();
            }
        }
        if (name != null) devices.add(new OutputDevice(name, description, name.equals(defaultSink)));
        return devices;
    }
}

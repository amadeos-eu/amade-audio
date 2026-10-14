package org.datazuul.amadeos.audio.service.mixer;

import org.datazuul.amadeos.audio.domain.OutputDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Master volume, microphone level and output device of the sound server. Uses {@code wpctl} (PipeWire /
 * WirePlumber) and falls back to {@code pactl} (PulseAudio, or PipeWire with pipewire-pulse) when
 * {@code wpctl} is missing or fails. Every method blocks for a few milliseconds; call from a background
 * thread. Without either tool the methods report nothing, and the mixer uses its own master gain.
 */
public final class SoundServer {
    private static final Logger LOG = LoggerFactory.getLogger(SoundServer.class);
    static final String SINK = "@DEFAULT_AUDIO_SINK@";
    static final String SOURCE = "@DEFAULT_AUDIO_SOURCE@";
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");

    /** Which tool controls the volume. */
    public enum Backend {
        WPCTL, PACTL, NONE
    }

    private final CommandRunner runner;
    private volatile Backend backend;
    private volatile boolean warned;

    public SoundServer() {
        this(CommandRunner.system());
    }

    public SoundServer(CommandRunner runner) {
        this.runner = runner;
    }

    /** The tool in use, found on first use. */
    public Backend backend() {
        Backend b = backend;
        if (b == null) {
            b = detect();
            backend = b;
            LOG.info("Sound server volume control: {}", b);
        }
        return b;
    }

    private Backend detect() {
        try {
            if (parseVolume(runner.run(List.of("wpctl", "get-volume", SINK))).isPresent()) return Backend.WPCTL;
        } catch (IOException e) {
            LOG.debug("wpctl not usable: {}", e.toString());
        }
        try {
            if (parsePactlVolume(runner.run(List.of("pactl", "get-sink-volume", "@DEFAULT_SINK@"))).isPresent()) {
                return Backend.PACTL;
            }
        } catch (IOException e) {
            LOG.debug("pactl not usable: {}", e.toString());
        }
        return Backend.NONE;
    }

    /** True if the master volume of the system can be read and set. */
    public boolean hasMasterVolume() {
        return backend() != Backend.NONE;
    }

    /** Master volume 0..1 (more than 100 % is shown as 1). */
    public OptionalDouble masterVolume() {
        return volume(true);
    }

    /** Sets the master volume; false if it did not work. */
    public boolean setMasterVolume(double value) {
        return setVolume(true, value);
    }

    /** Capture level of the default microphone, 0..1. */
    public OptionalDouble microphoneVolume() {
        return volume(false);
    }

    public boolean setMicrophoneVolume(double value) {
        return setVolume(false, value);
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

    /** Makes the output the default; the sound server moves running streams there. */
    public void setDefaultOutput(OutputDevice device) {
        try {
            runner.run(List.of("pactl", "set-default-sink", device.name()));
        } catch (IOException e) {
            LOG.warn("Cannot switch output to {}: {}", device.description(), e.toString());
        }
    }

    private OptionalDouble volume(boolean sink) {
        try {
            return switch (backend()) {
                case WPCTL -> parseVolume(runner.run(List.of("wpctl", "get-volume", sink ? SINK : SOURCE)));
                case PACTL -> parsePactlVolume(runner.run(sink
                        ? List.of("pactl", "get-sink-volume", "@DEFAULT_SINK@")
                        : List.of("pactl", "get-source-volume", "@DEFAULT_SOURCE@")));
                case NONE -> OptionalDouble.empty();
            };
        } catch (IOException e) {
            LOG.debug("Cannot read the {} volume: {}", sink ? "master" : "microphone", e.toString());
            return OptionalDouble.empty();
        }
    }

    private boolean setVolume(boolean sink, double value) {
        double v = Math.max(0, Math.min(1, value));
        Backend b = backend();
        try {
            switch (b) {
                case WPCTL -> runner.run(List.of("wpctl", "set-volume", sink ? SINK : SOURCE,
                        String.format(Locale.ROOT, "%.3f", v)));
                case PACTL -> runner.run(List.of("pactl", sink ? "set-sink-volume" : "set-source-volume",
                        sink ? "@DEFAULT_SINK@" : "@DEFAULT_SOURCE@", Math.round(v * 100) + "%"));
                case NONE -> {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            if (!warned) {
                warned = true;
                LOG.warn("Cannot set the {} volume with {}: {}", sink ? "master" : "microphone", b, e.toString());
            }
            if (b == Backend.WPCTL) backend = null; // try again, maybe pactl works
            return false;
        }
    }

    /** {@code Volume: 0.40} or {@code Volume: 0.40 [MUTED]} (wpctl). */
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

    /** {@code Volume: front-left: 26214 /  40% / -23.88 dB,   front-right: ...} (pactl): the first percentage. */
    static OptionalDouble parsePactlVolume(String output) {
        Matcher m = PERCENT.matcher(output);
        if (!output.contains("Volume") || !m.find()) return OptionalDouble.empty();
        return OptionalDouble.of(Math.min(1.0, Integer.parseInt(m.group(1)) / 100.0));
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

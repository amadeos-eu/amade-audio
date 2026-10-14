package org.datazuul.amadeos.audio.service.mixer;

import org.datazuul.amadeos.audio.domain.OutputDevice;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundServerTest {
    @Test
    void parsesVolumes() {
        assertEquals(0.4, SoundServer.parseVolume("Volume: 0.40\n").getAsDouble(), 1e-9);
        assertEquals(0.4, SoundServer.parseVolume("Volume: 0.40 [MUTED]").getAsDouble(), 1e-9);
        assertEquals(1.0, SoundServer.parseVolume("Volume: 1.30").getAsDouble(), 1e-9);
        assertTrue(SoundServer.parseVolume("garbage").isEmpty());
    }

    @Test
    void parsesSinks() {
        String out = """
                Sink #45
                	State: RUNNING
                	Name: alsa_output.pci.analog-stereo
                	Description: Built-in Audio Analog Stereo
                	Properties:
                		device.description = "x"
                Sink #77
                	Name: bluez_output.AA
                	Description: Headphones
                """;
        List<OutputDevice> sinks = SoundServer.parseSinks(out, "bluez_output.AA");
        assertEquals(2, sinks.size());
        assertEquals("Built-in Audio Analog Stereo", sinks.get(0).description());
        assertFalse(sinks.get(0).isDefault());
        assertTrue(sinks.get(1).isDefault());
    }

    @Test
    void sendsCommandsAndSurvivesMissingTools() {
        List<List<String>> commands = new ArrayList<>();
        SoundServer server = new SoundServer(cmd -> {
            commands.add(cmd);
            return "Volume: 0.25";
        });
        assertTrue(server.setMasterVolume(0.5));
        assertEquals(SoundServer.Backend.WPCTL, server.backend());
        assertEquals(List.of("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", "0.500"), commands.get(commands.size() - 1));
        assertEquals(0.25, server.masterVolume().getAsDouble(), 1e-9);
        server.setDefaultOutput(new OutputDevice("hdmi", "HDMI", false));
        assertEquals(List.of("pactl", "set-default-sink", "hdmi"), commands.get(commands.size() - 1));

        SoundServer missing = new SoundServer(cmd -> {
            throw new IOException("not found");
        });
        assertTrue(missing.masterVolume().isEmpty());
        assertTrue(missing.outputs().isEmpty());
        assertFalse(missing.setMicrophoneVolume(0.3));
        assertFalse(missing.hasMasterVolume());
    }

    @Test
    void fallsBackToPactlWithoutWpctl() {
        List<List<String>> commands = new ArrayList<>();
        SoundServer server = new SoundServer(cmd -> {
            commands.add(cmd);
            if (cmd.get(0).equals("wpctl")) throw new IOException("wpctl: not found");
            return "Volume: front-left: 26214 /  40% / -23.88 dB,   front-right: 26214 /  40% / -23.88 dB";
        });
        assertEquals(SoundServer.Backend.PACTL, server.backend());
        assertEquals(0.4, server.masterVolume().getAsDouble(), 1e-9);
        assertTrue(server.setMasterVolume(0.55));
        assertEquals(List.of("pactl", "set-sink-volume", "@DEFAULT_SINK@", "55%"), commands.get(commands.size() - 1));
        assertTrue(server.setMicrophoneVolume(0.2));
        assertEquals(List.of("pactl", "set-source-volume", "@DEFAULT_SOURCE@", "20%"), commands.get(commands.size() - 1));
        assertTrue(SoundServer.parsePactlVolume("garbage").isEmpty());
    }
}

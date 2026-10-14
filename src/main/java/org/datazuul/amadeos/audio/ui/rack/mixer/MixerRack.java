package org.datazuul.amadeos.audio.ui.rack.mixer;

import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.audio.ui.widget.Fader;
import org.datazuul.amadeos.audio.ui.widget.Led;
import org.datazuul.amadeos.audio.ui.widget.RackPanel;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * The mixer rack: a fader per device, the microphone level, output selection, bass and treble, and
 * the master volume of the sound server.
 */
public final class MixerRack extends RackPanel {
    static final int FADER_HEIGHT = 112;

    final JToggleButton mute = Led.toggle("Mute", Led.RED);
    final Map<SourceId, Fader> faders = new EnumMap<>(SourceId.class);
    final Fader microphone = new Fader("Microphone level", FADER_HEIGHT, false, false);
    final Fader bass = new Fader("Bass", FADER_HEIGHT, true, true);
    final Fader treble = new Fader("Treble", FADER_HEIGHT, true, true);
    final Fader master = new Fader("Master volume", FADER_HEIGHT, false, false);
    final JPanel outputs = new JPanel();
    final ButtonGroup outputGroup = new ButtonGroup();

    MixerRack() {
        super(new BorderLayout(18, 0));
        for (SourceId id : SourceId.values()) faders.put(id, new Fader(id.label() + " volume", FADER_HEIGHT, false, false));

        JPanel channels = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        channels.setOpaque(false);
        channels.add(strip("CD", faders.get(SourceId.CD)));
        channels.add(strip("Player", faders.get(SourceId.PLAYER)));
        channels.add(strip("Web", faders.get(SourceId.WEBRADIO)));
        channels.add(strip("Mic", microphone));

        outputs.setOpaque(false);
        outputs.setLayout(new BoxLayout(outputs, BoxLayout.Y_AXIS));
        JPanel outputBox = new JPanel(new BorderLayout(0, 6));
        outputBox.setOpaque(false);
        JLabel outputLabel = new JLabel("Output");
        outputLabel.setFont(outputLabel.getFont().deriveFont(Font.BOLD));
        outputBox.add(outputLabel, BorderLayout.NORTH);
        outputBox.add(outputs, BorderLayout.CENTER);
        outputBox.add(Box.createHorizontalStrut(220), BorderLayout.SOUTH);

        JPanel tone = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tone.setOpaque(false);
        tone.add(bass);
        tone.add(treble);
        JPanel toneStrip = labelled("Bass / Treble", tone);

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.LEFT, 24, 0));
        middle.setOpaque(false);
        middle.add(channels);
        middle.add(outputBox);
        middle.add(toneStrip);

        JLabel masterLabel = new JLabel("Master");
        masterLabel.setFont(masterLabel.getFont().deriveFont(Font.BOLD));
        JPanel masterStrip = labelled(masterLabel, master);

        add(RackPanel.sideButtons(mute), BorderLayout.WEST);
        add(middle, BorderLayout.CENTER);
        add(masterStrip, BorderLayout.EAST);
    }

    private static JPanel strip(String label, Fader fader) {
        return labelled(label, fader);
    }

    private static JPanel labelled(String label, JComponent c) {
        return labelled(new JLabel(label), c);
    }

    private static JPanel labelled(JLabel label, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(c, BorderLayout.CENTER);
        p.add(label, BorderLayout.SOUTH);
        return p;
    }
}

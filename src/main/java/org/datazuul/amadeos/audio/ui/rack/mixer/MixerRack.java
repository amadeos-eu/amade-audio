package org.datazuul.amadeos.audio.ui.rack.mixer;

import static org.datazuul.amadeos.audio.i18n.Texts.tr;

import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.audio.ui.widget.Fader;
import org.datazuul.amadeos.audio.ui.widget.Led;
import org.datazuul.amadeos.audio.ui.widget.DeviceLabel;
import org.datazuul.amadeos.audio.ui.widget.RackPanel;
import org.datazuul.amadeos.audio.ui.widget.Scale;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * The mixer rack: a fader per device, the microphone level, output selection, bass and treble, and
 * the master volume of the sound server.
 */
public final class MixerRack extends RackPanel {
    /** At the current {@link Scale}. */
    static final int FADER_HEIGHT = Scale.px(112);

    final JToggleButton mute = Led.toggle(tr("mixer.mute"), Led.RED);
    final Map<SourceId, Fader> faders = new EnumMap<>(SourceId.class);
    final Fader microphone = new Fader(tr("mixer.microphone-level"), FADER_HEIGHT, false, false);
    final Fader bass = new Fader(tr("mixer.bass"), FADER_HEIGHT, true, true);
    final Fader treble = new Fader(tr("mixer.treble"), FADER_HEIGHT, true, true);
    final Fader master = new Fader(tr("mixer.master-volume"), FADER_HEIGHT, false, false);
    final JPanel outputs = new JPanel();
    final ButtonGroup outputGroup = new ButtonGroup();

    MixerRack() {
        super(new BorderLayout(Scale.px(18), 0));
        setDeviceLabel(tr("plate.mixer"), DeviceLabel.Symbol.MIXER);
        for (SourceId id : SourceId.values()) {
            faders.put(id, new Fader(tr("mixer.volume", tr("source." + id.name().toLowerCase(java.util.Locale.ROOT))),
                    FADER_HEIGHT, false, false));
        }

        JPanel channels = new JPanel(new FlowLayout(FlowLayout.LEFT, Scale.px(18), 0));
        channels.setOpaque(false);
        channels.add(strip(tr("mixer.channel.cd"), faders.get(SourceId.CD)));
        channels.add(strip(tr("mixer.channel.player"), faders.get(SourceId.PLAYER)));
        channels.add(strip(tr("mixer.channel.web"), faders.get(SourceId.WEBRADIO)));
        channels.add(strip(tr("mixer.channel.mic"), microphone));

        outputs.setOpaque(false);
        outputs.setLayout(new BoxLayout(outputs, BoxLayout.Y_AXIS));
        JPanel outputBox = new JPanel(new BorderLayout(0, Scale.px(6)));
        outputBox.setOpaque(false);
        JLabel outputLabel = new JLabel(tr("mixer.output"));
        outputLabel.setFont(outputLabel.getFont().deriveFont(Font.BOLD));
        outputBox.add(outputLabel, BorderLayout.NORTH);
        outputBox.add(outputs, BorderLayout.CENTER);
        outputBox.add(Box.createHorizontalStrut(Scale.px(220)), BorderLayout.SOUTH);

        JPanel tone = new JPanel(new FlowLayout(FlowLayout.LEFT, Scale.px(10), 0));
        tone.setOpaque(false);
        tone.add(bass);
        tone.add(treble);
        JPanel toneStrip = labelled(tr("mixer.bass-treble"), tone);

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.LEFT, Scale.px(24), 0));
        middle.setOpaque(false);
        middle.add(channels);
        middle.add(outputBox);
        middle.add(toneStrip);

        JLabel masterLabel = new JLabel(tr("mixer.master"));
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
        JPanel p = new JPanel(new BorderLayout(0, Scale.px(6)));
        p.setOpaque(false);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(c, BorderLayout.CENTER);
        p.add(label, BorderLayout.SOUTH);
        return p;
    }
}

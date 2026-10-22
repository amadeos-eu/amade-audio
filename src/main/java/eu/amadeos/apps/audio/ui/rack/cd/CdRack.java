package eu.amadeos.apps.audio.ui.rack.cd;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.audio.ui.widget.CoverView;
import eu.amadeos.apps.audio.ui.widget.LcdPanel;
import eu.amadeos.apps.audio.ui.widget.Led;
import eu.amadeos.apps.audio.ui.widget.DeviceLabel;
import eu.amadeos.apps.audio.ui.widget.RackPanel;
import eu.amadeos.apps.audio.ui.widget.Scale;
import eu.amadeos.apps.audio.ui.widget.SevenSegmentDisplay;
import eu.amadeos.commons.swing.TransportButtons;
import eu.amadeos.commons.swing.TransportGlyph;

import javax.swing.*;
import java.awt.*;

/** The CD rack: Power and Eject, the disc window with a small cover, track and time LCDs, transport. */
public final class CdRack extends RackPanel {
    final JToggleButton power = Led.toggle(tr("rack.power"), Led.GREEN);
    final JButton eject = new JButton(tr("cd.eject"));
    final CoverView cover = new CoverView(56);
    final JLabel source = RackPanel.subLabel(tr("cd.source"));
    final JLabel album = new JLabel(tr("cd.no-disc"));
    final SevenSegmentDisplay track = new SevenSegmentDisplay("88", 44);
    final SevenSegmentDisplay time = new SevenSegmentDisplay("88:88", 44);
    final JLabel mode = LcdPanel.caption(" ");
    final LcdPanel trackLcd = new LcdPanel(new BorderLayout());
    final LcdPanel timeLcd = new LcdPanel(new BorderLayout());
    final JButton previous;
    final JButton stop;
    final JButton play;
    final JButton pause;
    final JButton next;

    CdRack(CdPresenter p) {
        super(new BorderLayout(Scale.px(16), 0));
        setDeviceLabel(tr("plate.cd"), DeviceLabel.Symbol.DISC);
        power.setSelected(true);
        power.addActionListener(e -> p.power(power.isSelected()));
        eject.addActionListener(e -> p.eject());
        eject.setIcon(Scale.transport().icon(TransportGlyph.EJECT));
        eject.setHorizontalTextPosition(SwingConstants.LEFT);
        previous = Scale.transport().button(TransportGlyph.PREVIOUS, p::previous);
        stop = Scale.transport().button(TransportGlyph.STOP, p::stop);
        play = Scale.transport().button(TransportGlyph.PLAY, p::play);
        pause = Scale.transport().button(TransportGlyph.PAUSE, p::pause);
        next = Scale.transport().button(TransportGlyph.NEXT, p::next);

        JPanel info = RackPanel.infoWindow(new BorderLayout(Scale.px(14), 0));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        album.setFont(album.getFont().deriveFont(Font.BOLD, 16f));
        text.add(Box.createVerticalGlue());
        text.add(source);
        text.add(Box.createVerticalStrut(Scale.px(3)));
        text.add(album);
        text.add(Box.createVerticalGlue());
        info.add(cover, BorderLayout.WEST);
        info.add(text, BorderLayout.CENTER);

        trackLcd.add(track, BorderLayout.CENTER);
        trackLcd.add(LcdPanel.caption(tr("lcd.track")), BorderLayout.SOUTH);
        JPanel timeBox = new JPanel(new BorderLayout());
        timeBox.setOpaque(false);
        timeBox.add(time, BorderLayout.CENTER);
        timeBox.add(LcdPanel.caption(tr("lcd.min-sec")), BorderLayout.SOUTH);
        timeLcd.add(timeBox, BorderLayout.CENTER);
        mode.setVerticalAlignment(SwingConstants.TOP);
        mode.setPreferredSize(Scale.size(64, 10));
        timeLcd.add(mode, BorderLayout.EAST);

        // Same structure as the other racks: [side buttons | info + LCDs | transport], so the LCDs
        // and the transport bar line up with the player and web radio racks.
        JPanel lcdRow = new JPanel(new BorderLayout(Scale.px(8), 0));
        lcdRow.setOpaque(false);
        lcdRow.add(trackLcd, BorderLayout.WEST);
        lcdRow.add(timeLcd, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(Scale.px(14), 0));
        center.setOpaque(false);
        center.add(info, BorderLayout.CENTER);
        center.add(lcdRow, BorderLayout.EAST);

        JPanel transportWrap = new JPanel(new GridBagLayout());
        transportWrap.setOpaque(false);
        transportWrap.add(TransportButtons.bar(previous, stop, play, pause, next));

        add(RackPanel.sideButtons(power, eject), BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(transportWrap, BorderLayout.EAST);
    }

    void setDimmed(boolean dimmed) {
        trackLcd.setDimmed(dimmed);
        timeLcd.setDimmed(dimmed);
    }

    void setControlsEnabled(boolean enabled) {
        for (AbstractButton b : new AbstractButton[]{eject, previous, stop, play, pause, next}) b.setEnabled(enabled);
    }
}

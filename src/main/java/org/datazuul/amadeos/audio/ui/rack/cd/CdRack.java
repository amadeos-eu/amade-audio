package org.datazuul.amadeos.audio.ui.rack.cd;

import org.datazuul.amadeos.audio.ui.widget.CoverView;
import org.datazuul.amadeos.audio.ui.widget.LcdPanel;
import org.datazuul.amadeos.audio.ui.widget.Led;
import org.datazuul.amadeos.audio.ui.widget.RackPanel;
import org.datazuul.amadeos.audio.ui.widget.SevenSegmentDisplay;
import org.datazuul.amadeos.audio.ui.widget.TransportIcon;
import org.datazuul.amadeos.audio.ui.widget.TransportIcon.Kind;

import javax.swing.*;
import java.awt.*;

/** The CD rack: Power and Eject, the disc window with a small cover, track and time LCDs, transport. */
public final class CdRack extends RackPanel {
    final JToggleButton power = Led.toggle("Power", Led.GREEN);
    final JButton eject = new JButton("Eject");
    final CoverView cover = new CoverView(56);
    final JLabel source = RackPanel.subLabel("AUDIO CD");
    final JLabel album = new JLabel("No disc");
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
        super(new BorderLayout(16, 0));
        power.setSelected(true);
        power.addActionListener(e -> p.power(power.isSelected()));
        eject.addActionListener(e -> p.eject());
        eject.setIcon(new TransportIcon(Kind.EJECT));
        eject.setHorizontalTextPosition(SwingConstants.LEFT);
        previous = TransportIcon.button(Kind.PREVIOUS, p::previous);
        stop = TransportIcon.button(Kind.STOP, p::stop);
        play = TransportIcon.button(Kind.PLAY, p::play);
        pause = TransportIcon.button(Kind.PAUSE, p::pause);
        next = TransportIcon.button(Kind.NEXT, p::next);

        JPanel info = RackPanel.infoWindow(new BorderLayout(14, 0));
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        album.setFont(album.getFont().deriveFont(Font.BOLD, 16f));
        text.add(source);
        text.add(album);
        info.add(cover, BorderLayout.WEST);
        info.add(text, BorderLayout.CENTER);

        trackLcd.add(track, BorderLayout.CENTER);
        trackLcd.add(LcdPanel.caption("Track"), BorderLayout.SOUTH);
        JPanel timeBox = new JPanel(new BorderLayout());
        timeBox.setOpaque(false);
        timeBox.add(time, BorderLayout.CENTER);
        timeBox.add(LcdPanel.caption("Min      Sec"), BorderLayout.SOUTH);
        timeLcd.add(timeBox, BorderLayout.CENTER);
        mode.setVerticalAlignment(SwingConstants.TOP);
        mode.setPreferredSize(new Dimension(64, 10));
        timeLcd.add(mode, BorderLayout.EAST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        right.setOpaque(false);
        right.add(trackLcd);
        right.add(timeLcd);
        JPanel transport = TransportIcon.bar(previous, stop, play, pause, next);
        JPanel transportWrap = new JPanel(new GridBagLayout());
        transportWrap.setOpaque(false);
        transportWrap.add(transport);
        right.add(Box.createHorizontalStrut(8));
        right.add(transportWrap);

        add(RackPanel.sideButtons(power, eject), BorderLayout.WEST);
        add(info, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    void setDimmed(boolean dimmed) {
        trackLcd.setDimmed(dimmed);
        timeLcd.setDimmed(dimmed);
    }

    void setControlsEnabled(boolean enabled) {
        for (AbstractButton b : new AbstractButton[]{eject, previous, stop, play, pause, next}) b.setEnabled(enabled);
    }
}

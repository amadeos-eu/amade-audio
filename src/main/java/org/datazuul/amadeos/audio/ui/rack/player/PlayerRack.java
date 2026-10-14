package org.datazuul.amadeos.audio.ui.rack.player;

import org.datazuul.amadeos.audio.ui.widget.LcdPanel;
import org.datazuul.amadeos.audio.ui.widget.Led;
import org.datazuul.amadeos.audio.ui.widget.RackPanel;
import org.datazuul.amadeos.audio.ui.widget.SeekBar;
import org.datazuul.amadeos.audio.ui.widget.SevenSegmentDisplay;
import org.datazuul.amadeos.audio.ui.widget.SpectrumMeter;
import org.datazuul.amadeos.audio.ui.widget.TransportIcon;
import org.datazuul.amadeos.audio.ui.widget.TransportIcon.Kind;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** The file player rack: Power and Open, file window with format badges and position, LCD, transport. */
public final class PlayerRack extends RackPanel {
    static final String[] FORMATS = {"MP3", "OGG", "FLAC", "WAV", "AAC", "MIDI"};

    final JToggleButton power = Led.toggle("Power", Led.GREEN);
    final JButton open = new JButton("Open...");
    final JLabel folder = RackPanel.subLabel("LOCAL FILES");
    final JLabel file = new JLabel("No file");
    final Map<String, JLabel> badges = new LinkedHashMap<>();
    final SeekBar seek = new SeekBar();
    final SevenSegmentDisplay number = new SevenSegmentDisplay("88", 40);
    final SpectrumMeter spectrum = new SpectrumMeter();
    final SevenSegmentDisplay time = new SevenSegmentDisplay("88:88", 44);
    final LcdPanel lcd = new LcdPanel(new BorderLayout(14, 0));
    final JButton previous;
    final JButton stop;
    final JButton play;
    final JButton pause;
    final JButton next;

    PlayerRack(PlayerPresenter p) {
        super(new BorderLayout(16, 0));
        power.setSelected(true);
        power.addActionListener(e -> p.power(power.isSelected()));
        open.addActionListener(e -> p.openFiles());
        previous = TransportIcon.button(Kind.PREVIOUS, p::previous);
        stop = TransportIcon.button(Kind.STOP, p::stop);
        play = TransportIcon.button(Kind.PLAY, p::play);
        pause = TransportIcon.button(Kind.PAUSE, p::pause);
        next = TransportIcon.button(Kind.NEXT, p::next);
        seek.onSeek(p::seek);

        JPanel info = RackPanel.infoWindow(new BorderLayout(0, 4));
        file.setFont(file.getFont().deriveFont(Font.BOLD, 15f));
        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        badgeRow.setOpaque(false);
        ((FlowLayout) badgeRow.getLayout()).setAlignOnBaseline(true);
        for (String f : FORMATS) {
            JLabel badge = badge(f);
            badges.put(f, badge);
            badgeRow.add(badge);
        }
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{folder, file, badgeRow}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            text.add(c);
            text.add(Box.createVerticalStrut(3));
        }
        info.add(text, BorderLayout.CENTER);
        JPanel left = new JPanel(new BorderLayout(0, 8)) {
            /** Fixed width, height from the content. */
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(260, super.getPreferredSize().height);
            }
        };
        left.setOpaque(false);
        left.add(info, BorderLayout.CENTER);
        left.add(seek, BorderLayout.SOUTH);

        JPanel numberBox = new JPanel(new BorderLayout());
        numberBox.setOpaque(false);
        numberBox.add(number, BorderLayout.CENTER);
        numberBox.add(LcdPanel.caption("File"), BorderLayout.SOUTH);
        JPanel timeBox = new JPanel(new BorderLayout());
        timeBox.setOpaque(false);
        timeBox.add(time, BorderLayout.CENTER);
        timeBox.add(LcdPanel.caption("Min      Sec"), BorderLayout.SOUTH);
        lcd.add(numberBox, BorderLayout.WEST);
        lcd.add(spectrum, BorderLayout.CENTER);
        lcd.add(timeBox, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(14, 0));
        center.setOpaque(false);
        center.add(left, BorderLayout.WEST);
        center.add(lcd, BorderLayout.CENTER);

        JPanel transportWrap = new JPanel(new GridBagLayout());
        transportWrap.setOpaque(false);
        transportWrap.add(TransportIcon.bar(previous, stop, play, pause, next));

        add(RackPanel.sideButtons(power, open), BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(transportWrap, BorderLayout.EAST);
    }

    /** Highlights the badge of the playing format. */
    void setFormat(String format) {
        badges.forEach((name, badge) -> {
            badge.putClientProperty("amade.on", name.equals(format));
            badge.repaint();
        });
    }

    void setDimmed(boolean dimmed) {
        lcd.setDimmed(dimmed);
    }

    void setControlsEnabled(boolean enabled) {
        for (AbstractButton b : new AbstractButton[]{open, previous, stop, play, pause, next}) b.setEnabled(enabled);
        seek.setEnabled(enabled);
    }

    private static JLabel badge(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                boolean on = Boolean.TRUE.equals(getClientProperty("amade.on"));
                var t = AmadeUI.theme();
                if (on) {
                    g.setColor(t.textActive());
                    g.fillRect(0, 0, getWidth(), getHeight());
                    setForeground(t.base());
                } else {
                    g.setColor(t.shadow());
                    g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    setForeground(t.textActive());
                }
                super.paintComponent(g);
            }
        };
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        return label;
    }
}

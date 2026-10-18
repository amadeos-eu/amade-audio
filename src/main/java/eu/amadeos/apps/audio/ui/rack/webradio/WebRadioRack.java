package eu.amadeos.apps.audio.ui.rack.webradio;

import static eu.amadeos.apps.audio.i18n.Texts.tr;

import eu.amadeos.apps.audio.ui.widget.DotMatrixDisplay;
import eu.amadeos.apps.audio.ui.widget.LcdPalette;
import eu.amadeos.apps.audio.ui.widget.LcdPanel;
import eu.amadeos.apps.audio.ui.widget.Led;
import eu.amadeos.apps.audio.ui.widget.LevelMeter;
import eu.amadeos.apps.audio.ui.widget.DeviceLabel;
import eu.amadeos.apps.audio.ui.widget.RackPanel;
import eu.amadeos.apps.audio.ui.widget.Scale;
import eu.amadeos.apps.audio.ui.widget.Widget;
import eu.amadeos.apps.audio.ui.widget.TransportIcon;
import eu.amadeos.apps.audio.ui.widget.TransportIcon.Kind;

import javax.swing.*;
import java.awt.*;

/** The web radio rack: dot-matrix station display with title, meters and buffer, transport with Record. */
public final class WebRadioRack extends RackPanel {
    final JToggleButton power = Led.toggle(tr("rack.power"), Led.GREEN);
    final JButton stations = new JButton(tr("radio.stations"));
    final DotMatrixDisplay station = new DotMatrixDisplay(3, 64);
    final DotMatrixDisplay title = new DotMatrixDisplay(2, 110);
    final JLabel streamInfo = LcdPanel.caption(" ");
    final JLabel recording = new JLabel(" ");
    final LevelMeter meter = new LevelMeter();
    final BufferBar buffer = new BufferBar();
    final LcdPanel lcd = new LcdPanel(new BorderLayout(Scale.px(20), 0));
    final JButton previous;
    final JButton stop;
    final JButton play;
    final JToggleButton record = TransportIcon.toggle(Kind.RECORD);
    final JButton next;

    WebRadioRack(WebRadioPresenter p) {
        super(new BorderLayout(Scale.px(16), 0));
        setDeviceLabel(tr("plate.radio"), DeviceLabel.Symbol.RADIO);
        power.setSelected(true);
        power.addActionListener(e -> p.power(power.isSelected()));
        stations.addActionListener(e -> p.showStations());
        stations.setToolTipText(tr("radio.stations.tooltip"));
        previous = TransportIcon.button(Kind.PREVIOUS, p::previous);
        stop = TransportIcon.button(Kind.STOP, p::stop);
        play = TransportIcon.button(Kind.PLAY, p::play);
        next = TransportIcon.button(Kind.NEXT, p::next);
        record.addActionListener(e -> p.record(record.isSelected()));
        record.setToolTipText(tr("radio.record.tooltip"));
        record.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) p.recordingOptions();
            }
        });

        station.setText(tr("radio.lcd.idle"));
        recording.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        recording.setForeground(LcdPalette.PEAK);
        JPanel top = new JPanel(new BorderLayout(Scale.px(12), 0));
        top.setOpaque(false);
        top.add(station, BorderLayout.CENTER);
        top.add(streamInfo, BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout(Scale.px(12), 0));
        bottom.setOpaque(false);
        bottom.add(title, BorderLayout.CENTER);
        bottom.add(recording, BorderLayout.EAST);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, Scale.px(4)));
        text.setOpaque(false);
        text.add(top);
        text.add(bottom);
        JPanel meters = new JPanel(new BorderLayout(0, Scale.px(4)));
        meters.setOpaque(false);
        meters.setPreferredSize(Scale.size(260, 50));
        meters.add(meter, BorderLayout.CENTER);
        meters.add(buffer, BorderLayout.SOUTH);
        lcd.add(text, BorderLayout.CENTER);
        lcd.add(meters, BorderLayout.EAST);

        JPanel transportWrap = new JPanel(new GridBagLayout());
        transportWrap.setOpaque(false);
        transportWrap.add(TransportIcon.bar(previous, stop, play, record, next));

        add(RackPanel.sideButtons(power, stations), BorderLayout.WEST);
        add(lcd, BorderLayout.CENTER);
        add(transportWrap, BorderLayout.EAST);
    }

    void setDimmed(boolean dimmed) {
        lcd.setDimmed(dimmed);
    }

    void setControlsEnabled(boolean enabled) {
        for (AbstractButton b : new AbstractButton[]{stations, previous, stop, play, record, next}) b.setEnabled(enabled);
    }

    /** Fill level of the network buffer. */
    static final class BufferBar extends Widget {
        private double fill;

        BufferBar() {
            setPreferredSize(Scale.size(300, 12));
            getAccessibleContext().setAccessibleName(tr("radio.buffer"));
        }

        void setFill(double value) {
            if (Math.abs(value - fill) > 0.005) {
                fill = value;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color c = LcdPanel.colorFor(this);
            g.setColor(c);
            g.setFont(Scale.font(Font.MONOSPACED, Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            String label = tr("radio.buffer.short");
            g.drawString(label, 0, (getHeight() + fm.getAscent()) / 2 - 1);
            int x = Math.max(Scale.px(30), fm.stringWidth(label) + Scale.px(6));
            int w = getWidth() - x - 1;
            int pad = Math.max(1, Scale.px(3));
            g.drawRect(x, pad, w, getHeight() - 2 * pad);
            g.fillRect(x + 2, pad + 2, (int) Math.round((w - 3) * fill), Math.max(1, getHeight() - 2 * pad - 3));
        }
    }
}

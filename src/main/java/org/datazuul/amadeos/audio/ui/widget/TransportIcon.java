package org.datazuul.amadeos.audio.ui.widget;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/** Symbols of the transport buttons, drawn in the button's text colour (the record dot in red). */
public final class TransportIcon implements Icon {
    public enum Kind {
        PREVIOUS("Previous"), NEXT("Next"), REWIND("Rewind"), FORWARD("Fast forward"), STOP("Stop"),
        PLAY("Play"), PAUSE("Pause"), RECORD("Record"), EJECT("Eject"), FAVORITE("Add to favourites");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int SIZE = 16;
    private final Kind kind;

    public TransportIcon(Kind kind) {
        this.kind = kind;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        g2.setColor(c.isEnabled() ? c.getForeground() : UIManager.getColor("textInactiveText"));
        switch (kind) {
            case PREVIOUS -> {
                g2.fillRect(2, 2, 2, 12);
                g2.fill(triangle(14, 2, 14, 14, 5, 8));
            }
            case NEXT -> {
                g2.fillRect(12, 2, 2, 12);
                g2.fill(triangle(2, 2, 2, 14, 11, 8));
            }
            case REWIND -> {
                g2.fill(triangle(8, 2, 8, 14, 1, 8));
                g2.fill(triangle(15, 2, 15, 14, 8, 8));
            }
            case FORWARD -> {
                g2.fill(triangle(1, 2, 1, 14, 8, 8));
                g2.fill(triangle(8, 2, 8, 14, 15, 8));
            }
            case STOP -> g2.fillRect(3, 3, 10, 10);
            case PLAY -> g2.fill(triangle(4, 2, 4, 14, 14, 8));
            case PAUSE -> {
                g2.fillRect(3, 2, 4, 12);
                g2.fillRect(9, 2, 4, 12);
            }
            case RECORD -> {
                g2.setColor(c.isEnabled() ? new Color(0xC8, 0x28, 0x1C) : UIManager.getColor("textInactiveText"));
                g2.fillOval(2, 2, 12, 12);
            }
            case EJECT -> {
                g2.fill(triangle(8, 2, 15, 9, 1, 9));
                g2.fillRect(1, 11, 14, 3);
            }
            case FAVORITE -> {
                Path2D star = new Path2D.Float();
                for (int i = 0; i < 10; i++) {
                    double r = i % 2 == 0 ? 7.5 : 3.2;
                    double a = Math.PI / 2 + i * Math.PI / 5;
                    double px = 8 + r * Math.cos(a);
                    double py = 8.5 - r * Math.sin(a);
                    if (i == 0) star.moveTo(px, py);
                    else star.lineTo(px, py);
                }
                star.closePath();
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(star);
            }
        }
        g2.dispose();
    }

    private static Shape triangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        return new Polygon(new int[]{x1, x2, x3}, new int[]{y1, y2, y3}, 3);
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    /** A transport push button with tooltip and accessible name. */
    public static JButton button(Kind kind, Runnable action) {
        JButton b = new JButton(new TransportIcon(kind));
        style(b, kind);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** A transport toggle button, e.g. Record. */
    public static JToggleButton toggle(Kind kind) {
        JToggleButton b = new JToggleButton(new TransportIcon(kind));
        style(b, kind);
        return b;
    }

    private static void style(AbstractButton b, Kind kind) {
        b.setToolTipText(kind.label());
        b.getAccessibleContext().setAccessibleName(kind.label());
        b.setMargin(new Insets(6, 14, 6, 14));
        Dimension size = new Dimension(50, 34);
        b.setPreferredSize(size);
        b.setMinimumSize(size);
        b.setFocusable(true);
    }

    /** Buttons side by side without gaps, as a transport bar. */
    public static JPanel bar(AbstractButton... buttons) {
        JPanel bar = new JPanel(new GridLayout(1, buttons.length, 0, 0));
        bar.setOpaque(false);
        for (AbstractButton b : buttons) bar.add(b);
        return bar;
    }
}

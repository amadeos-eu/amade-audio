package eu.amadeos.apps.audio.ui.widget;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.DoubleConsumer;

/** Position slider of the player: shows the progress; dragging seeks when released. */
public final class SeekBar extends Widget {
    private double position;
    private boolean dragging;
    private DoubleConsumer onSeek = p -> {
    };

    public SeekBar() {
        setPreferredSize(Scale.size(280, 18));
        setFocusable(true);
        getAccessibleContext().setAccessibleName(tr("widget.position"));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                requestFocusInWindow();
                dragging = true;
                move(e.getX());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) move(e.getX());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!dragging) return;
                dragging = false;
                onSeek.accept(position);
            }

            private void move(int x) {
                int half = knob() / 2;
                position = Math.max(0, Math.min(1, (x - half) / (double) Math.max(1, getWidth() - 2 * half)));
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!isEnabled()) return;
                double step = e.getKeyCode() == KeyEvent.VK_RIGHT ? 0.02 : e.getKeyCode() == KeyEvent.VK_LEFT ? -0.02 : 0;
                if (step != 0) {
                    position = Math.max(0, Math.min(1, position + step));
                    onSeek.accept(position);
                    repaint();
                }
            }
        });
    }

    public void onSeek(DoubleConsumer handler) {
        onSeek = handler;
    }

    /** Progress 0..1; ignored while the user drags. */
    public void setPosition(double value) {
        if (dragging) return;
        double v = Double.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
        if (Math.abs(v - position) > 0.0005) {
            position = v;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        DesktopTheme t = AmadeUI.theme();
        int trackH = Math.max(5, Scale.px(8));
        int trackY = (getHeight() - trackH) / 2;
        int half = knob() / 2;
        g.setColor(new Color(0x10, 0x12, 0x16));
        g.fillRect(0, trackY, getWidth(), trackH);
        AmadeUI.paintBevel(g, 0, trackY, getWidth(), trackH, false, 1);
        int x = half + (int) Math.round(position * (getWidth() - 2 * half));
        g.setColor(t.shadow());
        g.fillRect(1, trackY + 1, Math.max(0, x - 1), trackH - 2);
        if (!isEnabled()) return;
        g.setColor(t.base());
        g.fillRect(x - half, 0, 2 * half, getHeight());
        AmadeUI.paintBevel(g, x - half, 0, 2 * half, getHeight(), true, Scale.factor() < 0.8f ? 1 : 2);
        if (isFocusOwner()) {
            g.setColor(t.textActive());
            g.drawRect(x - half - 1, 0, 2 * half + 1, getHeight() - 1);
        }
    }

    private static int knob() {
        return Math.max(8, Scale.px(12));
    }

    @Override
    protected javax.accessibility.AccessibleRole role() {
        return javax.accessibility.AccessibleRole.SLIDER;
    }
}

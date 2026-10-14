package org.datazuul.amadeos.audio.ui.widget;

import static org.datazuul.amadeos.audio.i18n.Texts.tr;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

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
        setPreferredSize(new Dimension(280, 18));
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
                position = Math.max(0, Math.min(1, (x - 6) / (double) Math.max(1, getWidth() - 12)));
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
        int trackY = getHeight() / 2 - 4;
        g.setColor(new Color(0x10, 0x12, 0x16));
        g.fillRect(0, trackY, getWidth(), 8);
        AmadeUI.paintBevel(g, 0, trackY, getWidth(), 8, false, 1);
        int x = 6 + (int) Math.round(position * (getWidth() - 12));
        g.setColor(t.shadow());
        g.fillRect(1, trackY + 1, Math.max(0, x - 1), 6);
        if (!isEnabled()) return;
        g.setColor(t.base());
        g.fillRect(x - 6, 0, 12, getHeight());
        AmadeUI.paintBevel(g, x - 6, 0, 12, getHeight(), true, 2);
        if (isFocusOwner()) {
            g.setColor(t.textActive());
            g.drawRect(x - 7, 0, 13, getHeight() - 1);
        }
    }

    @Override
    protected javax.accessibility.AccessibleRole role() {
        return javax.accessibility.AccessibleRole.SLIDER;
    }
}

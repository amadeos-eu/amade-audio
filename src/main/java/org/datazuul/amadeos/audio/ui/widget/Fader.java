package org.datazuul.amadeos.audio.ui.widget;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleValue;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Vertical mixer fader with a grooved slot and a ridged knob, in the theme's colours. Value 0..1;
 * mouse drag, wheel and the arrow/Page keys move it, Home/End jump to the ends.
 */
public final class Fader extends JComponent {
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final int knobWidth;
    private final int knobHeight;
    private final boolean centred;
    private float value;
    private boolean adjusting;

    /**
     * @param centred a tone control: the middle is 0 dB and marked
     */
    public Fader(String name, int height, boolean small, boolean centred) {
        this.knobWidth = small ? 22 : 34;
        this.knobHeight = small ? 16 : 22;
        this.centred = centred;
        Dimension size = new Dimension(knobWidth + 4, height);
        setPreferredSize(size);
        setMinimumSize(size);
        setFocusable(true);
        setToolTipText(name);
        getAccessibleContext().setAccessibleName(name);
        MouseAdapter mouse = new MouseAdapter() {
            private int grab;

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                adjusting = true;
                int knobY = knobY();
                grab = e.getY() >= knobY && e.getY() < knobY + knobHeight ? e.getY() - knobY : knobHeight / 2;
                drag(e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                drag(e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                adjusting = false;
                fire();
            }

            private void drag(int y) {
                int travel = travel();
                setValue(1f - (float) (y - grab - 2) / Math.max(1, travel));
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                setValue(value - (float) e.getPreciseWheelRotation() * 0.03f);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP, KeyEvent.VK_RIGHT -> setValue(value + 0.02f);
                    case KeyEvent.VK_DOWN, KeyEvent.VK_LEFT -> setValue(value - 0.02f);
                    case KeyEvent.VK_PAGE_UP -> setValue(value + 0.1f);
                    case KeyEvent.VK_PAGE_DOWN -> setValue(value - 0.1f);
                    case KeyEvent.VK_HOME -> setValue(1f);
                    case KeyEvent.VK_END -> setValue(0f);
                    default -> {
                        return;
                    }
                }
                e.consume();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    public float value() {
        return value;
    }

    /** True while the mouse holds the knob; listeners may wait for the release to save. */
    public boolean isAdjusting() {
        return adjusting;
    }

    public void setValue(float v) {
        float clamped = Math.max(0f, Math.min(1f, v));
        if (clamped == value) return;
        value = clamped;
        repaint();
        fire();
    }

    /** Sets the value without notifying listeners, e.g. when reading the system volume. */
    public void setValueQuietly(float v) {
        value = Math.max(0f, Math.min(1f, v));
        repaint();
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fire() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }

    private int travel() {
        return getHeight() - knobHeight - 4;
    }

    private int knobY() {
        return 2 + Math.round((1f - value) * travel());
    }

    @Override
    protected void paintComponent(Graphics g) {
        DesktopTheme t = AmadeUI.theme();
        int cx = getWidth() / 2;
        int slotW = 20 * knobWidth / 34;
        // Slot.
        g.setColor(t.sunkenBg());
        g.fillRect(cx - slotW / 2, 0, slotW, getHeight());
        g.setColor(new Color(0x10, 0x12, 0x16));
        g.fillRect(cx - 2, 6, 4, getHeight() - 12);
        g.setColor(t.shadow());
        g.drawLine(cx - 3, 6, cx - 3, getHeight() - 7);
        if (centred) {
            g.setColor(t.darkShadow());
            g.drawLine(cx - slotW / 2, getHeight() / 2, cx + slotW / 2 - 1, getHeight() / 2);
        }
        // Knob.
        int y = knobY();
        int x = cx - knobWidth / 2;
        g.setColor(isFocusOwner() ? t.softHighlight() : t.highlight());
        g.fillRect(x, y, knobWidth, knobHeight);
        g.setColor(t.base());
        g.fillRect(x + 2, y + 2, knobWidth - 4, knobHeight - 4);
        AmadeUI.paintBevel(g, x, y, knobWidth, knobHeight, true, 2);
        g.setColor(t.shadow());
        int ridges = knobHeight >= 20 ? 3 : 1;
        for (int i = 0; i < ridges; i++) {
            int ry = y + knobHeight / 2 - (ridges - 1) * 2 + i * 4;
            g.drawLine(x + 5, ry, x + knobWidth - 6, ry);
        }
        if (isFocusOwner()) {
            g.setColor(t.textActive());
            g.drawRect(x - 1, y - 1, knobWidth + 1, knobHeight + 1);
        }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.SLIDER;
                }

                @Override
                public AccessibleValue getAccessibleValue() {
                    return new AccessibleValue() {
                        @Override
                        public Number getCurrentAccessibleValue() {
                            return Math.round(value * 100);
                        }

                        @Override
                        public boolean setCurrentAccessibleValue(Number n) {
                            setValue(n.floatValue() / 100f);
                            return true;
                        }

                        @Override
                        public Number getMinimumAccessibleValue() {
                            return 0;
                        }

                        @Override
                        public Number getMaximumAccessibleValue() {
                            return 100;
                        }
                    };
                }
            };
        }
        return accessibleContext;
    }
}

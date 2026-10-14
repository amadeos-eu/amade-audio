package org.datazuul.amadeos.audio.ui.widget;

import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;

/**
 * Black LCD window with a sunken bevel. Elements inside take their colour from {@link #colorFor}: the
 * glow colour, or a dimmed one while the device is not active.
 */
public class LcdPanel extends JPanel {
    private boolean dimmed;

    public LcdPanel(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    }

    public void setDimmed(boolean value) {
        if (dimmed != value) {
            dimmed = value;
            repaint();
        }
    }

    public boolean isDimmed() {
        return dimmed;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(LcdPalette.BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** Colour for an element inside an LCD panel. */
    public static Color colorFor(Component c) {
        Color glow = LcdPalette.glow();
        LcdPanel panel = (LcdPanel) SwingUtilities.getAncestorOfClass(LcdPanel.class, c);
        return panel != null && panel.isDimmed() ? LcdPalette.dimmed(glow) : glow;
    }

    /** Small caption like {@code Track} or {@code Min  Sec} in the LCD colour. */
    public static JLabel caption(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(colorFor(this));
                super.paintComponent(g);
            }
        };
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }
}

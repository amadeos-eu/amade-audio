package org.datazuul.amadeos.audio.ui.widget;

import javax.swing.*;
import java.awt.*;

/** A small square indicator lamp. */
public final class Led implements Icon {
    public static final Color GREEN = new Color(0x5C, 0xFF, 0x5C);
    public static final Color RED = new Color(0xFF, 0x4B, 0x3A);
    private static final Color OFF = new Color(0x2C, 0x34, 0x2C);
    private static final int SIZE = 10;

    private final Color color;
    private final java.util.function.BooleanSupplier lit;

    public Led(Color color, java.util.function.BooleanSupplier lit) {
        this.color = color;
        this.lit = lit;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        boolean on = lit.getAsBoolean();
        g.setColor(new Color(0x1A, 0x1A, 0x1A));
        g.fillRect(x, y, SIZE, SIZE);
        g.setColor(on ? color : OFF);
        g.fillRect(x + 1, y + 1, SIZE - 2, SIZE - 2);
        if (on) {
            g.setColor(new Color(255, 255, 255, 110));
            g.fillRect(x + 2, y + 2, 3, 2);
        }
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    /** A toggle button with an LED that is lit while it is selected, like the rack's Power and Repeat buttons. */
    public static JToggleButton toggle(String text, Color color) {
        JToggleButton button = new JToggleButton(text);
        button.setIcon(new Led(color, button::isSelected));
        button.setHorizontalTextPosition(SwingConstants.LEFT);
        button.setIconTextGap(10);
        return button;
    }

    /** A push button with an LED driven from outside, e.g. the rack buttons showing the active device. */
    public static JButton button(String text, Color color, java.util.function.BooleanSupplier lit) {
        JButton button = new JButton(text, new Led(color, lit));
        button.setIconTextGap(8);
        return button;
    }
}

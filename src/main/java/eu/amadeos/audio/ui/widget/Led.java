package eu.amadeos.audio.ui.widget;

import javax.swing.*;
import java.awt.*;

/** A small square indicator lamp. */
public final class Led implements Icon {
    public static final Color GREEN = new Color(0x5C, 0xFF, 0x5C);
    public static final Color RED = new Color(0xFF, 0x4B, 0x3A);
    private static final Color OFF = new Color(0x2C, 0x34, 0x2C);

    private final Color color;
    private final java.util.function.BooleanSupplier lit;

    public Led(Color color, java.util.function.BooleanSupplier lit) {
        this.color = color;
        this.lit = lit;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        boolean on = lit.getAsBoolean();
        int size = size();
        int shine = Math.max(1, size / 4);
        g.setColor(new Color(0x1A, 0x1A, 0x1A));
        g.fillRect(x, y, size, size);
        g.setColor(on ? color : OFF);
        g.fillRect(x + 1, y + 1, size - 2, size - 2);
        if (on) {
            g.setColor(new Color(255, 255, 255, 110));
            g.fillRect(x + 2, y + 2, shine + 1, shine);
        }
    }

    private static int size() {
        return Math.max(6, Scale.px(10));
    }

    @Override
    public int getIconWidth() {
        return size();
    }

    @Override
    public int getIconHeight() {
        return size();
    }

    /** A toggle button with an LED that is lit while it is selected, like the rack's Power and Repeat buttons. */
    public static JToggleButton toggle(String text, Color color) {
        JToggleButton button = new JToggleButton(text);
        button.setIcon(new Led(color, button::isSelected));
        button.setHorizontalTextPosition(SwingConstants.LEFT);
        button.setIconTextGap(Scale.px(10));
        return button;
    }

    /** A push button with an LED driven from outside, e.g. the rack buttons showing the active device. */
    public static JButton button(String text, Color color, java.util.function.BooleanSupplier lit) {
        JButton button = new JButton(text, new Led(color, lit));
        button.setIconTextGap(Scale.px(8));
        return button;
    }
}

package org.datazuul.amadeos.audio.ui.widget;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;

/**
 * Size of the racks: all pixel sizes of the rack fronts are given at 100 % and pass through {@link #px}.
 * Texts shrink with them, but not below {@link #MIN_FONT} so they stay readable. Lists and tables in
 * scroll panes (the playlist, station list) and dialogs keep their normal size.
 *
 * <p>Set once at start-up, before the racks are built; a change takes effect at the next start.
 */
public final class Scale {
    /** Smallest text size in pixels the racks use. */
    public static final float MIN_FONT = 9f;

    private static final String SCALED = "amade.scale.font";
    /** Client property: leave the fonts of this component and its children as they are. */
    public static final String KEEP = "amade.scale.keep";

    private static volatile float factor = 1f;

    private Scale() {
    }

    /** The size of the racks, 50 to 100 percent. */
    public static void setPercent(int percent) {
        factor = Math.max(50, Math.min(100, percent)) / 100f;
    }

    public static float factor() {
        return factor;
    }

    /** A length given at 100 %; at least 1 unless it is 0. */
    public static int px(int value) {
        if (value == 0) return 0;
        int scaled = Math.round(value * factor);
        return value > 0 ? Math.max(1, scaled) : Math.min(-1, scaled);
    }

    public static float px(float value) {
        return value * factor;
    }

    public static Dimension size(int width, int height) {
        return new Dimension(px(width), px(height));
    }

    public static Insets insets(int top, int left, int bottom, int right) {
        return new Insets(px(top), px(left), px(bottom), px(right));
    }

    public static Border empty(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(px(top), px(left), px(bottom), px(right));
    }

    /** A text size given at 100 %: scaled, but not below {@link #MIN_FONT} (nor above its own size). */
    public static float text(float size) {
        return Math.max(Math.min(size, MIN_FONT), size * factor);
    }

    /** A font of the racks, its size given at 100 %. */
    public static Font font(String family, int style, int size) {
        return new Font(family, style, size).deriveFont(text(size));
    }

    /**
     * Scales the fonts of a rack and everything in it, also of components added later. Fonts set in the
     * code are given at 100 % like the default ones, so each component is scaled exactly once.
     * Scroll panes, text fields and components marked {@link #KEEP} are left alone.
     */
    public static void fonts(Component c) {
        if (factor == 1f || c instanceof JScrollPane || c instanceof JTextComponent || c instanceof JTable
                || c instanceof JList) {
            return;
        }
        if (c instanceof JComponent jc) {
            if (jc.getClientProperty(KEEP) != null) return;
            if (jc.getClientProperty(SCALED) == null) {
                jc.putClientProperty(SCALED, Boolean.TRUE);
                Font f = jc.getFont();
                if (f != null) jc.setFont(f.deriveFont(text(f.getSize2D())));
                jc.addContainerListener(new ContainerAdapter() {
                    @Override
                    public void componentAdded(ContainerEvent e) {
                        fonts(e.getChild());
                    }
                });
            }
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) fonts(child);
        }
    }
}

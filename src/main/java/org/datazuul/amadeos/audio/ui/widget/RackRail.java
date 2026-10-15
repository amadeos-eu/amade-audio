package org.datazuul.amadeos.audio.ui.widget;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;

/** The dark strip between two racks with the two handles. */
public final class RackRail extends Widget {
    private final boolean handles;

    public RackRail(boolean handles) {
        this.handles = handles;
        setPreferredSize(new Dimension(100, Scale.px(handles ? 12 : 6)));
        setMinimumSize(getPreferredSize());
        setMaximumSize(new Dimension(Short.MAX_VALUE, getPreferredSize().height));
    }

    /** Colour of the rack frame: the theme's darkest shadow. */
    public static Color frame(DesktopTheme t) {
        Color c = t.darkShadow();
        return new Color(c.getRed() * 2 / 3, c.getGreen() * 2 / 3, c.getBlue() * 2 / 3);
    }

    @Override
    protected void paintComponent(Graphics g) {
        DesktopTheme t = AmadeUI.theme();
        g.setColor(frame(t));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (!handles) return;
        int w = Scale.px(56);
        int inset = Scale.px(88);
        int body = Math.max(1, Scale.px(3));
        int y = getHeight() / 2 - body / 2 - 1;
        for (int x : new int[]{inset, getWidth() - inset - w}) {
            g.setColor(t.highlight());
            g.fillRect(x, y, w, 1);
            g.setColor(t.base());
            g.fillRect(x, y + 1, w, body);
            g.setColor(t.shadow());
            g.fillRect(x, y + 1 + body, w, 1);
        }
    }

    @Override
    protected javax.accessibility.AccessibleRole role() {
        return javax.accessibility.AccessibleRole.SEPARATOR;
    }
}

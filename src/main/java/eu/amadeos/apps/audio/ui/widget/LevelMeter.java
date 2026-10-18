package eu.amadeos.apps.audio.ui.widget;

import static eu.amadeos.apps.audio.i18n.Texts.tr;

import javax.swing.*;
import java.awt.*;

/** Left/right peak meter with 20 segments, the top ones amber and red. */
public final class LevelMeter extends Widget {
    private static final int SEGMENTS = 20;
    private float left;
    private float right;

    public LevelMeter() {
        setPreferredSize(Scale.size(300, 30));
        setOpaque(false);
        getAccessibleContext().setAccessibleName(tr("widget.level-meter"));
    }

    public void setLevels(float l, float r) {
        if (l != left || r != right) {
            left = l;
            right = r;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color glow = LcdPanel.colorFor(this);
        g.setFont(Scale.font(Font.MONOSPACED, Font.PLAIN, 11));
        int labelW = Scale.px(14);
        int gap = Scale.px(6);
        int rowH = Math.max(4, (getHeight() - Scale.px(4)) / 2 - Scale.px(2));
        paintRow(g, "L", left, 0, rowH, labelW, glow);
        paintRow(g, "R", right, rowH + gap, rowH, labelW, glow);
    }

    private void paintRow(Graphics g, String label, float level, int y, int h, int labelW, Color glow) {
        g.setColor(glow);
        g.drawString(label, 0, y + h);
        int w = getWidth() - labelW;
        int seg = w / SEGMENTS;
        int lit = Math.round(level * SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            Color c = i >= 17 ? LcdPalette.PEAK : i >= 14 ? LcdPalette.WARN : glow;
            g.setColor(i < lit ? c : LcdPalette.ghost(c));
            g.fillRect(labelW + i * seg, y, seg - Math.max(1, Scale.px(2)), h);
        }
    }

    @Override
    protected javax.accessibility.AccessibleRole role() {
        return javax.accessibility.AccessibleRole.PROGRESS_BAR;
    }
}

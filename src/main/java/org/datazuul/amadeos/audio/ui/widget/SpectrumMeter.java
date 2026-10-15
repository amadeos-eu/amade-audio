package org.datazuul.amadeos.audio.ui.widget;

import static org.datazuul.amadeos.audio.i18n.Texts.tr;

import org.datazuul.amadeos.audio.domain.AudioLevels;

import javax.swing.*;
import java.awt.*;

/** 16-band spectrum made of stacked LCD segments, with frequency labels below. */
public final class SpectrumMeter extends Widget {
    private static final String[] LABELS = {"60", "250", "1k", "4k", "16k Hz"};
    private final float[] bands = new float[AudioLevels.BANDS];

    public SpectrumMeter() {
        setPreferredSize(Scale.size(280, 74));
        setOpaque(false);
        getAccessibleContext().setAccessibleName(tr("widget.spectrum"));
    }

    public void setBands(float[] values) {
        boolean changed = false;
        for (int i = 0; i < bands.length && i < values.length; i++) {
            // Fall slowly, rise at once, as on hardware.
            float v = Math.max(values[i], bands[i] - 0.06f);
            if (v != bands[i]) {
                bands[i] = v;
                changed = true;
            }
        }
        if (changed) repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color glow = LcdPanel.colorFor(this);
        Color ghost = LcdPalette.ghost(LcdPalette.glow());
        g.setFont(Scale.font(Font.MONOSPACED, Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        int labelH = fm.getHeight();
        int h = getHeight() - labelH - 2;
        int colW = getWidth() / bands.length;
        int pitch = Math.max(3, Scale.px(5));
        int segH = pitch - Math.max(1, Scale.px(2));
        int rows = Math.max(1, h / pitch);
        for (int b = 0; b < bands.length; b++) {
            int lit = Math.round(bands[b] * rows);
            for (int r = 0; r < rows; r++) {
                g.setColor(r < lit ? glow : ghost);
                g.fillRect(b * colW + 1, h - (r + 1) * pitch + (pitch - segH), colW - Math.max(2, Scale.px(3)), segH);
            }
        }
        g.setColor(glow);
        int y = getHeight() - fm.getDescent();
        for (int i = 0; i < LABELS.length; i++) {
            int x = i * (getWidth() - fm.stringWidth(LABELS[LABELS.length - 1])) / (LABELS.length - 1);
            g.drawString(LABELS[i], x, y);
        }
    }

    @Override
    protected javax.accessibility.AccessibleRole role() {
        return javax.accessibility.AccessibleRole.PANEL;
    }
}

package org.datazuul.amadeos.audio.ui.widget;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Seven-segment digits like {@code 02:47}, with the unlit segments faintly visible. */
public final class SevenSegmentDisplay extends Widget {
    // Segment bits: a (top), b (top right), c (bottom right), d (bottom), e (bottom left), f (top left), g (middle).
    private static final int[] DIGITS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
    private static final int MINUS = 0x40;

    private final int digitHeight;
    private String text;

    /**
     * @param pattern text whose length fixes the width, e.g. {@code 88:88}
     */
    public SevenSegmentDisplay(String pattern, int digitHeight) {
        this.digitHeight = digitHeight;
        this.text = pattern.replaceAll("[0-9]", " ");
        Dimension size = new Dimension(width(pattern) + 4, digitHeight + 4);
        setPreferredSize(size);
        setMinimumSize(size);
        setOpaque(false);
    }

    public void setText(String value) {
        String v = value == null ? "" : value;
        if (!v.equals(text)) {
            text = v;
            getAccessibleContext().setAccessibleDescription(v.strip());
            repaint();
        }
    }

    public String text() {
        return text;
    }

    private int digitWidth() {
        return Math.round(digitHeight * 0.56f);
    }

    private int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c == ':' || c == '.' ? Math.round(digitHeight * 0.28f) : digitWidth() + Math.round(digitHeight * 0.12f);
        return w;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color on = LcdPanel.colorFor(this);
        Color off = LcdPalette.ghost(LcdPalette.glow());
        int x = Math.max(0, (getWidth() - width(text)) / 2);
        int y = (getHeight() - digitHeight) / 2;
        for (char c : text.toCharArray()) {
            if (c == ':' || c == '.') {
                int w = Math.round(digitHeight * 0.28f);
                int dot = Math.max(3, digitHeight / 9);
                g2.setColor(on);
                if (c == ':') {
                    g2.fillRect(x + (w - dot) / 2, y + digitHeight / 3 - dot / 2, dot, dot);
                    g2.fillRect(x + (w - dot) / 2, y + digitHeight * 2 / 3 - dot / 2, dot, dot);
                } else {
                    g2.fillRect(x + (w - dot) / 2, y + digitHeight - dot, dot, dot);
                }
                x += w;
                continue;
            }
            int mask = c >= '0' && c <= '9' ? DIGITS[c - '0'] : c == '-' ? MINUS : 0;
            paintDigit(g2, x, y, mask, on, off);
            x += digitWidth() + Math.round(digitHeight * 0.12f);
        }
        g2.dispose();
    }

    private void paintDigit(Graphics2D g2, int x, int y, int mask, Color on, Color off) {
        float w = digitWidth();
        float h = digitHeight;
        float t = Math.max(2f, h * 0.11f);
        float gap = Math.max(1f, t * 0.25f);
        float half = h / 2f;
        RoundRectangle2D[] seg = {
                new RoundRectangle2D.Float(x + t / 2 + gap, y, w - t - 2 * gap, t, t, t),                         // a
                new RoundRectangle2D.Float(x + w - t, y + t / 2 + gap, t, half - t / 2 - 2 * gap, t, t),          // b
                new RoundRectangle2D.Float(x + w - t, y + half + gap, t, half - t / 2 - 2 * gap, t, t),           // c
                new RoundRectangle2D.Float(x + t / 2 + gap, y + h - t, w - t - 2 * gap, t, t, t),                 // d
                new RoundRectangle2D.Float(x, y + half + gap, t, half - t / 2 - 2 * gap, t, t),                   // e
                new RoundRectangle2D.Float(x, y + t / 2 + gap, t, half - t / 2 - 2 * gap, t, t),                  // f
                new RoundRectangle2D.Float(x + t / 2 + gap, y + half - t / 2, w - t - 2 * gap, t, t, t)           // g
        };
        for (int i = 0; i < 7; i++) {
            g2.setColor((mask & (1 << i)) != 0 ? on : off);
            g2.fill(seg[i]);
        }
    }
}

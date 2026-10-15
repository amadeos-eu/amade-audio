package org.datazuul.amadeos.audio.ui.widget;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 * The name of a rack on a vertical name plate at its left edge, engraved in the theme's colours below
 * an own symbol per device. Plain text, so it can be translated.
 */
public final class DeviceLabel {
    public enum Symbol { MIXER, DISC, FILES, RADIO }

    /** At 100 %. */
    static final int PLATE_WIDTH = 24;

    private final String name;
    private final Symbol symbol;

    /** @param name short, as the plate is only as long as the rack is high, e.g. {@code PLAYER} */
    public DeviceLabel(String name, Symbol symbol) {
        this.name = name;
        this.symbol = symbol;
    }

    /** Extra space the plate needs inside the rack, at the left. */
    Insets space() {
        return new Insets(0, plateWidth() + Scale.px(10), 0, 0);
    }

    /** Paints into the rack's content area {@code (x, y, w, h)} (inside the bevel and padding). */
    void paint(Graphics2D g, Rectangle area) {
        DesktopTheme t = AmadeUI.theme();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font font = Scale.font(Font.SANS_SERIF, Font.BOLD, 11);
        int plate = plateWidth();
        int x = area.x - plate - Scale.px(10);
        int y = area.y - Scale.px(4);
        int h = area.height + Scale.px(8);
        int symbol = Math.max(9, Scale.px(14));
        g.setColor(t.sunkenBg());
        g.fillRect(x, y, plate, h);
        AmadeUI.paintBevel(g, x, y, plate, h, false, 1);
        paintSymbol(g, x + (plate - symbol) / 2, y + Scale.px(6), symbol, accent(t));
        // Text reading upwards, from the bottom of the plate.
        int available = h - symbol - Scale.px(18);
        Font spaced = font.deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.12));
        g.setFont(spaced);
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(name);
        // Translated names can be longer than the plate: shrink the letters until they fit.
        for (float size = font.getSize2D() - 1; textW > available && size >= 7; size--) {
            g.setFont(spaced.deriveFont(size));
            fm = g.getFontMetrics();
            textW = fm.stringWidth(name);
        }
        int tx = x + plate / 2 + fm.getAscent() / 2 - 1;
        int ty = y + h - Scale.px(6) - Math.max(0, (available - textW) / 2);
        AffineTransform old = g.getTransform();
        g.rotate(-Math.PI / 2, tx, ty);
        engrave(g, name, tx, ty, t);
        g.setTransform(old);
    }

    /** The plate is as wide as the text is high, at least {@code PLATE_WIDTH} at 100 %. */
    private static int plateWidth() {
        return Math.max(Scale.px(PLATE_WIDTH), Math.round(Scale.text(11)) + 6);
    }

    /** Text pressed into the plate: light edge below right, dark letters. */
    private static void engrave(Graphics2D g, String text, int x, int y, DesktopTheme t) {
        g.setColor(t.highlight());
        g.drawString(text, x + 1, y + 1);
        g.setColor(t.isDark() ? t.textActive() : t.darkShadow());
        g.drawString(text, x, y);
    }

    private static Color accent(DesktopTheme t) {
        Color c = t.activeTitleBg();
        int d = Math.abs(c.getRed() - t.base().getRed()) + Math.abs(c.getGreen() - t.base().getGreen())
                + Math.abs(c.getBlue() - t.base().getBlue());
        return d < 60 ? (t.isDark() ? t.textActive() : t.darkShadow()) : c;
    }

    private void paintSymbol(Graphics2D g, int x, int y, int s, Color c) {
        g.setColor(c);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(Math.max(1.4f, s / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (symbol) {
            case MIXER -> {
                for (int i = 0; i < 3; i++) {
                    int lx = x + s / 6 + i * s / 3;
                    g.drawLine(lx, y, lx, y + s);
                    int ky = y + (i == 1 ? s / 4 : i == 0 ? s * 3 / 5 : s * 2 / 5);
                    g.fillRect(lx - s / 7, ky, 2 * s / 7 + 1, Math.max(2, s / 6));
                }
            }
            case DISC -> {
                g.drawOval(x, y, s, s);
                g.drawOval(x + s * 2 / 5, y + s * 2 / 5, s / 5, s / 5);
                g.drawArc(x + s / 6, y + s / 6, s * 2 / 3, s * 2 / 3, 30, 60);
            }
            case FILES -> {
                int r = Math.max(3, s / 4);
                g.fillOval(x + 1, y + s - r - 1, r + 1, r);
                g.fillOval(x + s - r - 1, y + s - r - 3, r + 1, r);
                g.drawLine(x + r + 1, y + s - r / 2 - 1, x + r + 1, y + 2);
                g.drawLine(x + s - 1, y + s - r / 2 - 3, x + s - 1, y);
                Path2D beam = new Path2D.Float();
                beam.moveTo(x + r + 1, y + 2);
                beam.lineTo(x + s - 1, y);
                g.draw(beam);
            }
            case RADIO -> {
                int cx = x + s / 2;
                g.drawLine(cx, y + s / 3, cx, y + s);
                g.fillOval(cx - 2, y + s / 3 - 2, 4, 4);
                g.drawArc(cx - s / 4, y + s / 3 - s / 4, s / 2, s / 2, -40, 80);
                g.drawArc(cx - s / 4, y + s / 3 - s / 4, s / 2, s / 2, 140, 80);
                g.drawArc(cx - s / 2, y + s / 3 - s / 2, s, s, -40, 80);
                g.drawArc(cx - s / 2, y + s / 3 - s / 2, s, s, 140, 80);
            }
        }
        g.setStroke(old);
    }
}

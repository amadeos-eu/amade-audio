package org.datazuul.amadeos.audio.ui.widget;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 5x7 dot-matrix text, scrolling when it is longer than the display (after dz-radio's LcdScrollPanel).
 */
public final class DotMatrixDisplay extends Widget {
    private static final Map<Character, byte[]> CHARS = new HashMap<>();
    private static final int COLUMNS_PER_CHAR = 6;

    static {
        for (LcdCharacter c : LcdCharacter.values()) CHARS.put(c.character(), c.columns());
    }

    private final int dot;
    private final Timer timer;
    private String text = "";
    private int offset;
    private int pause;

    /** @param dot size of one dot in pixels, including the gap */
    public DotMatrixDisplay(int dot, int columns) {
        this.dot = dot;
        Dimension size = new Dimension(columns * dot, 9 * dot);
        setPreferredSize(size);
        setMinimumSize(new Dimension(10 * dot, 9 * dot));
        setOpaque(false);
        timer = new Timer(60, e -> tick());
    }

    public void setText(String value) {
        String v = value == null ? "" : value.strip();
        if (!v.equals(text)) {
            text = v;
            offset = 0;
            pause = 25;
            getAccessibleContext().setAccessibleDescription(v);
            repaint();
        }
    }

    public String text() {
        return text;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private boolean scrolls() {
        return text.length() * COLUMNS_PER_CHAR > getWidth() / dot;
    }

    private void tick() {
        if (!scrolls() || !isShowing()) return;
        if (pause > 0) {
            pause--;
            return;
        }
        offset++;
        if (offset >= (text.length() + 4) * COLUMNS_PER_CHAR) {
            offset = 0;
            pause = 25;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color on = LcdPanel.colorFor(this);
        Color off = LcdPalette.ghost(LcdPalette.glow());
        int columns = getWidth() / dot;
        int top = (getHeight() - 9 * dot) / 2;
        String shown = scrolls() ? text + "    " + text : text;
        int size = Math.max(1, dot - 1);
        for (int col = 0; col < columns; col++) {
            int global = col + (scrolls() ? offset : 0);
            int charIndex = global / COLUMNS_PER_CHAR;
            int inner = global % COLUMNS_PER_CHAR;
            byte bits = 0;
            if (charIndex < shown.length() && inner < 5) bits = glyph(shown.charAt(charIndex))[inner];
            for (int row = 0; row < 9; row++) {
                boolean lit = row > 0 && row < 8 && (bits & (1 << (row - 1))) != 0;
                g.setColor(lit ? on : off);
                g.fillRect(col * dot, top + row * dot, size, size);
            }
        }
    }

    private static byte[] glyph(char c) {
        byte[] g = CHARS.get(c);
        if (g == null) g = CHARS.get(Character.toUpperCase(c));
        if (g == null) g = CHARS.get(Character.toString(c).toUpperCase(Locale.ROOT).charAt(0));
        if (g == null) g = CHARS.get('?');
        return g == null ? new byte[5] : g;
    }
}

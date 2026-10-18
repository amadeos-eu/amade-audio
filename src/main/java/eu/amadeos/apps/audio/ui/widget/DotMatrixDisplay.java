package eu.amadeos.apps.audio.ui.widget;

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

    /**
     * @param dot size of one dot in pixels at 100 %, including the gap; never below 2, so the dots stay
     *            apart and the letters readable
     */
    public DotMatrixDisplay(int dot, int columns) {
        this.dot = Math.max(2, Scale.px(dot));
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

    static byte[] glyph(char c) {
        byte[] g = known(c);
        if (g == null) g = accented(c);
        if (g == null) g = CHARS.get('?');
        return g == null ? new byte[5] : g;
    }

    private static byte[] known(char c) {
        byte[] g = CHARS.get(c);
        if (g == null) g = CHARS.get(Character.toUpperCase(c));
        if (g == null) g = CHARS.get(Character.toString(c).toUpperCase(Locale.ROOT).charAt(0));
        return g;
    }

    /**
     * Letters of French, Spanish and Italian (é è ê à ç ñ í ó ú ...) from the base letter and the accent, so
     * that translated texts show on the LCD: the accent takes the top row and the letter loses its second
     * row (a cedilla takes the bottom row instead). ¿ and ¡ are ? and ! turned upside down.
     */
    private static byte[] accented(char c) {
        if (c == '¿') return rotated(CHARS.get('?'));
        if (c == '¡') return rotated(CHARS.get('!'));
        String nfd = java.text.Normalizer.normalize(String.valueOf(c), java.text.Normalizer.Form.NFD);
        if (nfd.length() != 2) return null;
        byte[] base = known(nfd.charAt(0));
        if (base == null) return null;
        String mark = switch (nfd.charAt(1)) {
            case '\u0301' -> "   X ";  // acute
            case '\u0300' -> " X   ";  // grave
            case '\u0302' -> " XXX ";  // circumflex
            case '\u0308' -> " X X ";  // diaeresis
            case '\u0303' -> "XX XX";  // tilde
            case '\u030A' -> "  X  ";  // ring
            case '\u0327' -> "  X  ";  // cedilla, below
            default -> null;
        };
        if (mark == null) return null;
        boolean below = nfd.charAt(1) == '\u0327';
        byte[] g = new byte[5];
        for (int col = 0; col < 5; col++) {
            int bits = base[col] & 0x7F;
            int dot = mark.charAt(col) == 'X' ? 1 : 0;
            g[col] = (byte) (below
                    ? (bits & 1) | ((bits & 0x7C) >> 1) | (dot << 6)
                    : dot | ((bits & 1) << 1) | (bits & 0x7C));
        }
        return g;
    }

    private static byte[] rotated(byte[] glyph) {
        if (glyph == null) return null;
        byte[] g = new byte[5];
        for (int col = 0; col < 5; col++) {
            int bits = glyph[4 - col] & 0x7F;
            int flipped = 0;
            for (int row = 0; row < 7; row++) if ((bits & (1 << row)) != 0) flipped |= 1 << (6 - row);
            g[col] = (byte) flipped;
        }
        return g;
    }
}

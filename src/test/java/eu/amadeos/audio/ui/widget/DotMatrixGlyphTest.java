package eu.amadeos.audio.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DotMatrixGlyphTest {
    private static String rows(char c) {
        byte[] g = DotMatrixDisplay.glyph(c);
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 5; col++) sb.append((g[col] & (1 << row)) != 0 ? 'X' : ' ');
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void accentedLettersOfTheFiveLanguagesHaveOwnGlyphs() {
        String unknown = rows('?');
        for (char c : "éèêëàâçñíìîóòôúùûÉÈÀÇÑÍÓÚ¿¡".toCharArray()) {
            assertNotEquals(unknown, rows(c), "glyph for " + c);
        }
        assertNotEquals(rows('e'), rows('é'));
        assertNotEquals(rows('E'), rows('É'));
        assertNotEquals(rows('É'), rows('È'));
    }

    @Test
    void accentTakesTheTopRow() {
        String expected = String.join("\n", "   X ", "XXXXX", "X    ", "XXX  ", "X    ", "X    ", "XXXXX") + "\n";
        assertEquals(expected, rows('É'));
    }

    @Test
    void invertedMarksAreTurnedUpsideDown() {
        String q = rows('?');
        String inverted = rows('¿');
        String[] a = q.split("\n");
        String[] b = inverted.split("\n");
        for (int i = 0; i < 7; i++) assertEquals(new StringBuilder(a[6 - i]).reverse().toString(), b[i]);
    }
}

package org.datazuul.amadeos.audio.ui.playlist;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.function.IntPredicate;

/** Table cells with the playing row in bold and padded columns. */
public final class PlayingRenderer extends DefaultTableCellRenderer {
    private final IntPredicate playing;

    public PlayingRenderer(IntPredicate playing) {
        this.playing = playing;
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                   int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, false, row, column);
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        int modelRow = table.convertRowIndexToModel(row);
        setFont(table.getFont().deriveFont(playing.test(modelRow) ? Font.BOLD : Font.PLAIN));
        return this;
    }

    /** Sets the renderer on all columns. */
    public static void install(JTable table, IntPredicate playing) {
        PlayingRenderer r = new PlayingRenderer(playing);
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(r);
    }

    /** Fixed widths for the narrow columns. */
    public static void widths(JTable table, int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            if (widths[i] <= 0) continue;
            var col = table.getColumnModel().getColumn(i);
            col.setPreferredWidth(widths[i]);
            col.setMaxWidth(widths[i] * 2);
        }
    }
}

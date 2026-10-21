package eu.amadeos.apps.audio.ui.rack.cd;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.audio.domain.AlbumInfo;
import eu.amadeos.apps.audio.domain.CdTrack;
import eu.amadeos.apps.audio.ui.playlist.CoverColumn;
import eu.amadeos.apps.audio.ui.playlist.PlayingRenderer;
import eu.amadeos.apps.audio.ui.playlist.PlaylistPanel;
import eu.amadeos.apps.audio.ui.widget.Led;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/** The CD's track list in the playlist panel. */
public final class CdTrackListView {
    final CoverColumn cover = new CoverColumn();
    final Model model = new Model();
    final JTable table = new JTable(model);
    final JButton edit = new JButton(tr("cd.edit-titles"));
    final JButton lookUp = new JButton(tr("cd.look-up"));
    final JToggleButton shuffle = Led.toggle(tr("list.shuffle"), Led.GREEN);
    final JToggleButton repeat = Led.toggle(tr("list.repeat"), Led.GREEN);
    final JPanel component;

    CdTrackListView(CdPresenter p) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        PlayingRenderer.install(table, row -> row == model.playing);
        PlayingRenderer.widths(table, 64, 0, 0, 80);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) p.play(table.getSelectedRow());
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "amade.play");
        table.getActionMap().put("amade.play", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (table.getSelectedRow() >= 0) p.play(table.getSelectedRow());
            }
        });
        edit.addActionListener(e -> p.editTitles());
        lookUp.addActionListener(e -> p.lookUp());
        shuffle.addActionListener(e -> p.setOptions(shuffle.isSelected(), repeat.isSelected()));
        repeat.addActionListener(e -> p.setOptions(shuffle.isSelected(), repeat.isSelected()));
        component = PlaylistPanel.view(cover, PlaylistPanel.scroll(table),
                PlaylistPanel.buttonRow(new JComponent[]{edit, lookUp}, new JComponent[]{shuffle, repeat}));
    }

    /** Tracks of the disc with titles. */
    static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {tr("column.track"), tr("column.title"), tr("column.artist"),
                tr("column.length")};
        private List<CdTrack> tracks = List.of();
        private AlbumInfo album = AlbumInfo.unknown(0);
        int playing = -1;

        void set(List<CdTrack> newTracks, AlbumInfo newAlbum) {
            tracks = List.copyOf(newTracks);
            album = newAlbum;
            fireTableDataChanged();
        }

        void setPlaying(int index) {
            int old = playing;
            playing = index;
            if (old >= 0 && old < tracks.size()) fireTableRowsUpdated(old, old);
            if (index >= 0 && index < tracks.size()) fireTableRowsUpdated(index, index);
        }

        @Override
        public int getRowCount() {
            return tracks.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            CdTrack t = tracks.get(row);
            return switch (column) {
                case 0 -> (row == playing ? "▶ " : "") + String.format("%02d", t.number());
                case 1 -> album.title(row);
                case 2 -> album.artist(row);
                default -> PlaylistPanel.time(t.length().toSeconds());
            };
        }
    }
}

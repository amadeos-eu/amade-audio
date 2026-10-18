package eu.amadeos.audio.ui.rack.player;

import static eu.amadeos.audio.i18n.Texts.tr;

import eu.amadeos.audio.domain.PlaylistEntry;
import eu.amadeos.audio.ui.playlist.CoverColumn;
import eu.amadeos.audio.ui.playlist.PlayingRenderer;
import eu.amadeos.audio.ui.playlist.PlaylistPanel;
import eu.amadeos.audio.ui.widget.Led;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The player's playlist in the playlist panel; files can be dropped onto it. */
public final class FilePlaylistView {
    final CoverColumn cover = new CoverColumn();
    final Model model = new Model();
    final JTable table = new JTable(model);
    final JButton addFiles = new JButton(tr("player.add-files"));
    final JButton addFolder = new JButton(tr("player.add-folder"));
    final JButton remove = new JButton(tr("player.remove"));
    final JButton clear = new JButton(tr("player.clear"));
    final JButton load = new JButton(tr("player.load-m3u"));
    final JButton save = new JButton(tr("player.save-m3u"));
    final JToggleButton shuffle = Led.toggle(tr("list.shuffle"), Led.GREEN);
    final JToggleButton repeat = Led.toggle(tr("list.repeat"), Led.GREEN);
    final JPanel component;

    FilePlaylistView(PlayerPresenter p) {
        PlayingRenderer.install(table, row -> row == model.playing);
        PlayingRenderer.widths(table, 52, 0, 0, 0, 64, 64);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) p.play(table.getSelectedRow());
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "amade.play");
        table.getActionMap().put("amade.play", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.getSelectedRow() >= 0) p.play(table.getSelectedRow());
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "amade.remove");
        table.getActionMap().put("amade.remove", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                p.removeSelected();
            }
        });
        addFiles.addActionListener(e -> p.openFiles());
        addFolder.addActionListener(e -> p.openFolder());
        remove.addActionListener(e -> p.removeSelected());
        clear.addActionListener(e -> p.clear());
        load.addActionListener(e -> p.loadPlaylist());
        save.addActionListener(e -> p.savePlaylist());
        shuffle.addActionListener(e -> p.setOptions(shuffle.isSelected(), repeat.isSelected()));
        repeat.addActionListener(e -> p.setOptions(shuffle.isSelected(), repeat.isSelected()));
        JScrollPane scroll = PlaylistPanel.scroll(table);
        TransferHandler drop = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    List<Path> paths = new ArrayList<>();
                    for (File f : files) paths.add(f.toPath());
                    p.add(paths, false);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        table.setTransferHandler(drop);
        scroll.setTransferHandler(drop);
        component = PlaylistPanel.view(cover, scroll, PlaylistPanel.buttonRow(
                new JComponent[]{addFiles, addFolder, remove, clear, load, save}, new JComponent[]{shuffle, repeat}));
    }

    /** Table of the playlist entries. */
    static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {tr("column.number"), tr("column.title"), tr("column.artist"),
                tr("column.album"), tr("column.length"), tr("column.format")};
        private List<PlaylistEntry> entries = List.of();
        int playing = -1;

        void set(List<PlaylistEntry> newEntries, int newPlaying) {
            entries = List.copyOf(newEntries);
            playing = newPlaying;
            fireTableDataChanged();
        }

        void setPlaying(int index) {
            int old = playing;
            playing = index;
            if (old >= 0 && old < entries.size()) fireTableRowsUpdated(old, old);
            if (index >= 0 && index < entries.size()) fireTableRowsUpdated(index, index);
        }

        @Override
        public int getRowCount() {
            return entries.size();
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
            PlaylistEntry e = entries.get(row);
            return switch (column) {
                case 0 -> (row == playing ? "▶ " : "") + String.format("%02d", row + 1);
                case 1 -> e.displayTitle();
                case 2 -> e.artist() == null ? "" : e.artist();
                case 3 -> e.album() == null ? "" : e.album();
                case 4 -> e.length().isZero() ? "" : PlaylistPanel.time(e.length().toSeconds());
                default -> e.format();
            };
        }
    }
}

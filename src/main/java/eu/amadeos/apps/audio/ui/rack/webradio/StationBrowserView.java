package eu.amadeos.apps.audio.ui.rack.webradio;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.audio.domain.Station;
import eu.amadeos.apps.audio.ui.playlist.CoverColumn;
import eu.amadeos.apps.audio.ui.playlist.PlayingRenderer;
import eu.amadeos.apps.audio.ui.playlist.PlaylistPanel;
import eu.amadeos.apps.audio.ui.widget.Scale;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Stations in the playlist panel: favourites, search, genres and countries, as in dz-radio. The text field
 * searches radio-browser on the Search tab and filters the open list on the other tabs.
 */
public final class StationBrowserView {
    enum Tab {
        FAVORITES, SEARCH, GENRES, COUNTRIES;

        /** From {@code stations.tab.<tab>}. */
        String label() {
            return tr("stations.tab." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    final CoverColumn cover = new CoverColumn();
    final Model model = new Model();
    final JTable table = new JTable(model);
    final DefaultListModel<Choice> choices = new DefaultListModel<>();
    final JList<Choice> choiceList = new JList<>(choices);
    final JScrollPane choiceScroll = new JScrollPane(choiceList);
    final JButton play = new JButton(tr("stations.play"));
    final JButton add = new JButton(tr("stations.add"));
    final JButton remove = new JButton(tr("stations.remove"));
    final JButton up = new JButton(tr("stations.up"));
    final JButton down = new JButton(tr("stations.down"));
    final JTextField query = new JTextField(24);
    final JLabel queryLabel = new JLabel(tr("stations.tab.search"));
    final JLabel status = new JLabel(" ");
    final JPanel tabs = new JPanel(new GridLayout(1, Tab.values().length, 2, 0));
    final java.util.Map<Tab, JToggleButton> tabButtons = new java.util.EnumMap<>(Tab.class);
    final JPanel component;
    /** All genres or countries; {@link #choices} shows those matching the filter. */
    private List<Choice> allChoices = List.of();
    /** Set while the field is cleared by a tab change, so that does not count as typing. */
    private boolean clearing;

    /** An entry of the genre or country list: the key to look up, its name and the shown text. */
    record Choice(String key, String name, String label) {
        @Override
        public String toString() {
            return label;
        }

        boolean matches(String filter) {
            return filter.isEmpty() || contains(name, filter) || key.equalsIgnoreCase(filter);
        }
    }

    /** Genres or countries with their station count, sorted alphabetically by name in the user's language. */
    static List<Choice> sortedChoices(Map<String, Integer> counts, Function<String, String> name) {
        List<Choice> list = new ArrayList<>();
        counts.forEach((key, count) -> {
            String n = name.apply(key);
            list.add(new Choice(key, n, n + " (" + count + ")"));
        });
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.SECONDARY);
        list.sort(Comparator.comparing(Choice::name, collator));
        return list;
    }

    /** Case-insensitive substring match. */
    static boolean contains(String text, String filter) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    StationBrowserView(WebRadioPresenter p) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        PlayingRenderer.install(table, row -> row == model.playing);
        PlayingRenderer.widths(table, 52, 0, 70, 0, 64, 70);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) p.playSelected();
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "amade.play");
        table.getActionMap().put("amade.play", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                p.playSelected();
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) p.selectionChanged();
        });
        choiceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        choiceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && choiceList.getSelectedValue() != null) p.choose(choiceList.getSelectedValue());
        });
        choiceScroll.setPreferredSize(new Dimension(200, 10));
        choiceScroll.setVisible(false);
        play.addActionListener(e -> p.playSelected());
        add.addActionListener(e -> p.addFavorite());
        remove.addActionListener(e -> p.removeFavorite());
        up.addActionListener(e -> p.moveFavorite(-1));
        down.addActionListener(e -> p.moveFavorite(1));
        query.addActionListener(e -> p.queryEntered(query.getText()));
        query.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }

            private void changed() {
                if (!clearing) p.filter(query.getText());
            }
        });
        query.getAccessibleContext().setAccessibleName(tr("stations.search.accessible"));
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));

        ButtonGroup group = new ButtonGroup();
        tabs.setOpaque(false);
        for (Tab tab : Tab.values()) {
            JToggleButton b = new JToggleButton(tab.label());
            b.addActionListener(e -> p.showTab(tab));
            group.add(b);
            tabs.add(b);
            tabButtons.put(tab, b);
        }

        JPanel lists = new JPanel(new BorderLayout(Scale.px(8), 0));
        lists.setOpaque(false);
        lists.add(choiceScroll, BorderLayout.WEST);
        lists.add(PlaylistPanel.scroll(table), BorderLayout.CENTER);
        queryLabel.setLabelFor(query);
        component = PlaylistPanel.view(cover, lists, PlaylistPanel.buttonRow(
                new JComponent[]{play, add, remove, up, down, queryLabel, query}, new JComponent[]{status}));
    }

    void selectTab(Tab tab) {
        tabButtons.get(tab).setSelected(true);
        boolean favorites = tab == Tab.FAVORITES;
        remove.setVisible(favorites);
        up.setVisible(favorites);
        down.setVisible(favorites);
        add.setVisible(!favorites);
        choiceScroll.setVisible(tab == Tab.GENRES || tab == Tab.COUNTRIES);
        choiceScroll.getParent().revalidate();
        boolean search = tab == Tab.SEARCH;
        queryLabel.setText(tr(search ? "stations.tab.search" : "stations.filter"));
        query.getAccessibleContext().setAccessibleName(tr(search ? "stations.search.accessible" : "stations.filter.accessible"));
        clearing = true;
        query.setText("");
        clearing = false;
    }

    void setChoices(List<Choice> list, String filter) {
        allChoices = List.copyOf(list);
        filterChoices(filter);
    }

    void filterChoices(String filter) {
        choices.clear();
        for (Choice c : allChoices) if (c.matches(filter)) choices.addElement(c);
    }

    Station selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.stations.get(table.convertRowIndexToModel(row));
    }

    /** Stations of the current tab. */
    static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {tr("column.number"), tr("column.station"), tr("column.country"),
                tr("column.genre"), tr("column.codec"), tr("column.bitrate")};
        List<Station> stations = List.of();
        int playing = -1;

        void set(List<Station> list, Station current) {
            stations = List.copyOf(list);
            playing = indexOf(current);
            fireTableDataChanged();
        }

        void setPlaying(Station current) {
            playing = indexOf(current);
            fireTableDataChanged();
        }

        int indexOf(Station s) {
            if (s == null) return -1;
            for (int i = 0; i < stations.size(); i++) if (stations.get(i).sameAs(s)) return i;
            return -1;
        }

        @Override
        public int getRowCount() {
            return stations.size();
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
            Station s = stations.get(row);
            return switch (column) {
                case 0 -> (row == playing ? "▶ " : "") + (row + 1);
                case 1 -> s.name();
                case 2 -> s.country();
                case 3 -> s.genre();
                case 4 -> s.codec();
                default -> s.bitrate() > 0 ? s.bitrate() + "k" : "";
            };
        }
    }
}

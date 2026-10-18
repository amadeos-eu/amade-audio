package eu.amadeos.apps.audio.ui.playlist;

import eu.amadeos.apps.audio.domain.SourceId;
import eu.amadeos.apps.audio.ui.widget.Led;
import eu.amadeos.apps.audio.ui.widget.RackPanel;
import eu.amadeos.apps.audio.ui.widget.Scale;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * The panel under the racks. It shows the list of the active device: the player's playlist, the CD's
 * track list or the radio stations. Each device's view brings its own header text and tabs.
 */
public final class PlaylistPanel extends RackPanel {
    /** Height of the panel when shown, at 100 %. The list keeps its text size, so it does not shrink as far. */
    public static final int HEIGHT = 360;

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel title = new JLabel();
    private final JLabel info = RackPanel.subLabel(" ");
    private final JPanel tabs = new JPanel(new FlowLayout(FlowLayout.RIGHT, Scale.px(2), 0));
    private final Map<SourceId, Header> headers = new EnumMap<>(SourceId.class);
    private SourceId shown;

    /** Title, info line and tabs of one device's view. */
    public static final class Header {
        private final String title;
        private String info = " ";
        private final JComponent tabs;

        public Header(String title, JComponent tabs) {
            this.title = title;
            this.tabs = tabs;
        }
    }

    public PlaylistPanel() {
        super(new BorderLayout(0, Scale.px(8)));
        title.setIcon(new Led(Led.GREEN, () -> true));
        title.setIconTextGap(Scale.px(12));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        info.setFont(info.getFont().deriveFont(12f));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, Scale.px(14), 0));
        left.setOpaque(false);
        left.add(title);
        left.add(info);
        tabs.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(left, BorderLayout.WEST);
        header.add(tabs, BorderLayout.EAST);
        content.setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        setPreferredSize(new Dimension(100, Math.max(260, Scale.px(HEIGHT))));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, Math.max(160, Scale.px(180)));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    /** Registers a device's view. */
    public void addView(SourceId id, Header header, JComponent view) {
        headers.put(id, header);
        content.add(view, id.name());
    }

    /** Shows the view of the device. */
    public void show(SourceId id) {
        Header h = headers.get(id);
        if (h == null) return;
        shown = id;
        cards.show(content, id.name());
        title.setText(h.title);
        info.setText(h.info);
        tabs.removeAll();
        if (h.tabs != null) tabs.add(h.tabs);
        tabs.revalidate();
        tabs.repaint();
    }

    public SourceId shown() {
        return shown;
    }

    /** Info next to the title, e.g. {@code Player · 12 files · 54:12}. */
    public void setInfo(SourceId id, String text) {
        Header h = headers.get(id);
        if (h == null) return;
        h.info = text == null || text.isBlank() ? " " : text;
        if (id == shown) info.setText(h.info);
    }

    /** A view: cover column, list, and a row of buttons below. */
    public static JPanel view(CoverColumn cover, JComponent list, JComponent buttons) {
        JPanel center = new JPanel(new BorderLayout(Scale.px(14), 0));
        center.setOpaque(false);
        center.add(cover, BorderLayout.WEST);
        center.add(list, BorderLayout.CENTER);
        JPanel view = new JPanel(new BorderLayout(0, Scale.px(8)));
        view.setOpaque(false);
        view.add(center, BorderLayout.CENTER);
        if (buttons != null) view.add(buttons, BorderLayout.SOUTH);
        return view;
    }

    /** A row of buttons, left and right groups. */
    public static JPanel buttonRow(JComponent[] left, JComponent[] right) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT, Scale.px(8), 0));
        l.setOpaque(false);
        for (JComponent c : left) l.add(c);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, Scale.px(8), 0));
        r.setOpaque(false);
        for (JComponent c : right) r.add(c);
        row.add(l, BorderLayout.WEST);
        row.add(r, BorderLayout.EAST);
        return row;
    }

    /** A table in a scroll pane, styled for the playlist. */
    public static JScrollPane scroll(JTable table) {
        table.setRowHeight(22);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(UIManager.getColor("Table.background"));
        return scroll;
    }

    /** Formats a duration in seconds as {@code m:ss} or {@code h:mm:ss}. */
    public static String time(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = seconds / 60 % 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}

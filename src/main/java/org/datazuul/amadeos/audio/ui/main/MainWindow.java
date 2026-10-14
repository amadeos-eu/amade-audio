package org.datazuul.amadeos.audio.ui.main;

import org.datazuul.amadeos.audio.ui.playlist.PlaylistPanel;
import org.datazuul.amadeos.audio.ui.widget.RackRail;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The frameless window: the racks stacked in a dark rack frame, the playlist panel below. Racks can be
 * hidden; the window then shrinks.
 */
final class MainWindow extends JFrame {
    static final int WIDTH = 1240;
    private final Map<String, JComponent[]> racks = new LinkedHashMap<>();
    private final JPanel stack = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(RackRail.frame(AmadeUI.theme()));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    };
    private final PlaylistPanel playlist;
    private final JComponent playlistRail;

    MainWindow(HeaderRack header, Map<String, JComponent> rackComponents, PlaylistPanel playlist) {
        super("Audio Station");
        this.playlist = playlist;
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-audio.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        stack.add(header);
        stack.add(new RackRail(true));
        for (Map.Entry<String, JComponent> e : rackComponents.entrySet()) {
            JComponent rail = new RackRail(true);
            racks.put(e.getKey(), new JComponent[]{e.getValue(), rail});
            stack.add(e.getValue());
            stack.add(rail);
        }
        stack.add(playlist);
        playlistRail = new RackRail(false);
        stack.add(playlistRail);
        for (Component c : stack.getComponents()) {
            if (c instanceof JComponent jc) jc.setAlignmentX(LEFT_ALIGNMENT);
        }
        setContentPane(stack);
        header.enableDragging(this);
        relayout();
        setLocationRelativeTo(null);
    }

    void setRackVisible(String id, boolean visible) {
        JComponent[] parts = racks.get(id);
        if (parts == null) return;
        for (JComponent c : parts) c.setVisible(visible);
        relayout();
    }

    void setPlaylistVisible(boolean visible) {
        if (playlist.isVisible() == visible) return;
        playlist.setVisible(visible);
        playlistRail.setVisible(visible);
        relayout();
    }

    /** Fits the window to the visible racks, keeping its top edge. */
    void relayout() {
        Point location = isShowing() ? getLocation() : null;
        stack.revalidate();
        Dimension pref = stack.getPreferredSize();
        setSize(new Dimension(Math.max(WIDTH, pref.width), pref.height));
        validate();
        if (location != null) setLocation(location);
    }
}

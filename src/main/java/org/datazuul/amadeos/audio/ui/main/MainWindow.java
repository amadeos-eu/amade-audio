package org.datazuul.amadeos.audio.ui.main;

import static org.datazuul.amadeos.audio.i18n.Texts.tr;

import org.datazuul.amadeos.audio.ui.playlist.PlaylistPanel;
import org.datazuul.amadeos.audio.ui.widget.RackRail;
import org.datazuul.amadeos.audio.ui.widget.Scale;
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
    /** Width at 100 %. */
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
        super(tr("app.title"));
        this.playlist = playlist;
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-audio.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBorder(Scale.empty(6, 6, 0, 6));
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
        Scale.fonts(stack);
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
        Rectangle screen = usableScreen();
        setSize(new Dimension(Math.max(Scale.px(WIDTH), pref.width), Math.min(pref.height, screen.height)));
        validate();
        if (location != null) {
            // Keep the bottom on the screen when the window grows.
            int y = Math.min(location.y, screen.y + screen.height - getHeight());
            setLocation(location.x, Math.max(screen.y, y));
        }
    }

    /** The screen area without panels, of the screen the window is on. */
    private Rectangle usableScreen() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    }
}

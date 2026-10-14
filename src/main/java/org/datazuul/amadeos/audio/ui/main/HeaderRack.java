package org.datazuul.amadeos.audio.ui.main;

import static org.datazuul.amadeos.audio.i18n.Texts.tr;

import org.datazuul.amadeos.audio.ui.widget.Led;
import org.datazuul.amadeos.audio.ui.widget.RackPanel;
import org.datazuul.amadeos.audio.ui.widget.Widget;
import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The top rack: menu button, logo, the rack buttons with the active device's LED, and the window
 * buttons. It replaces the window frame; drag it to move the window.
 */
final class HeaderRack extends RackPanel {
    /** Rack ids; the button texts come from {@code rack.<id>}. */
    static final String[] RACKS = {"mixer", "cd", "player", "webradio"};

    final JButton menu = new JButton(new MenuIcon());
    final Map<String, JToggleButton> rackButtons = new LinkedHashMap<>();
    final JButton minimize = new JButton(new WindowIcon(false));
    final JButton close = new JButton(new WindowIcon(true));
    final JButton power = new JButton(tr("header.power"));

    HeaderRack(MainPresenter p, Predicate<String> lit) {
        super(new BorderLayout(16, 0));
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        menu.setToolTipText(tr("header.menu"));
        menu.getAccessibleContext().setAccessibleName(tr("header.menu"));
        menu.setMargin(new Insets(4, 8, 4, 8));
        JPopupMenu popup = new JPopupMenu();
        popup.add(item(tr("menu.open-files"), p::openFiles));
        popup.add(item(tr("menu.open-folder"), p::openFolder));
        popup.addSeparator();
        popup.add(item(tr("menu.settings"), () -> p.openOptions("Recording")));
        popup.add(item(tr("menu.about"), p::about));
        popup.addSeparator();
        popup.add(item(tr("menu.quit"), p::quit));
        menu.addActionListener(e -> popup.show(menu, 0, menu.getHeight()));

        JPanel buttons = new JPanel(new GridLayout(1, RACKS.length, 0, 0));
        buttons.setOpaque(false);
        for (String rack : RACKS) {
            String name = tr("rack." + rack);
            JToggleButton b = new JToggleButton(name);
            b.setIcon(new Led(Led.GREEN, () -> lit.test(rack)));
            b.setIconTextGap(10);
            b.setSelected(true);
            b.setToolTipText(tr("header.show-rack", name));
            b.setMargin(new Insets(8, 14, 8, 14));
            b.addActionListener(e -> p.setRackVisible(rack, b.isSelected()));
            rackButtons.put(rack, b);
            buttons.add(b);
        }

        minimize.setToolTipText(tr("header.minimize"));
        minimize.getAccessibleContext().setAccessibleName(tr("header.minimize"));
        close.setToolTipText(tr("header.close"));
        close.getAccessibleContext().setAccessibleName(tr("header.close"));
        for (JButton b : new JButton[]{minimize, close}) b.setMargin(new Insets(2, 6, 2, 6));
        minimize.addActionListener(e -> p.minimize());
        close.addActionListener(e -> p.quit());
        power.setToolTipText(tr("header.power.tooltip"));
        power.setMargin(new Insets(2, 8, 2, 8));
        power.setFont(power.getFont().deriveFont(11f));
        power.addActionListener(e -> p.quit());
        JPanel windowButtons = new JPanel(new BorderLayout(0, 4));
        windowButtons.setOpaque(false);
        JPanel top = new JPanel(new GridLayout(1, 2, 4, 0));
        top.setOpaque(false);
        top.add(minimize);
        top.add(close);
        windowButtons.add(top, BorderLayout.NORTH);
        windowButtons.add(power, BorderLayout.SOUTH);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        JPanel menuWrap = new JPanel(new GridBagLayout());
        menuWrap.setOpaque(false);
        menuWrap.add(menu);
        left.add(menuWrap);
        left.add(new Logo());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        JPanel buttonsWrap = new JPanel(new GridBagLayout());
        buttonsWrap.setOpaque(false);
        buttonsWrap.add(buttons);
        right.add(buttonsWrap);
        right.add(windowButtons);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    /** Lets the user move the window by dragging the rack (also its logo). */
    void enableDragging(Window window) {
        MouseAdapter drag = new MouseAdapter() {
            private Point grab;

            @Override
            public void mousePressed(MouseEvent e) {
                grab = e.getLocationOnScreen();
                grab.translate(-window.getX(), -window.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (grab == null) return;
                Point p = e.getLocationOnScreen();
                window.setLocation(p.x - grab.x, p.y - grab.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                grab = null;
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
        for (Component c : ((JPanel) getComponent(0)).getComponents()) {
            if (c instanceof Logo) {
                c.addMouseListener(drag);
                c.addMouseMotionListener(drag);
            }
        }
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    /** "AMADE·AUDIO for amadeOS" in the theme's title colour with an engraved shadow. */
    static final class Logo extends Widget {
        private static final Font BIG = new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 42);
        private static final Font SMALL = new Font(Font.SERIF, Font.ITALIC, 17);

        Logo() {
            setPreferredSize(new Dimension(450, 56));
            getAccessibleContext().setAccessibleName(tr("app.title"));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            DesktopTheme t = AmadeUI.theme();
            g2.setFont(BIG);
            String text = "AMADE·AUDIO";
            int y = getHeight() - 12;
            g2.setColor(t.highlight());
            g2.drawString(text, 3, y + 3);
            g2.setColor(t.darkShadow());
            g2.drawString(text, 2, y + 2);
            g2.setColor(accent(t));
            g2.drawString(text, 0, y);
            int w = g2.getFontMetrics().stringWidth(text);
            g2.setFont(SMALL);
            g2.setColor(t.textInactive());
            g2.drawString(tr("header.for-amadeos"), w + 14, y);
            g2.dispose();
        }

        /** The active title colour, or a lighter one if it is too close to the rack colour. */
        static Color accent(DesktopTheme t) {
            Color c = t.activeTitleBg();
            if (distance(c, t.base()) < 60) c = t.isDark() ? new Color(0x7F, 0xB3, 0xE6) : t.darkShadow();
            return c;
        }

        private static int distance(Color a, Color b) {
            return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue());
        }
    }

    /** Three bars, the menu symbol. */
    private static final class MenuIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(c.getForeground());
            for (int i = 0; i < 3; i++) g.fillRect(x, y + 1 + i * 5, 16, 2);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    /** Minimize (a small raised square) and close (a cross), as in the CDE window frame. */
    private static final class WindowIcon implements Icon {
        private final boolean close;

        WindowIcon(boolean close) {
            this.close = close;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (close) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(x + 2, y + 2, x + 9, y + 9);
                g2.drawLine(x + 9, y + 2, x + 2, y + 9);
                g2.dispose();
            } else {
                AmadeUI.paintBevel(g, x + 3, y + 3, 6, 6, true, 1);
            }
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }
}

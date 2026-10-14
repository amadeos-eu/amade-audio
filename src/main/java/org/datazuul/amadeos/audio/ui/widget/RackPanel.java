package org.datazuul.amadeos.audio.ui.widget;

import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;

/** The front plate of a rack: theme colour with a raised bevel. */
public class RackPanel extends JPanel {
    public RackPanel(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(10, 14, 10, 14)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(AmadeUI.theme().base());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** A vertical column of side buttons (Power, Eject, ...), all the same width. */
    public static JPanel sideButtons(AbstractButton... buttons) {
        JPanel column = new JPanel(new GridLayout(buttons.length, 1, 0, 8));
        column.setOpaque(false);
        for (AbstractButton b : buttons) {
            b.setPreferredSize(new Dimension(100, 32));
            column.add(b);
        }
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(column);
        return wrap;
    }

    /** A sunken info window in the theme's sunken colour. */
    public static JPanel infoWindow(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(AmadeUI.theme().sunkenBg());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(true);
        panel.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        return panel;
    }

    /** Small spaced capitals like {@code AUDIO CD · /dev/sr0} in the inactive text colour. */
    public static JLabel subLabel(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(AmadeUI.theme().textInactive());
                super.paintComponent(g);
            }
        };
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        return label;
    }
}

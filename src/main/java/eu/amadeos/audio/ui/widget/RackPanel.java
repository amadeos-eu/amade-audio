package eu.amadeos.audio.ui.widget;

import static eu.amadeos.audio.i18n.Texts.tr;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;

/** The front plate of a rack: theme colour with a raised bevel. */
public class RackPanel extends JPanel {
    private DeviceLabel label;
    public RackPanel(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), Scale.empty(10, 14, 10, 14)));
    }

    /** Racks keep their height; only the playlist panel shrinks on small screens. */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(super.getMinimumSize().width, getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
    }

    /** Engraves the device's name into the rack front. */
    public void setDeviceLabel(String name, DeviceLabel.Symbol symbol) {
        label = new DeviceLabel(name, symbol);
        Insets extra = label.space();
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(
                Scale.px(10) + extra.top, Scale.px(14) + extra.left, Scale.px(10), Scale.px(14))));
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(AmadeUI.theme().base());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (label != null) {
            Insets in = getInsets();
            Graphics2D g2 = (Graphics2D) g.create();
            label.paint(g2, new Rectangle(in.left, in.top, getWidth() - in.left - in.right, getHeight() - in.top - in.bottom));
            g2.dispose();
        }
    }

    /** A vertical column of side buttons (Power, Eject, ...), all the same width. */
    public static JPanel sideButtons(AbstractButton... buttons) {
        JPanel column = new JPanel(new GridLayout(buttons.length, 1, 0, Scale.px(8)));
        column.setOpaque(false);
        for (AbstractButton b : buttons) {
            b.setPreferredSize(new Dimension(sideWidth(b), Scale.px(32)));
            column.add(b);
        }
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(column);
        return wrap;
    }

    /** Texts of all side buttons; every rack uses the width of the longest, so the racks stay aligned. */
    private static final String[] SIDE_TEXTS = {"rack.power", "cd.eject", "player.open", "radio.stations", "mixer.mute"};

    /** At least 100 pixels (at 100 %), wider when a translation needs it. */
    static int sideWidth(AbstractButton sample) {
        // The fonts are scaled once the rack is in the window, so measure with the scaled font already.
        Font font = sample.getFont();
        FontMetrics fm = sample.getFontMetrics(font.deriveFont(Scale.text(font.getSize2D())));
        Insets in = sample.getInsets();
        int width = Scale.px(100);
        for (String key : SIDE_TEXTS) {
            width = Math.max(width, fm.stringWidth(tr(key)) + Scale.px(16) + sample.getIconTextGap() + in.left + in.right + Scale.px(8));
        }
        return width;
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
        panel.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), Scale.empty(6, 12, 6, 12)));
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

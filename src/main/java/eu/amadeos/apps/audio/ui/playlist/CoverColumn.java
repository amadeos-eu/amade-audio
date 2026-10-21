package eu.amadeos.apps.audio.ui.playlist;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.audio.ui.widget.CoverView;
import eu.amadeos.apps.audio.ui.widget.RackPanel;
import eu.amadeos.apps.audio.ui.widget.Scale;

import javax.swing.*;
import java.awt.*;

/** Large cover with album, artist, details and the cover's source, left of the playlist. */
public final class CoverColumn extends JPanel {
    /** Cover size at 100 %. */
    public static final int SIZE = 196;
    private final CoverView cover = new CoverView(SIZE);
    private final JLabel line1 = new JLabel(" ");
    private final JLabel line2 = new JLabel(" ");
    private final JLabel line3 = RackPanel.subLabel(" ");
    private final JLabel source = RackPanel.subLabel(" ");

    public CoverColumn() {
        super(new BorderLayout(0, Scale.px(6)));
        setOpaque(false);
        line1.setFont(line1.getFont().deriveFont(Font.BOLD, 14f));
        line2.setFont(line2.getFont().deriveFont(Font.PLAIN, 13f));
        line3.setFont(line3.getFont().deriveFont(12f));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        for (JLabel l : new JLabel[]{line1, line2, line3}) {
            l.setAlignmentX(LEFT_ALIGNMENT);
            text.add(l);
            text.add(Box.createVerticalStrut(Scale.px(2)));
        }
        add(cover, BorderLayout.NORTH);
        add(text, BorderLayout.CENTER);
        add(source, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(Scale.px(SIZE), 10));
    }

    public void setText(String first, String second, String third) {
        line1.setText(html(first));
        line2.setText(html(second));
        line3.setText(third == null || third.isBlank() ? " " : third);
    }

    /** The image, or null for the placeholder; {@code from} is shown below, e.g. {@code Cover Art Archive}. */
    public void setImage(byte[] image, String from) {
        cover.setImage(image);
        source.setText(image == null || from == null || from.isBlank() ? " " : tr("cover.source", from));
    }

    private static String html(String s) {
        if (s == null || s.isBlank()) return " ";
        String escaped = s.replace("&", "&amp;").replace("<", "&lt;");
        return "<html><body style='width:" + (Scale.px(SIZE) - 8) + "px'>" + escaped + "</body></html>";
    }
}

package eu.amadeos.apps.audio.ui.widget;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** A cover or station logo, scaled to fit; a disc symbol in the theme's colours when there is none. */
public final class CoverView extends Widget {
    private BufferedImage image;

    /** @param size at 100 %, see {@link Scale} */
    public CoverView(int size) {
        Dimension d = Scale.size(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        getAccessibleContext().setAccessibleName(tr("widget.cover"));
    }

    /** Shows the image; null or undecodable data shows the placeholder. */
    public void setImage(byte[] data) {
        BufferedImage decoded = null;
        if (data != null) {
            try {
                decoded = ImageIO.read(new ByteArrayInputStream(data));
            } catch (IOException | RuntimeException e) {
                decoded = null;
            }
        }
        image = decoded;
        repaint();
    }

    public boolean hasImage() {
        return image != null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        DesktopTheme t = AmadeUI.theme();
        int w = getWidth();
        int h = getHeight();
        g2.setColor(t.fieldBackground());
        g2.fillRect(0, 0, w, h);
        if (image != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double scale = Math.min((w - 4.0) / image.getWidth(), (h - 4.0) / image.getHeight());
            int iw = (int) Math.round(image.getWidth() * scale);
            int ih = (int) Math.round(image.getHeight() * scale);
            g2.drawImage(image, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(t.textInactive());
            int d = Math.min(w, h) * 2 / 5;
            int cx = w / 2;
            int cy = h / 2;
            g2.setStroke(new BasicStroke(Math.max(1.5f, d / 30f)));
            g2.drawOval(cx - d, cy - d, 2 * d, 2 * d);
            int hole = Math.max(3, d / 4);
            g2.drawOval(cx - hole, cy - hole, 2 * hole, 2 * hole);
            g2.drawArc(cx - d * 7 / 10, cy - d * 7 / 10, d * 14 / 10, d * 14 / 10, 20, 70);
        }
        AmadeUI.paintBevel(g2, 0, 0, w, h, false, 2);
        g2.dispose();
    }

    @Override
    protected javax.accessibility.AccessibleRole role() {
        return javax.accessibility.AccessibleRole.ICON;
    }
}

package eu.amadeos.audio.ui.widget;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class ScaleTest {
    @AfterEach
    void reset() {
        Scale.setPercent(100);
    }

    @Test
    void lengthsShrinkButNeverVanish() {
        Scale.setPercent(60);
        assertEquals(744, Scale.px(1240));
        assertEquals(1, Scale.px(1));
        assertEquals(0, Scale.px(0));
        assertEquals(new Dimension(30, 20), Scale.size(50, 34));
        Scale.setPercent(100);
        assertEquals(1240, Scale.px(1240));
    }

    @Test
    void textsStayReadable() {
        Scale.setPercent(60);
        assertEquals(9f, Scale.text(12), 0.001f, "not below the minimum");
        assertEquals(8f, Scale.text(8), 0.001f, "small texts are not made larger");
        assertEquals(25.2f, Scale.text(42), 0.001f);
        Scale.setPercent(100);
        assertEquals(12f, Scale.text(12), 0.001f);
    }

    @Test
    void fontsOfARackAreScaledOnceAndListsKeepTheirs() {
        Scale.setPercent(60);
        JPanel rack = new JPanel();
        JLabel title = new JLabel("Title");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        JTable list = new JTable(1, 1);
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        JLabel kept = new JLabel("kept");
        kept.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        kept.putClientProperty(Scale.KEEP, Boolean.TRUE);
        rack.add(title);
        rack.add(new JScrollPane(list));
        rack.add(kept);

        Scale.fonts(rack);
        Scale.fonts(rack);
        assertEquals(12f, title.getFont().getSize2D(), 0.001f);
        assertTrue(title.getFont().isBold());
        assertEquals(13f, list.getFont().getSize2D(), 0.001f);
        assertEquals(14f, kept.getFont().getSize2D(), 0.001f);

        JButton later = new JButton("Later");
        later.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        rack.add(later);
        assertEquals(12f, later.getFont().getSize2D(), 0.001f, "added after the rack was scaled");
    }

    @Test
    void nothingChangesAtFullSize() {
        JLabel label = new JLabel("x");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        Scale.fonts(label);
        assertEquals(12f, label.getFont().getSize2D(), 0.001f);
    }
}

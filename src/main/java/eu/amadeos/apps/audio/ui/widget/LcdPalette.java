package eu.amadeos.apps.audio.ui.widget;

import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.commons.swing.AmadeUI;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Glow colour of the LCDs. {@code theme} picks a colour that suits the desktop theme (amber for Desert,
 * blue for Brocade and Dark Slate, green otherwise); the user may fix one in the options.
 */
public final class LcdPalette {
    public static final Map<String, Color> FIXED = new LinkedHashMap<>();

    static {
        FIXED.put("green", new Color(0x5C, 0xFF, 0x5C));
        FIXED.put("amber", new Color(0xFF, 0xB2, 0x3F));
        FIXED.put("blue", new Color(0x5E, 0xC8, 0xFF));
        FIXED.put("red", new Color(0xFF, 0x5A, 0x4A));
        FIXED.put("white", new Color(0xE8, 0xEC, 0xF0));
    }

    public static final Color BACKGROUND = new Color(0x05, 0x08, 0x06);
    public static final Color WARN = new Color(0xFF, 0xD2, 0x3F);
    public static final Color PEAK = new Color(0xFF, 0x4B, 0x3A);

    private static volatile String setting = "theme";

    private LcdPalette() {
    }

    /** {@code theme} or a key of {@link #FIXED}. */
    public static void setSetting(String value) {
        setting = value == null || !(value.equals("theme") || FIXED.containsKey(value)) ? "theme" : value;
    }

    public static String setting() {
        return setting;
    }

    /** The glow colour for the current theme and setting. */
    public static Color glow() {
        Color fixed = FIXED.get(setting);
        return fixed != null ? fixed : forTheme(AmadeUI.theme());
    }

    // --8<-- [start:theme]
    static Color forTheme(DesktopTheme theme) {
        return switch (theme.id()) {
            case "cde-desert" -> FIXED.get("amber");
            case "cde-brocade", "dark-slate" -> FIXED.get("blue");
            default -> FIXED.get("green");
        };
    }
    // --8<-- [end:theme]

    /** Unlit segments and dots: the glow colour faintly on black. */
    public static Color ghost(Color glow) {
        return mix(glow, BACKGROUND, 0.16);
    }

    /** Text of a device that is not active. */
    public static Color dimmed(Color glow) {
        return mix(glow, BACKGROUND, 0.32);
    }

    static Color mix(Color c, Color base, double weightOfC) {
        return new Color(
                (int) Math.round(c.getRed() * weightOfC + base.getRed() * (1 - weightOfC)),
                (int) Math.round(c.getGreen() * weightOfC + base.getGreen() * (1 - weightOfC)),
                (int) Math.round(c.getBlue() * weightOfC + base.getBlue() * (1 - weightOfC)));
    }
}

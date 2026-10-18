package eu.amadeos.audio.i18n;

import eu.amadeos.commons.i18n.Messages;

import java.util.Locale;

/**
 * The texts of Audio Station in the language {@code AmadeApplication} chose (the default locale), from
 * {@code eu/amadeos/audio/messages*.properties}. Loaded on first use.
 */
public final class Texts {
    /** Base name of the text files. */
    public static final String BUNDLE = "eu.amadeos.audio.messages";
    private static volatile Messages messages;

    private Texts() {
    }

    public static Messages messages() {
        Messages m = messages;
        if (m == null || !m.locale().equals(Locale.getDefault())) {
            m = Messages.load(Texts.class, BUNDLE, Locale.getDefault());
            messages = m;
        }
        return m;
    }

    /** The text for the key. */
    public static String tr(String key) {
        return messages().get(key);
    }

    /** The text with {@code {0}}, {@code {1}}, ... filled in. */
    public static String tr(String key, Object... arguments) {
        return messages().format(key, arguments);
    }
}

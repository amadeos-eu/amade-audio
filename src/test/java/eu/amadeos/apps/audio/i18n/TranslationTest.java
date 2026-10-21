package eu.amadeos.apps.audio.i18n;

import eu.amadeos.apps.audio.bootstrap.AmadeAudioApplication;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Texts;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(AmadeAudioApplication.class, Texts.bundle()));
    }

    @Test
    void keysBuiltFromIdsExist() {
        Messages english = Messages.load(AmadeAudioApplication.class, Texts.bundle(), Locale.ENGLISH);
        List<String> keys = List.of("rack.mixer", "rack.cd", "rack.player", "rack.webradio",
                "source.cd", "source.player", "source.webradio",
                "options.page.Recording", "options.page.Display", "options.page.Network",
                "options.lcd.theme", "options.lcd.green", "options.lcd.amber", "options.lcd.blue", "options.lcd.red",
                "options.lcd.white", "stations.tab.favorites", "stations.tab.search", "stations.tab.genres",
                "stations.tab.countries", "transport.previous", "transport.next", "transport.rewind",
                "transport.forward", "transport.stop", "transport.play", "transport.pause", "transport.record",
                "transport.eject", "transport.favorite");
        for (String key : keys) assertTrue(english.has(key), key);
    }

    @Test
    void countsAndNumbersFollowTheLanguage() {
        Messages english = Messages.load(AmadeAudioApplication.class, Texts.bundle(), Locale.ENGLISH);
        assertEquals("no tracks", english.format("cd.tracks", 0));
        assertEquals("1 track", english.format("cd.tracks", 1));
        assertEquals("12 tracks", english.format("cd.tracks", 12));
        assertEquals("Track 07", english.format("cd.track-title", 7));
        assertEquals("3 stations for \"jazz\"", english.format("stations.found", 3, "\"jazz\""));
        Messages german = Messages.load(AmadeAudioApplication.class, Texts.bundle(), Locale.GERMANY);
        assertEquals("1.234 Dateien", german.format("player.files", 1234));
        assertEquals("Titel 03", german.format("cd.track-title", 3));
        Messages french = Messages.load(AmadeAudioApplication.class, Texts.bundle(), Locale.FRANCE);
        assertEquals("Impossible d'éjecter : busy", french.format("cd.error.eject", "busy"));
        for (Language language : Language.values()) {
            Messages m = Messages.load(AmadeAudioApplication.class, Texts.bundle(), language.locale());
            assertFalse(m.format("stations.found", 0, "x").startsWith("!"), language.code());
        }
    }
}

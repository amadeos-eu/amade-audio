package eu.amadeos.audio.ui.rack.webradio;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StationBrowserViewTest {
    @Test
    void choicesAreSortedAlphabeticallyByName() {
        // As radio-browser returns them: by station count
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Rock", 900);
        counts.put("jazz", 500);
        counts.put("électronique", 300);
        counts.put("Ambient", 100);
        List<StationBrowserView.Choice> choices = StationBrowserView.sortedChoices(counts, k -> k);
        assertEquals(List.of("Ambient", "électronique", "jazz", "Rock"),
                choices.stream().map(StationBrowserView.Choice::name).toList());
        assertEquals("jazz (500)", choices.get(2).label());
        assertEquals("jazz", choices.get(2).key());
    }

    @Test
    void countriesSortByTheirShownName() {
        Map<String, Integer> counts = new LinkedHashMap<>(Map.of("DE", 1, "AT", 2, "CH", 3));
        Map<String, String> names = Map.of("DE", "Germany", "AT", "Austria", "CH", "Switzerland");
        assertEquals(List.of("AT", "DE", "CH"),
                StationBrowserView.sortedChoices(counts, names::get).stream().map(StationBrowserView.Choice::key).toList());
    }

    @Test
    void filterMatchesNameOrKeyIgnoringCase() {
        StationBrowserView.Choice germany = new StationBrowserView.Choice("DE", "Germany", "Germany (120)");
        assertTrue(germany.matches(""));
        assertTrue(germany.matches("germ"));
        assertTrue(germany.matches("de"));
        assertFalse(germany.matches("120"), "the station count is not part of the name");
        assertFalse(germany.matches("aus"));
    }
}

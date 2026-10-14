package org.datazuul.amadeos.audio.service.cd;

import java.util.List;

/** CD-Text of a disc: album title and performer, and per audio track the title and performer. */
public record CdText(String album, String performer, List<String> titles, List<String> performers) {
    public static final CdText NONE = new CdText("", "", List.of(), List.of());

    public CdText {
        album = album == null ? "" : album.strip();
        performer = performer == null ? "" : performer.strip();
        titles = List.copyOf(titles);
        performers = List.copyOf(performers);
    }

    public boolean isEmpty() {
        return album.isEmpty() && titles.stream().allMatch(String::isBlank);
    }
}

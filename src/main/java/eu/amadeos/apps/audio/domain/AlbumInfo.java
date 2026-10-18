package eu.amadeos.apps.audio.domain;

import static eu.amadeos.apps.audio.i18n.Texts.tr;

import java.util.ArrayList;
import java.util.List;

/**
 * What is known about a CD: from CD-Text, MusicBrainz or entered by hand.
 *
 * @param titles         one entry per track of the TOC, in order; blank if unknown
 * @param artists        track artists, blank where the album artist applies
 * @param releaseId      MusicBrainz release MBID, empty if not from MusicBrainz
 * @param releaseGroupId MusicBrainz release group MBID, for cover art fallback
 * @param source         e.g. {@code CD-Text}, {@code MusicBrainz}, {@code Edited}
 */
public record AlbumInfo(String album, String artist, String year, String label, List<String> titles,
                        List<String> artists, String releaseId, String releaseGroupId, String source) {
    public AlbumInfo {
        album = nn(album);
        artist = nn(artist);
        year = nn(year);
        label = nn(label);
        titles = List.copyOf(titles.stream().map(AlbumInfo::nn).toList());
        artists = List.copyOf(artists.stream().map(AlbumInfo::nn).toList());
        releaseId = nn(releaseId);
        releaseGroupId = nn(releaseGroupId);
        source = nn(source);
    }

    /** Nothing known yet for a disc with the given number of tracks. */
    public static AlbumInfo unknown(int trackCount) {
        List<String> blank = new ArrayList<>();
        for (int i = 0; i < trackCount; i++) blank.add("");
        return new AlbumInfo("", "", "", "", blank, blank, "", "", "");
    }

    public boolean isKnown() {
        return !album.isEmpty() || titles.stream().anyMatch(t -> !t.isEmpty());
    }

    /** Title of the track at the given index (0-based), or {@code Track nn}. */
    public String title(int index) {
        String t = index >= 0 && index < titles.size() ? titles.get(index) : "";
        return t.isEmpty() ? tr("cd.track-title", index + 1) : t;
    }

    /** Artist of the track at the given index, or the album artist. */
    public String artist(int index) {
        String a = index >= 0 && index < artists.size() ? artists.get(index) : "";
        return a.isEmpty() ? artist : a;
    }

    public AlbumInfo withTitles(List<String> newTitles, String newSource) {
        return new AlbumInfo(album, artist, year, label, newTitles, artists, releaseId, releaseGroupId, newSource);
    }

    public AlbumInfo withAlbum(String newAlbum, String newArtist, String newSource) {
        return new AlbumInfo(newAlbum, newArtist, year, label, titles, artists, releaseId, releaseGroupId, newSource);
    }

    private static String nn(String s) {
        return s == null ? "" : s.strip();
    }
}

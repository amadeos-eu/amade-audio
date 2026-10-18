package eu.amadeos.audio.service.metadata;

import com.google.inject.Inject;
import eu.amadeos.audio.domain.AlbumInfo;
import eu.amadeos.audio.service.cd.CdDisc;
import eu.amadeos.audio.service.cd.CdText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Titles of a CD, in this order: what the user entered or chose before (cache), CD-Text, MusicBrainz,
 * or nothing (then the tracks are called {@code Track 01} and can be named by hand).
 */
public final class CdMetadataService {
    private final MusicBrainzClient musicBrainz;
    private final MetadataCache cache;

    @Inject
    public CdMetadataService(MusicBrainzClient musicBrainz, MetadataCache cache) {
        this.musicBrainz = musicBrainz;
        this.cache = cache;
    }

    /** What is known without the network: cache, then CD-Text, else unknown. */
    public AlbumInfo offline(CdDisc disc) {
        int tracks = disc.toc().audioTracks().size();
        return cache.album(disc.discId()).orElseGet(() -> fromCdText(disc.text(), tracks));
    }

    /** True if the offline answer is final (from the cache) and no online lookup is needed. */
    public boolean isCached(CdDisc disc) {
        return cache.album(disc.discId()).isPresent();
    }

    /** Candidates from MusicBrainz; the caller lets the user choose if there are several. */
    public List<AlbumInfo> lookup(CdDisc disc) throws IOException {
        return musicBrainz.lookup(disc.discId(), disc.toc());
    }

    /** Remembers the chosen or edited data for this disc. */
    public void remember(CdDisc disc, AlbumInfo info) {
        cache.storeAlbum(disc.discId(), info);
    }

    static AlbumInfo fromCdText(CdText text, int tracks) {
        if (text.isEmpty()) return AlbumInfo.unknown(tracks);
        List<String> titles = new ArrayList<>(text.titles());
        List<String> artists = new ArrayList<>();
        for (String p : text.performers()) artists.add(p.equals(text.performer()) ? "" : p);
        while (titles.size() < tracks) titles.add("");
        while (artists.size() < tracks) artists.add("");
        return new AlbumInfo(text.album(), text.performer(), "", "", titles.subList(0, tracks),
                artists.subList(0, tracks), "", "", "CD-Text");
    }
}

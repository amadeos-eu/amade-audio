package org.datazuul.amadeos.audio.domain;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Table of contents of an audio CD.
 *
 * @param leadOutLba first sector after the last track
 */
public record CdToc(int firstTrack, int lastTrack, int leadOutLba, List<CdTrack> tracks) {
    public CdToc {
        tracks = List.copyOf(tracks);
    }

    /**
     * Builds the TOC from track start addresses (LBA) and the lead-out, as libcdio reports them.
     *
     * @param audio per track, false for data tracks; null means all audio
     */
    public static CdToc of(int firstTrack, int[] lbas, int leadOutLba, boolean[] audio) {
        CdTrack[] tracks = new CdTrack[lbas.length];
        for (int i = 0; i < lbas.length; i++) {
            int end = i + 1 < lbas.length ? lbas[i + 1] : leadOutLba;
            tracks[i] = new CdTrack(firstTrack + i, lbas[i], Math.max(0, end - lbas[i]), audio == null || audio[i]);
        }
        return new CdToc(firstTrack, firstTrack + lbas.length - 1, leadOutLba, List.of(tracks));
    }

    public Optional<CdTrack> track(int number) {
        return tracks.stream().filter(t -> t.number() == number).findFirst();
    }

    public List<CdTrack> audioTracks() {
        return tracks.stream().filter(CdTrack::audio).toList();
    }

    public Duration length() {
        return audioTracks().stream().map(CdTrack::length).reduce(Duration.ZERO, Duration::plus);
    }
}

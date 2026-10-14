package org.datazuul.amadeos.audio.service.cd;

import org.datazuul.amadeos.audio.domain.CdToc;
import org.datazuul.amadeos.audio.domain.CdTrack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * MusicBrainz disc ID and TOC string, as described in "Disc ID Calculation" on musicbrainz.org.
 * For enhanced CDs the data track is left out and the lead-out moved before it, as libdiscid does.
 */
public final class DiscId {
    /** Gap between the last audio session and a following data session. */
    static final int DATA_TRACK_GAP = 11400;

    private DiscId() {
    }

    /** The 28-character disc ID. */
    public static String of(CdToc toc) {
        Audio a = audio(toc);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%02X", a.first));
        sb.append(String.format("%02X", a.last));
        sb.append(String.format("%08X", a.leadOut));
        for (int i = 1; i <= 99; i++) {
            sb.append(String.format("%08X", offsetOf(a, i)));
        }
        try {
            byte[] sha = MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(sha).replace('+', '.').replace('/', '_').replace('=', '-');
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 missing", e);
        }
    }

    /** {@code first last leadout offset1 offset2 ...} joined with {@code +}, for the {@code toc} lookup. */
    public static String tocString(CdToc toc) {
        Audio a = audio(toc);
        StringBuilder sb = new StringBuilder().append(a.first).append('+').append(a.last).append('+').append(a.leadOut);
        for (CdTrack t : a.tracks) sb.append('+').append(t.lba());
        return sb.toString();
    }

    private static int offsetOf(Audio a, int number) {
        for (CdTrack t : a.tracks) if (t.number() == number) return t.lba();
        return 0;
    }

    private record Audio(int first, int last, int leadOut, List<CdTrack> tracks) {
    }

    private static Audio audio(CdToc toc) {
        List<CdTrack> audio = toc.audioTracks();
        if (audio.isEmpty()) throw new IllegalArgumentException("No audio tracks");
        int first = audio.get(0).number();
        int last = audio.get(audio.size() - 1).number();
        int leadOut = toc.leadOutLba();
        // A data track after the audio tracks (enhanced CD): the audio session ends before it.
        for (CdTrack t : toc.tracks()) {
            if (!t.audio() && t.number() > last) {
                leadOut = t.lba() - DATA_TRACK_GAP;
                break;
            }
        }
        return new Audio(first, last, leadOut, audio);
    }
}

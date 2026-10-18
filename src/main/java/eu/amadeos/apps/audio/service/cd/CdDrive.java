package eu.amadeos.apps.audio.service.cd;

import eu.amadeos.apps.audio.domain.CdTrack;
import eu.amadeos.apps.audio.service.engine.PcmSource;

import java.io.IOException;
import java.util.Optional;

/** A CD drive. Methods block on the drive; call them from a background thread. */
public interface CdDrive {
    /** Device path, e.g. {@code /dev/sr0}, or drive letter on Windows, e.g. {@code D:}. */
    String device();

    /** True if the drive can be used at all (library present, device exists). */
    boolean isAvailable();

    /** The audio CD in the drive, or empty if there is none (no disc, data disc, tray open). */
    Optional<CdDisc> readDisc();

    /**
     * Plays the track from the given sector offset (0 = start) to its end.
     */
    PcmSource open(CdTrack track, int sectorOffset) throws IOException;

    void eject() throws IOException;
}

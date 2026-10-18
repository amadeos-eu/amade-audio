package eu.amadeos.audio.domain;

import java.time.Duration;

/**
 * A track of an audio CD.
 *
 * @param lba     first sector (logical block address, including the 150-sector lead-in)
 * @param sectors length in sectors of 1/75 s
 * @param audio   false for data tracks of enhanced CDs
 */
public record CdTrack(int number, int lba, int sectors, boolean audio) {
    public static final int SECTORS_PER_SECOND = 75;
    public static final int BYTES_PER_SECTOR = 2352;

    public Duration length() {
        return Duration.ofMillis(sectors * 1000L / SECTORS_PER_SECOND);
    }
}

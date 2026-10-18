package eu.amadeos.audio.domain;

import java.util.Objects;

/**
 * A cover or station logo as encoded image (JPEG, PNG, ...).
 *
 * @param source where it came from, shown under the cover, e.g. {@code Cover Art Archive}
 */
public record Artwork(byte[] data, String source) {
    public Artwork {
        Objects.requireNonNull(data, "data");
        source = source == null ? "" : source;
    }
}

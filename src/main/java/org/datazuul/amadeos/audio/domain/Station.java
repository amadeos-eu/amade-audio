package org.datazuul.amadeos.audio.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * An internet radio station, as listed by radio-browser.info or added by the user.
 *
 * @param uuid    radio-browser.info station UUID, empty for stations added by hand
 * @param url     stream or playlist (.pls/.m3u) URL
 * @param tags    comma-separated genres
 * @param bitrate kbit/s, 0 if unknown
 */
public record Station(String uuid, String name, String url, String country, String tags, String codec, int bitrate,
                      String favicon) {
    public Station {
        Objects.requireNonNull(url, "url");
        uuid = uuid == null ? "" : uuid.strip();
        name = name == null || name.isBlank() ? url : name.strip();
        country = country == null ? "" : country.strip().toUpperCase(Locale.ROOT);
        tags = tags == null ? "" : tags.strip();
        codec = codec == null ? "" : codec.strip().toUpperCase(Locale.ROOT);
        favicon = favicon == null ? "" : favicon.strip();
        bitrate = Math.max(0, bitrate);
    }

    /** First tag with a capital letter, e.g. {@code Classical}; empty if the station has no tags. */
    public String genre() {
        String first = tags.split(",", 2)[0].strip();
        if (first.isEmpty()) return "";
        return first.substring(0, 1).toUpperCase(Locale.ROOT) + first.substring(1);
    }

    /** Same station: same UUID if both have one, otherwise same URL. */
    public boolean sameAs(Station other) {
        if (other == null) return false;
        if (!uuid.isEmpty() && !other.uuid.isEmpty()) return uuid.equals(other.uuid);
        return url.equals(other.url);
    }

    /** Codec and bitrate for the display, e.g. {@code AAC 128 kbit/s}. */
    public String streamInfo() {
        String c = codec.isEmpty() ? "" : codec;
        if (bitrate > 0) c = (c + " " + bitrate + " kbit/s").strip();
        return c;
    }
}

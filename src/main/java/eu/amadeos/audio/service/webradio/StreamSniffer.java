package eu.amadeos.audio.service.webradio;

import eu.amadeos.audio.service.decode.DecoderFactory.StreamCodec;

import java.util.Locale;

/**
 * Detects the codec of a radio stream from its first bytes and finds where the audio starts: after an
 * ID3v2 tag, at the first MP3 or ADTS (AAC) frame sync, or at an Ogg page. Ported from dz-radio.
 */
public final class StreamSniffer {
    /** Bytes to look at before falling back to the station's codec. */
    public static final int SNIFF_BYTES = 16384;

    /** Result: the codec and the offset of the first audio byte in the inspected data. */
    public record Detection(StreamCodec codec, int start) {
    }

    private StreamSniffer() {
    }

    /**
     * @param data    first bytes of the stream (after ICY metadata removal)
     * @param length  valid bytes in {@code data}
     * @param hint    content type and station codec, e.g. {@code audio/aacp AAC+}, used when no sync is found
     */
    public static Detection detect(byte[] data, int length, String hint) {
        int start = 0;
        if (length >= 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            int size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
            start = Math.min(length, 10 + size);
        }
        if (start + 4 <= length && data[start] == 'O' && data[start + 1] == 'g' && data[start + 2] == 'g'
                && data[start + 3] == 'S') {
            return new Detection(StreamCodec.OGG, start);
        }
        for (int i = start; i + 1 < length; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xE0) == 0xE0) {
                // ADTS: 12-bit sync and layer bits 00; MPEG audio layer III has layer bits 01.
                boolean aac = (data[i + 1] & 0xF6) == 0xF0;
                if (aac || isMpegHeader(data, i, length)) {
                    return new Detection(aac ? StreamCodec.AAC : StreamCodec.MP3, i);
                }
            }
        }
        String h = hint == null ? "" : hint.toLowerCase(Locale.ROOT);
        StreamCodec fallback = h.contains("ogg") || h.contains("vorbis") ? StreamCodec.OGG
                : h.contains("aac") || h.contains("mp4") ? StreamCodec.AAC : StreamCodec.MP3;
        return new Detection(fallback, start);
    }

    /** Checks bitrate and sample rate fields of an MPEG audio frame header at {@code i}. */
    static boolean isMpegHeader(byte[] data, int i, int length) {
        if (i + 3 >= length) return true; // cannot check, accept
        int b2 = data[i + 2] & 0xFF;
        int bitrateIndex = b2 >> 4;
        int sampleRateIndex = (b2 >> 2) & 0x03;
        return bitrateIndex != 0x0F && bitrateIndex != 0 && sampleRateIndex != 0x03 && (data[i + 1] & 0x06) != 0;
    }
}

package eu.amadeos.apps.audio.service.recording;

import eu.amadeos.apps.audio.service.decode.DecoderFactory.StreamCodec;

/** Finds frame boundaries in MP3 and ADTS (AAC) data, so recordings can be split without broken frames. */
final class FrameSync {
    private FrameSync() {
    }

    /** Offset of the first frame header in {@code data[from, to)}, or -1. */
    static int find(byte[] data, int from, int to, StreamCodec codec) {
        for (int i = from; i + 3 < to; i++) {
            if ((data[i] & 0xFF) != 0xFF) continue;
            int b1 = data[i + 1] & 0xFF;
            if (codec == StreamCodec.AAC) {
                if ((b1 & 0xF6) == 0xF0) return i;
            } else if (codec == StreamCodec.MP3) {
                int b2 = data[i + 2] & 0xFF;
                boolean sync = (b1 & 0xE0) == 0xE0;
                boolean layer = (b1 & 0x06) != 0;
                boolean bitrate = (b2 >> 4) != 0 && (b2 >> 4) != 0x0F;
                boolean rate = ((b2 >> 2) & 0x03) != 0x03;
                if (sync && layer && bitrate && rate) return i;
            } else {
                return -1;
            }
        }
        return -1;
    }
}

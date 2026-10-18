package eu.amadeos.audio.service.webradio;

import eu.amadeos.audio.domain.Station;
import eu.amadeos.audio.service.decode.DecoderFactory.StreamCodec;
import eu.amadeos.audio.service.engine.PcmSource;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/** A playing station: the decoded audio for the engine plus the download session behind it. */
public final class RadioPlayback implements PcmSource {
    private final Station station;
    private final RadioSession session;
    private final PcmSource pcm;
    private final StreamCodec codec;

    RadioPlayback(Station station, RadioSession session, PcmSource pcm, StreamCodec codec) {
        this.station = station;
        this.session = session;
        this.pcm = pcm;
        this.codec = codec;
    }

    public Station station() {
        return station;
    }

    public RadioSession session() {
        return session;
    }

    public StreamCodec codec() {
        return codec;
    }

    /** E.g. {@code AAC 128 kbit/s · 44.1 kHz}. */
    public String streamInfo() {
        int kbps = session.bitrate() > 0 ? session.bitrate() : station.bitrate();
        StringBuilder sb = new StringBuilder(codec.name());
        if (kbps > 0) sb.append(' ').append(kbps).append(" kbit/s");
        float rate = pcm.format().getSampleRate();
        if (rate > 0) sb.append(" · ").append(rate % 1000 == 0 ? String.valueOf((int) (rate / 1000)) : String.valueOf(rate / 1000)).append(" kHz");
        return sb.toString();
    }

    @Override
    public AudioFormat format() {
        return pcm.format();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return pcm.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
        session.close();
        pcm.close();
    }
}

package eu.amadeos.audio.service.webradio;

import com.google.inject.Inject;
import eu.amadeos.audio.domain.Station;
import eu.amadeos.audio.service.decode.DecoderFactory;
import eu.amadeos.audio.service.decode.DecoderFactory.StreamCodec;
import eu.amadeos.audio.service.engine.PcmSource;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Connects to stations; call from a background thread, it blocks until the stream decodes. */
public final class WebRadioService {
    private final DecoderFactory decoders;

    @Inject
    public WebRadioService(DecoderFactory decoders) {
        this.decoders = decoders;
    }

    public RadioPlayback open(Station station, RadioSession.Events events) throws IOException {
        String url = station.url();
        if (PlaylistParser.isPlaylistUrl(url)) {
            List<String> urls = PlaylistParser.fetch(url, RadioSession.USER_AGENT);
            if (urls.isEmpty()) throw new IOException("The playlist of " + station.name() + " lists no streams");
            url = urls.get(0);
        }
        RadioSession session = new RadioSession(url, station.codec(), events);
        session.start();
        try {
            StreamCodec codec = session.codec().get(25, TimeUnit.SECONDS);
            String hint = (session.contentType() + " " + station.codec()).toLowerCase(Locale.ROOT);
            boolean heAac = hint.contains("aacp") || hint.contains("aac+") || hint.contains("he-aac");
            PcmSource pcm = decoders.openStream(session.audio(), codec, heAac);
            return new RadioPlayback(station, session, pcm, codec);
        } catch (ExecutionException e) {
            session.close();
            Throwable cause = e.getCause();
            throw new IOException(cause.getMessage() == null ? cause.toString() : cause.getMessage(), cause);
        } catch (TimeoutException e) {
            session.close();
            throw new IOException(station.name() + " does not answer", e);
        } catch (InterruptedException e) {
            session.close();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (IOException | RuntimeException e) {
            session.close();
            throw e;
        }
    }
}

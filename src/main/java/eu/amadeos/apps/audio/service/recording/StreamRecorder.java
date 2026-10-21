package eu.amadeos.apps.audio.service.recording;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.audio.domain.RecordingSettings;
import eu.amadeos.apps.audio.service.decode.DecoderFactory.StreamCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a radio stream to disk as it arrives, without decoding: MP3 stays MP3, AAC is written as ADTS
 * ({@code .aac}), Ogg Vorbis as {@code .ogg}. Files being written end in {@code .part} and get their
 * name when they are complete. With {@link RecordingSettings#splitByTitle()} a new file starts at the
 * first frame after a title change (not for Ogg). Thread-safe; {@link #write} is called by the
 * downloader, the rest by the user interface.
 */
public final class StreamRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(StreamRecorder.class);

    /** What is being recorded, for the display. */
    public record Status(Path file, Duration elapsed, long bytes) {
    }

    public interface Listener {
        /** About once a second while recording. */
        void status(Status status);

        /** Recording ended by itself, e.g. after the maximum duration or a disk error. */
        void stopped(String reason, List<Path> files);
    }

    /** Writes artist/title tags into a finished file. */
    public interface Tagger {
        void tag(Path file, String artist, String title, String station);
    }

    private final RecordingSettings settings;
    private final String station;
    private final StreamCodec codec;
    private final Clock clock;
    private final Tagger tagger;
    private final Listener listener;
    private final List<Path> finished = new ArrayList<>();
    private OutputStream out;
    private Path file;
    private String fileTitle;
    private String pendingTitle;
    private Instant started;
    private Instant lastStatus;
    private long bytes;
    private boolean recording;

    public StreamRecorder(RecordingSettings settings, String station, StreamCodec codec, Clock clock, Tagger tagger,
                          Listener listener) {
        this.settings = settings;
        this.station = station;
        this.codec = codec;
        this.clock = clock;
        this.tagger = tagger;
        this.listener = listener;
    }

    /** Opens the first file, named after the current stream title (may be null). */
    public synchronized void start(String currentTitle) throws IOException {
        if (recording) return;
        started = clock.instant();
        lastStatus = started;
        open(currentTitle);
        recording = true;
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    /** Directory the files of this recording go into. */
    public Path directory() {
        return settings.directory().resolve(RecordingNames.safe(station));
    }

    /** The station announced a new title. */
    public synchronized void titleChanged(String title) {
        if (!recording) return;
        if (settings.splitByTitle() && codec != StreamCodec.OGG && title != null && !title.equals(fileTitle)) {
            pendingTitle = title;
        }
    }

    /** Appends stream data; splits at the next frame if a title change is pending. */
    public synchronized void write(byte[] data, int offset, int length) {
        if (!recording) return;
        try {
            int from = offset;
            int end = offset + length;
            if (pendingTitle != null) {
                int sync = FrameSync.find(data, from, end, codec);
                if (sync >= 0) {
                    writeRaw(data, from, sync - from);
                    String next = pendingTitle;
                    pendingTitle = null;
                    closeFile();
                    open(next);
                    from = sync;
                }
            }
            writeRaw(data, from, end - from);
            Instant now = clock.instant();
            if (settings.maxMinutes() > 0 && Duration.between(started, now).toMinutes() >= settings.maxMinutes()) {
                List<Path> files = stopInternal();
                listener.stopped(tr("recording.max-time", settings.maxMinutes()), files);
                return;
            }
            if (Duration.between(lastStatus, now).toMillis() >= 1000) {
                lastStatus = now;
                listener.status(new Status(file, Duration.between(started, now), bytes));
            }
        } catch (IOException e) {
            LOG.warn("Recording failed", e);
            List<Path> files = stopInternal();
            listener.stopped(tr("recording.failed", e.getMessage()), files);
        }
    }

    /** Ends the recording; returns the files written. */
    public synchronized List<Path> stop() {
        return stopInternal();
    }

    public synchronized Status status() {
        return new Status(file, started == null ? Duration.ZERO : Duration.between(started, clock.instant()), bytes);
    }

    private List<Path> stopInternal() {
        if (recording) {
            recording = false;
            try {
                closeFile();
            } catch (IOException e) {
                LOG.warn("Cannot finish recording {}", file, e);
            }
        }
        return List.copyOf(finished);
    }

    private void open(String title) throws IOException {
        Path dir = directory();
        Files.createDirectories(dir);
        String name = RecordingNames.name(settings.pattern(), station, LocalDateTime.now(clock), title);
        file = RecordingNames.unique(dir, name, codec.extension());
        fileTitle = title;
        out = Files.newOutputStream(RecordingNames.part(file));
        LOG.info("Recording to {}", file);
    }

    private void writeRaw(byte[] data, int offset, int length) throws IOException {
        if (length <= 0) return;
        out.write(data, offset, length);
        bytes += length;
    }

    private void closeFile() throws IOException {
        if (out == null) return;
        out.close();
        out = null;
        Path part = RecordingNames.part(file);
        if (Files.size(part) == 0) {
            Files.delete(part);
            return;
        }
        Files.move(part, file, StandardCopyOption.ATOMIC_MOVE);
        finished.add(file);
        String[] at = RecordingNames.artistAndTitle(fileTitle);
        if (tagger != null) {
            try {
                tagger.tag(file, at[0], at[1], station);
            } catch (RuntimeException e) {
                LOG.warn("Cannot tag {}", file, e);
            }
        }
    }
}

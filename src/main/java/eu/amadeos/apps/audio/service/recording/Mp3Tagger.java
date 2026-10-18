package eu.amadeos.apps.audio.service.recording;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;

/** Writes ID3 tags into recorded MP3 files; other formats are left as they are. */
public final class Mp3Tagger implements StreamRecorder.Tagger {
    private static final Logger LOG = LoggerFactory.getLogger(Mp3Tagger.class);

    @Override
    public void tag(Path file, String artist, String title, String station) {
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")) return;
        try {
            AudioFile audio = AudioFileIO.read(file.toFile());
            Tag tag = audio.getTagOrCreateAndSetDefault();
            if (!artist.isEmpty()) tag.setField(FieldKey.ARTIST, artist);
            if (!title.isEmpty()) tag.setField(FieldKey.TITLE, title);
            tag.setField(FieldKey.ALBUM, station);
            tag.setField(FieldKey.COMMENT, "Recorded from " + station + " with amade-audio");
            audio.commit();
        } catch (Exception e) {
            LOG.warn("Cannot write tags to {}: {}", file, e.toString());
        }
    }
}

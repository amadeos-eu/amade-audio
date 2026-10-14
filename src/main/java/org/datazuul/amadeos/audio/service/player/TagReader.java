package org.datazuul.amadeos.audio.service.player;

import org.datazuul.amadeos.audio.domain.PlaylistEntry;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

/** Reads tags, length and embedded cover art with jaudiotagger. */
public class TagReader {
    static {
        // jaudiotagger logs every frame it parses through java.util.logging.
        java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(Level.SEVERE);
    }

    /** The entry with tags; just the file name if the file has none or cannot be read (e.g. MIDI). */
    public PlaylistEntry read(Path file) {
        if (PlaylistEntry.formatOf(file).equals("MIDI")) return PlaylistEntry.of(file);
        try {
            AudioFile audio = AudioFileIO.read(file.toFile());
            Tag tag = audio.getTag();
            Duration length = Duration.ofSeconds(Math.max(0, audio.getAudioHeader().getTrackLength()));
            if (tag == null) return new PlaylistEntry(file, null, null, null, null, length, null);
            return new PlaylistEntry(file, first(tag, FieldKey.TITLE), first(tag, FieldKey.ARTIST),
                    first(tag, FieldKey.ALBUM), first(tag, FieldKey.YEAR), length, null);
        } catch (Exception | LinkageError e) {
            return PlaylistEntry.of(file);
        }
    }

    /** Cover art stored in the file's tags. */
    public Optional<org.datazuul.amadeos.audio.domain.Artwork> embeddedArtwork(Path file) {
        if (PlaylistEntry.formatOf(file).equals("MIDI")) return Optional.empty();
        try {
            Tag tag = AudioFileIO.read(file.toFile()).getTag();
            if (tag == null) return Optional.empty();
            Artwork art = tag.getFirstArtwork();
            if (art == null || art.getBinaryData() == null || art.getBinaryData().length == 0) return Optional.empty();
            return Optional.of(new org.datazuul.amadeos.audio.domain.Artwork(art.getBinaryData(), "Embedded in the file"));
        } catch (Exception | LinkageError e) {
            return Optional.empty();
        }
    }

    private static String first(Tag tag, FieldKey key) {
        try {
            String value = tag.getFirst(key);
            return value == null || value.isBlank() ? null : value.strip();
        } catch (RuntimeException e) {
            return null;
        }
    }
}

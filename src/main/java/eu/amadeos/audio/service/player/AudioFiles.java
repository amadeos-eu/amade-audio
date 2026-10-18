package eu.amadeos.audio.service.player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Which files the player can play, and finding them in folders. */
public final class AudioFiles {
    public static final Set<String> EXTENSIONS = Set.of("mp3", "ogg", "oga", "flac", "wav", "wave", "aif", "aiff",
            "au", "aac", "m4a", "mid", "midi", "kar", "rmi");

    private AudioFiles() {
    }

    public static boolean isAudio(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static boolean isPlaylist(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".m3u") || name.endsWith(".m3u8");
    }

    /** Audio files in the folder and its sub-folders, sorted by path. */
    public static List<Path> inFolder(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder, 8)) {
            return walk.filter(Files::isRegularFile).filter(AudioFiles::isAudio).sorted().toList();
        }
    }
}

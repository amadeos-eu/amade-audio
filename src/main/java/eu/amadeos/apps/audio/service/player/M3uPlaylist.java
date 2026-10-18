package eu.amadeos.apps.audio.service.player;

import eu.amadeos.apps.audio.domain.PlaylistEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes extended M3U playlists (UTF-8, paths relative to the playlist where possible). */
public final class M3uPlaylist {
    private M3uPlaylist() {
    }

    /** Files listed in the playlist, resolved against its directory; missing files are kept. */
    public static List<Path> read(Path playlist) throws IOException {
        List<Path> files = new ArrayList<>();
        Path base = playlist.toAbsolutePath().getParent();
        for (String line : Files.readAllLines(playlist, StandardCharsets.UTF_8)) {
            String l = line.strip();
            if (l.startsWith("﻿")) l = l.substring(1);
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (l.startsWith("file://")) l = java.net.URI.create(l).getPath();
            if (l.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) continue; // streams belong to the web radio
            try {
                Path p = Path.of(l);
                files.add(p.isAbsolute() ? p.normalize() : base.resolve(p).normalize());
            } catch (InvalidPathException e) {
                // skip lines that are no paths
            }
        }
        return files;
    }

    public static void write(Path playlist, List<PlaylistEntry> entries) throws IOException {
        Path base = playlist.toAbsolutePath().getParent();
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        for (PlaylistEntry e : entries) {
            long seconds = e.length().isZero() ? -1 : e.length().toSeconds();
            String label = e.artist() == null ? e.displayTitle() : e.artist() + " - " + e.displayTitle();
            sb.append("#EXTINF:").append(seconds).append(',').append(label).append('\n');
            Path file = e.file().toAbsolutePath();
            sb.append(file.startsWith(base) ? base.relativize(file) : file).append('\n');
        }
        Files.writeString(playlist, sb.toString(), StandardCharsets.UTF_8);
    }
}

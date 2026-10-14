package org.datazuul.amadeos.audio.ui.rack.player;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.Artwork;
import org.datazuul.amadeos.audio.domain.AudioLevels;
import org.datazuul.amadeos.audio.domain.PlaybackOptions;
import org.datazuul.amadeos.audio.domain.PlaylistEntry;
import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.audio.domain.TransportState;
import org.datazuul.amadeos.audio.repository.PreferencesStore;
import org.datazuul.amadeos.audio.service.PlaybackCoordinator;
import org.datazuul.amadeos.audio.service.decode.DecoderFactory;
import org.datazuul.amadeos.audio.service.engine.AudioChannel;
import org.datazuul.amadeos.audio.service.engine.AudioEngine;
import org.datazuul.amadeos.audio.service.engine.PcmSource;
import org.datazuul.amadeos.audio.service.metadata.ArtworkService;
import org.datazuul.amadeos.audio.service.player.AudioFiles;
import org.datazuul.amadeos.audio.service.player.M3uPlaylist;
import org.datazuul.amadeos.audio.service.player.Playlist;
import org.datazuul.amadeos.audio.service.player.TagReader;
import org.datazuul.amadeos.audio.ui.playlist.PlaylistPanel;
import org.datazuul.amadeos.audio.ui.widget.LcdText;
import org.datazuul.amadeos.audio.ui.widget.Ticker;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Behaviour of the file player rack and the playlist. Runs on the EDT. */
public final class PlayerPresenter implements AudioChannel.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(PlayerPresenter.class);

    private final AudioChannel channel;
    private final PlaybackCoordinator coordinator;
    private final DecoderFactory decoders;
    private final TagReader tags;
    private final ArtworkService artwork;
    private final PreferencesStore preferences;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final PlayerRack rack;
    private final FilePlaylistView list;
    private final PlaylistPanel.Header header = new PlaylistPanel.Header("PLAYLIST", null);
    private final Playlist playlist = new Playlist();
    private Component parent;
    private Consumer<String> info = s -> {
    };
    private boolean powered = true;
    private int request;
    private long durationMicros;

    @Inject
    public PlayerPresenter(AudioEngine engine, PlaybackCoordinator coordinator, DecoderFactory decoders, TagReader tags,
                           ArtworkService artwork, PreferencesStore preferences, ExecutorService executor, EdtExecutor edt) {
        this.channel = engine.channel(SourceId.PLAYER);
        this.coordinator = coordinator;
        this.decoders = decoders;
        this.tags = tags;
        this.artwork = artwork;
        this.preferences = preferences;
        this.executor = executor;
        this.edt = edt;
        this.rack = new PlayerRack(this);
        this.list = new FilePlaylistView(this);
        channel.addListener(this);
    }

    public JComponent rack() {
        return rack;
    }

    public JComponent listView() {
        return list.component;
    }

    public PlaylistPanel.Header header() {
        return header;
    }

    public void start(Component dialogParent, Consumer<String> infoSink) {
        parent = dialogParent;
        info = infoSink;
        Ticker.every(200, this::tick);
        showEntry(null);
        refresh();
    }

    public void setActive(boolean active) {
        rack.setDimmed(!active || !powered);
    }

    // ---- playlist --------------------------------------------------------------------------------

    /** Adds files, folders and M3U playlists; optionally plays the first added file. */
    public void add(List<Path> paths, boolean playFirst) {
        executor.execute(() -> {
            List<Path> files = new ArrayList<>();
            for (Path p : paths) {
                try {
                    if (Files.isDirectory(p)) files.addAll(AudioFiles.inFolder(p));
                    else if (AudioFiles.isPlaylist(p)) files.addAll(M3uPlaylist.read(p));
                    else if (AudioFiles.isAudio(p)) files.add(p.toAbsolutePath());
                } catch (IOException e) {
                    LOG.warn("Cannot read {}: {}", p, e.toString());
                }
            }
            List<PlaylistEntry> entries = files.stream().map(PlaylistEntry::of).toList();
            edt.execute(() -> {
                if (entries.isEmpty()) {
                    error("No playable audio files found.");
                    return;
                }
                int firstNew = playlist.size();
                playlist.addAll(entries);
                refresh();
                coordinator.select(SourceId.PLAYER);
                if (playFirst) play(firstNew);
                readTags(entries);
            });
        });
    }

    private void readTags(List<PlaylistEntry> entries) {
        executor.execute(() -> {
            for (PlaylistEntry plain : entries) {
                PlaylistEntry tagged = tags.read(plain.file());
                edt.execute(() -> {
                    List<PlaylistEntry> all = playlist.entries();
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i) == plain) {
                            playlist.replace(i, tagged);
                            if (i == playlist.currentIndex()) showEntry(tagged);
                            break;
                        }
                    }
                    list.model.set(playlist.entries(), playlist.currentIndex());
                    updateInfo();
                });
            }
        });
    }

    private void refresh() {
        list.model.set(playlist.entries(), channel.state().isActive() ? playlist.currentIndex() : -1);
        updateInfo();
    }

    private void updateInfo() {
        int n = playlist.size();
        info.accept("Player · " + n + (n == 1 ? " file" : " files")
                + (playlist.totalLength().isZero() ? "" : " · " + PlaylistPanel.time(playlist.totalLength().toSeconds())));
    }

    public void openFiles() {
        JFileChooser chooser = chooser();
        chooser.setMultiSelectionEnabled(true);
        List<String> extensions = new ArrayList<>(AudioFiles.EXTENSIONS.stream().sorted().toList());
        extensions.add("m3u");
        extensions.add("m3u8");
        chooser.setFileFilter(new FileNameExtensionFilter("Audio files and playlists", extensions.toArray(new String[0])));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        List<Path> paths = new ArrayList<>();
        for (File f : chooser.getSelectedFiles()) paths.add(f.toPath());
        remember(chooser);
        add(paths, !channel.state().isActive());
    }

    public void openFolder() {
        JFileChooser chooser = chooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        remember(chooser);
        add(List.of(chooser.getSelectedFile().toPath()), !channel.state().isActive());
    }

    void removeSelected() {
        int[] rows = list.table.getSelectedRows();
        if (rows.length == 0) return;
        int current = playlist.currentIndex();
        for (int r : rows) {
            if (r == current && channel.state().isActive()) {
                stop();
                break;
            }
        }
        playlist.remove(rows);
        refresh();
    }

    void clear() {
        stop();
        playlist.clear();
        showEntry(null);
        refresh();
    }

    void loadPlaylist() {
        JFileChooser chooser = chooser();
        chooser.setFileFilter(new FileNameExtensionFilter("M3U playlists", "m3u", "m3u8"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        remember(chooser);
        add(List.of(chooser.getSelectedFile().toPath()), false);
    }

    void savePlaylist() {
        if (playlist.isEmpty()) return;
        JFileChooser chooser = chooser();
        chooser.setFileFilter(new FileNameExtensionFilter("M3U playlists", "m3u", "m3u8"));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "playlist.m3u"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().matches(".*\\.m3u8?$")) file = file.resolveSibling(file.getFileName() + ".m3u");
        if (Files.exists(file) && JOptionPane.showConfirmDialog(parent, file.getFileName() + " already exists. Replace it?",
                "Save Playlist", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            M3uPlaylist.write(file, playlist.entries());
        } catch (IOException e) {
            error("Cannot save the playlist: " + e.getMessage());
        }
    }

    void setOptions(boolean shuffle, boolean repeat) {
        playlist.setOptions(new PlaybackOptions(shuffle, repeat));
    }

    private JFileChooser chooser() {
        Path last = preferences.current().lastFolder();
        Path start = last != null && Files.isDirectory(last) ? last : Path.of(System.getProperty("user.home"), "Music");
        return new JFileChooser(Files.isDirectory(start) ? start.toFile() : null);
    }

    private void remember(JFileChooser chooser) {
        File dir = chooser.getSelectedFile() != null && chooser.getSelectedFile().isDirectory()
                ? chooser.getSelectedFile() : chooser.getCurrentDirectory();
        if (dir != null) preferences.update(p -> p.withLastFolder(dir.toPath()));
    }

    // ---- transport -------------------------------------------------------------------------------

    void play() {
        if (!powered) return;
        if (channel.isPaused()) {
            channel.resume();
            return;
        }
        if (channel.state().isActive()) return;
        if (playlist.isEmpty()) {
            openFiles();
            return;
        }
        int selected = list.table.getSelectedRow();
        int current = playlist.currentIndex();
        play(selected >= 0 ? selected : current >= 0 ? current : playlist.first());
    }

    void play(int index) {
        playAt(index, 0);
    }

    private void playAt(int index, double fraction) {
        if (!powered || index < 0 || index >= playlist.size()) return;
        coordinator.activate(SourceId.PLAYER);
        playlist.setCurrent(index);
        PlaylistEntry entry = playlist.current();
        list.model.setPlaying(index);
        showEntry(entry);
        int req = ++request;
        executor.execute(() -> {
            try {
                PcmSource source = decoders.openFile(entry.file());
                long frames = source.frameLength();
                float rate = source.format().getSampleRate();
                long duration = frames > 0 ? (long) (frames * 1_000_000L / rate) : entry.length().toNanos() / 1000;
                long startMicros = 0;
                if (fraction > 0 && duration > 0) {
                    long targetFrames = (long) (fraction * duration / 1_000_000.0 * rate);
                    long skipped = source.skipFrames(targetFrames);
                    startMicros = (long) (skipped * 1_000_000L / rate);
                }
                long start = startMicros;
                edt.execute(() -> {
                    if (req != request) {
                        closeQuietly(source);
                        return;
                    }
                    durationMicros = duration;
                    channel.play(source, start);
                });
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot play {}", entry.file(), e);
                edt.execute(() -> {
                    if (req != request) return;
                    error("Cannot play " + entry.file().getFileName() + ":\n" + e.getMessage());
                });
            }
        });
    }

    void seek(double fraction) {
        if (!channel.state().isActive() || durationMicros <= 0) return;
        playAt(playlist.currentIndex(), fraction);
    }

    void stop() {
        request++;
        channel.stop();
        list.model.setPlaying(-1);
        rack.seek.setPosition(0);
        rack.time.setText("00:00");
    }

    void pause() {
        if (!channel.state().isActive()) return;
        if (channel.isPaused()) channel.resume();
        else channel.pause();
    }

    void next() {
        int n = playlist.next();
        if (n < 0) return;
        if (channel.state().isActive()) play(n);
        else select(n);
    }

    void previous() {
        if (channel.state().isActive() && channel.positionMicros() > 3_000_000) {
            play(playlist.currentIndex());
            return;
        }
        int p = playlist.previous();
        if (p < 0) p = Math.max(0, playlist.currentIndex());
        if (channel.state().isActive()) play(p);
        else select(p);
    }

    private void select(int index) {
        playlist.setCurrent(index);
        showEntry(playlist.current());
        list.table.getSelectionModel().setSelectionInterval(index, index);
        list.table.scrollRectToVisible(list.table.getCellRect(index, 0, true));
    }

    void power(boolean on) {
        powered = on;
        if (!on) {
            stop();
            coordinator.deactivate(SourceId.PLAYER);
        }
        rack.setControlsEnabled(on);
        rack.setDimmed(!on || coordinator.active() != SourceId.PLAYER);
    }

    private void showEntry(PlaylistEntry entry) {
        if (entry == null) {
            rack.folder.setText("LOCAL FILES");
            rack.file.setText("No file");
            rack.number.setText("  ");
            rack.time.setText("  :  ");
            rack.setFormat("");
            list.cover.setText("", "", "");
            list.cover.setImage(null, null);
            return;
        }
        Path dir = entry.file().getParent();
        rack.folder.setText("LOCAL FILES · " + (dir == null ? "" : abbreviate(dir)));
        rack.file.setText(entry.file().getFileName().toString());
        rack.number.setText(LcdText.number(playlist.currentIndex() + 1));
        rack.setFormat(entry.format());
        String details = String.join(" · ", List.of(entry.year() == null ? "" : entry.year(), entry.format())
                .stream().filter(s -> !s.isEmpty()).toList());
        list.cover.setText(entry.album() != null ? entry.album() : entry.displayTitle(),
                entry.artist() == null ? "" : entry.artist(), details);
        loadArtwork(entry);
    }

    private void loadArtwork(PlaylistEntry entry) {
        Path file = entry.file();
        executor.execute(() -> {
            Optional<Artwork> art = artwork.forFile(file);
            edt.execute(() -> {
                PlaylistEntry now = playlist.current();
                if (now == null || !now.file().equals(file)) return;
                list.cover.setImage(art.map(Artwork::data).orElse(null), art.map(Artwork::source).orElse(null));
            });
        });
    }

    private static String abbreviate(Path dir) {
        String home = System.getProperty("user.home");
        String s = dir.toString();
        if (s.startsWith(home)) s = "~" + s.substring(home.length());
        return s.length() > 40 ? "..." + s.substring(s.length() - 37) : s;
    }

    private void tick() {
        TransportState state = channel.state();
        if (state == TransportState.PLAYING || state == TransportState.PAUSED) {
            long pos = channel.positionMicros();
            boolean blinkOff = state == TransportState.PAUSED && System.currentTimeMillis() / 500 % 2 == 1;
            rack.time.setText(blinkOff ? "  :  " : LcdText.time(pos));
            if (durationMicros > 0) rack.seek.setPosition((double) pos / durationMicros);
        }
    }

    // ---- playback events -------------------------------------------------------------------------

    @Override
    public void stateChanged(SourceId source, TransportState state, String message) {
        edt.execute(() -> {
            if (state == TransportState.ERROR) {
                list.model.setPlaying(-1);
                error("Playback failed: " + message);
            }
            if (state == TransportState.PLAYING) list.model.setPlaying(playlist.currentIndex());
            if (state == TransportState.STOPPED && !channel.state().isActive()) rack.spectrum.setBands(new float[16]);
        });
    }

    @Override
    public void finished(SourceId source) {
        edt.execute(() -> {
            int n = playlist.next();
            if (n >= 0) play(n);
            else stop();
        });
    }

    @Override
    public void levels(SourceId source, AudioLevels levels) {
        edt.execute(() -> rack.spectrum.setBands(levels.bands()));
    }

    private void error(String text) {
        JOptionPane.showMessageDialog(parent, text, "Audio Station", JOptionPane.ERROR_MESSAGE);
    }

    private static void closeQuietly(PcmSource source) {
        try {
            source.close();
        } catch (IOException e) {
            LOG.debug("Close failed", e);
        }
    }
}

package org.datazuul.amadeos.audio.ui.rack.cd;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.AlbumInfo;
import org.datazuul.amadeos.audio.domain.Artwork;
import org.datazuul.amadeos.audio.domain.AudioLevels;
import org.datazuul.amadeos.audio.domain.CdTrack;
import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.audio.domain.TransportState;
import org.datazuul.amadeos.audio.service.PlaybackCoordinator;
import org.datazuul.amadeos.audio.service.cd.CdDisc;
import org.datazuul.amadeos.audio.service.cd.CdDrive;
import org.datazuul.amadeos.audio.service.engine.AudioChannel;
import org.datazuul.amadeos.audio.service.engine.AudioEngine;
import org.datazuul.amadeos.audio.service.engine.PcmSource;
import org.datazuul.amadeos.audio.service.metadata.ArtworkService;
import org.datazuul.amadeos.audio.service.metadata.CdMetadataService;
import org.datazuul.amadeos.audio.ui.playlist.PlaylistPanel;
import org.datazuul.amadeos.audio.ui.widget.LcdText;
import org.datazuul.amadeos.audio.ui.widget.Ticker;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Behaviour of the CD rack and its track list: watches the drive, finds titles and cover, plays tracks.
 * Runs on the EDT; drive and network access go to the background executor.
 */
public final class CdPresenter implements AudioChannel.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(CdPresenter.class);

    private final CdDrive drive;
    private final AudioChannel channel;
    private final PlaybackCoordinator coordinator;
    private final CdMetadataService metadata;
    private final ArtworkService artwork;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final CdRack rack;
    private final CdTrackListView list;
    private final PlaylistPanel.Header header = new PlaylistPanel.Header("TRACK LIST", null);
    private Component parent;
    private Consumer<String> info = s -> {
    };
    private CdDisc disc;
    private AlbumInfo album = AlbumInfo.unknown(0);
    private List<CdTrack> tracks = List.of();
    private List<Integer> shuffleOrder = new ArrayList<>();
    private int current = -1;
    private boolean shuffle;
    private boolean repeat;
    private boolean powered = true;
    private boolean reading;
    private int request;

    @Inject
    public CdPresenter(CdDrive drive, AudioEngine engine, PlaybackCoordinator coordinator, CdMetadataService metadata,
                       ArtworkService artwork, ExecutorService executor, EdtExecutor edt) {
        this.drive = drive;
        this.channel = engine.channel(SourceId.CD);
        this.coordinator = coordinator;
        this.metadata = metadata;
        this.artwork = artwork;
        this.executor = executor;
        this.edt = edt;
        this.rack = new CdRack(this);
        this.list = new CdTrackListView(this);
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

    /** Starts watching the drive. */
    public void start(Component dialogParent, Consumer<String> infoSink) {
        parent = dialogParent;
        info = infoSink;
        noDisc();
        Ticker.every(3000, this::poll);
        Ticker.every(250, this::tick);
        poll();
    }

    /** The CD is (not) the active device: its LCDs are lit or dimmed. */
    public void setActive(boolean active) {
        rack.setDimmed(!active || !powered);
    }

    // ---- drive -----------------------------------------------------------------------------------

    private void poll() {
        if (!powered || reading || channel.state().isActive()) return;
        reading = true;
        executor.execute(() -> {
            boolean available = drive.isAvailable();
            Optional<CdDisc> found = available ? drive.readDisc() : Optional.empty();
            edt.execute(() -> {
                reading = false;
                discRead(available, found);
            });
        });
    }

    private void discRead(boolean available, Optional<CdDisc> found) {
        if (!available) {
            if (disc != null) disc = null;
            rack.source.setText("NO CD DRIVE");
            rack.album.setText("No CD drive or libcdio not installed");
            return;
        }
        String id = found.map(CdDisc::discId).orElse(null);
        String old = disc == null ? null : disc.discId();
        if (id == null ? old == null : id.equals(old)) return;
        if (found.isEmpty()) {
            disc = null;
            if (channel.state().isActive()) channel.stop();
            noDisc();
            return;
        }
        disc = found.get();
        tracks = disc.toc().audioTracks();
        album = metadata.offline(disc);
        current = -1;
        reshuffle();
        LOG.info("Audio CD inserted: {} tracks, disc ID {}", tracks.size(), disc.discId());
        showAlbum();
        coordinator.select(SourceId.CD);
        loadArtwork();
        if (!metadata.isCached(disc)) lookUpOnline(false);
    }

    private void noDisc() {
        tracks = List.of();
        album = AlbumInfo.unknown(0);
        current = -1;
        rack.source.setText("AUDIO CD · " + drive.device());
        rack.album.setText("No disc");
        rack.track.setText("  ");
        rack.time.setText("  :  ");
        rack.cover.setImage(null);
        list.model.set(tracks, album);
        list.model.setPlaying(-1);
        list.cover.setText("No disc", "", "");
        list.cover.setImage(null, null);
        updateInfo();
    }

    private void showAlbum() {
        String src = album.source().isEmpty() ? "" : " · " + album.source().toUpperCase();
        rack.source.setText("AUDIO CD" + src);
        rack.album.setText(album.isKnown()
                ? (album.album().isEmpty() ? "Audio CD" : album.album()) + (album.artist().isEmpty() ? "" : " — " + album.artist())
                : "Audio CD · " + tracks.size() + " tracks");
        list.model.set(tracks, album);
        list.model.setPlaying(current);
        String details = String.join(" · ", List.of(album.year(), album.label()).stream().filter(s -> !s.isEmpty()).toList());
        list.cover.setText(album.album().isEmpty() ? "Audio CD" : album.album(), album.artist(), details);
        rack.track.setText(LcdText.number(current < 0 ? tracks.size() : current + 1));
        if (current < 0) rack.time.setText(LcdText.time(disc == null ? -1 : disc.toc().length().toNanos() / 1000));
        updateInfo();
    }

    private void updateInfo() {
        if (disc == null) {
            info.accept("Audio CD · no disc");
            return;
        }
        String text = "Audio CD · " + tracks.size() + " tracks · " + PlaylistPanel.time(disc.toc().length().toSeconds());
        if (!album.source().isEmpty()) text += " · " + album.source();
        info.accept(text);
    }

    private void loadArtwork() {
        CdDisc forDisc = disc;
        AlbumInfo forAlbum = album;
        if (forDisc == null) return;
        executor.execute(() -> {
            Optional<Artwork> art = artwork.forCd(forDisc.discId(), forAlbum);
            edt.execute(() -> {
                if (disc != forDisc) return;
                byte[] data = art.map(Artwork::data).orElse(null);
                rack.cover.setImage(data);
                list.cover.setImage(data, art.map(Artwork::source).orElse(null));
            });
        });
    }

    private void lookUpOnline(boolean explicit) {
        CdDisc forDisc = disc;
        if (forDisc == null) return;
        executor.execute(() -> {
            try {
                List<AlbumInfo> candidates = metadata.lookup(forDisc);
                edt.execute(() -> found(forDisc, candidates, explicit));
            } catch (IOException e) {
                LOG.info("MusicBrainz lookup failed: {}", e.toString());
                if (explicit) edt.execute(() -> error("MusicBrainz cannot be reached: " + e.getMessage()));
            }
        });
    }

    private void found(CdDisc forDisc, List<AlbumInfo> candidates, boolean explicit) {
        if (disc != forDisc) return;
        if (candidates.isEmpty()) {
            if (explicit) message("MusicBrainz does not know this disc.\nUse \"Edit Titles...\" to name the tracks.");
            return;
        }
        AlbumInfo chosen = candidates.size() == 1 ? candidates.get(0) : choose(candidates);
        if (chosen == null) return;
        album = chosen;
        executor.execute(() -> metadata.remember(forDisc, chosen));
        showAlbum();
        loadArtwork();
    }

    private AlbumInfo choose(List<AlbumInfo> candidates) {
        JList<AlbumInfo> choices = new JList<>(candidates.toArray(AlbumInfo[]::new));
        choices.setCellRenderer((l, value, index, selected, focus) -> {
            String details = String.join(", ", List.of(value.year(), value.label(), value.source().replace("MusicBrainz", "").replace(" · ", ""))
                    .stream().filter(s -> !s.isBlank()).toList());
            JLabel label = new JLabel(value.album() + " — " + value.artist() + (details.isEmpty() ? "" : "  (" + details + ")"));
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            label.setBackground(selected ? l.getSelectionBackground() : l.getBackground());
            label.setForeground(selected ? l.getSelectionForeground() : l.getForeground());
            return label;
        });
        choices.setSelectedIndex(0);
        choices.setVisibleRowCount(Math.min(8, candidates.size()));
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("MusicBrainz knows several releases with this disc. Which one is it?"), BorderLayout.NORTH);
        panel.add(new JScrollPane(choices), BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(parent, panel, "Audio CD", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? choices.getSelectedValue() : null;
    }

    // ---- actions ---------------------------------------------------------------------------------

    void play() {
        if (!powered || disc == null || tracks.isEmpty()) return;
        if (channel.isPaused()) {
            channel.resume();
            return;
        }
        if (channel.state().isActive()) return;
        int selected = list.table.getSelectedRow();
        play(selected >= 0 ? selected : current >= 0 ? current : first());
    }

    void play(int index) {
        if (!powered || disc == null || index < 0 || index >= tracks.size()) return;
        coordinator.activate(SourceId.CD);
        current = index;
        list.model.setPlaying(index);
        rack.track.setText(LcdText.number(index + 1));
        rack.time.setText("00:00");
        CdTrack track = tracks.get(index);
        int req = ++request;
        executor.execute(() -> {
            try {
                PcmSource source = drive.open(track, 0);
                edt.execute(() -> {
                    if (req != request) {
                        closeQuietly(source);
                        return;
                    }
                    channel.play(source, 0);
                });
            } catch (IOException e) {
                edt.execute(() -> error("Cannot play track " + track.number() + ": " + e.getMessage()));
            }
        });
    }

    void stop() {
        request++;
        channel.stop();
        list.model.setPlaying(-1);
        rack.time.setText(LcdText.time(disc == null ? -1 : disc.toc().length().toNanos() / 1000));
        rack.track.setText(LcdText.number(disc == null ? 0 : tracks.size()));
    }

    void pause() {
        if (!channel.state().isActive()) return;
        if (channel.isPaused()) channel.resume();
        else channel.pause();
    }

    void next() {
        int n = nextIndex();
        if (n < 0) n = 0;
        if (channel.state().isActive()) play(n);
        else select(n);
    }

    void previous() {
        if (channel.state().isActive() && channel.positionMicros() > 3_000_000) {
            play(current);
            return;
        }
        int p;
        if (shuffle) {
            int pos = shuffleOrder.indexOf(current);
            p = pos > 0 ? shuffleOrder.get(pos - 1) : current;
        } else {
            p = current > 0 ? current - 1 : repeat ? tracks.size() - 1 : 0;
        }
        if (channel.state().isActive()) play(p);
        else select(p);
    }

    private void select(int index) {
        if (index < 0 || index >= tracks.size()) return;
        current = index;
        rack.track.setText(LcdText.number(index + 1));
        rack.time.setText(LcdText.time(tracks.get(index).length().toNanos() / 1000));
        list.table.getSelectionModel().setSelectionInterval(index, index);
    }

    void eject() {
        stop();
        executor.execute(() -> {
            try {
                drive.eject();
            } catch (IOException e) {
                edt.execute(() -> error("Cannot eject: " + e.getMessage()));
            }
        });
        disc = null;
        noDisc();
    }

    void power(boolean on) {
        powered = on;
        if (!on) {
            stop();
            coordinator.deactivate(SourceId.CD);
        }
        rack.setControlsEnabled(on);
        rack.setDimmed(!on || coordinator.active() != SourceId.CD);
        if (on) poll();
    }

    void setOptions(boolean newShuffle, boolean newRepeat) {
        if (newShuffle && !shuffle) {
            shuffle = true;
            reshuffle();
        }
        shuffle = newShuffle;
        repeat = newRepeat;
        rack.mode.setText("<html>" + (shuffle ? "SHUFFLE" : "") + "<br>" + (repeat ? "REPEAT" : "") + "</html>");
    }

    void lookUp() {
        if (disc == null) return;
        lookUpOnline(true);
    }

    void editTitles() {
        if (disc == null) return;
        JTextField albumField = new JTextField(album.album(), 30);
        JTextField artistField = new JTextField(album.artist(), 30);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Track", "Title", "Artist"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0;
            }
        };
        for (int i = 0; i < tracks.size(); i++) {
            model.addRow(new Object[]{String.format("%02d", tracks.get(i).number()),
                    i < album.titles().size() ? album.titles().get(i) : "",
                    i < album.artists().size() ? album.artists().get(i) : ""});
        }
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 6));
        fields.add(new JLabel("Album:"));
        fields.add(albumField);
        fields.add(new JLabel("Artist:"));
        fields.add(artistField);
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(fields, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 300));
        panel.add(scroll, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(parent, panel, "Edit Titles", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        List<String> titles = new ArrayList<>();
        List<String> artists = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            titles.add(String.valueOf(model.getValueAt(i, 1)));
            artists.add(String.valueOf(model.getValueAt(i, 2)));
        }
        album = new AlbumInfo(albumField.getText(), artistField.getText(), album.year(), album.label(), titles, artists,
                album.releaseId(), album.releaseGroupId(), "Edited");
        CdDisc forDisc = disc;
        AlbumInfo edited = album;
        executor.execute(() -> metadata.remember(forDisc, edited));
        showAlbum();
    }

    // ---- playback events -------------------------------------------------------------------------

    @Override
    public void stateChanged(SourceId source, TransportState state, String message) {
        edt.execute(() -> {
            if (state == TransportState.ERROR) {
                list.model.setPlaying(-1);
                error("CD playback failed: " + message);
            }
            if (state == TransportState.STOPPED && !channel.state().isActive()) list.model.setPlaying(-1);
            if (state == TransportState.PLAYING) list.model.setPlaying(current);
        });
    }

    @Override
    public void finished(SourceId source) {
        edt.execute(() -> {
            int n = nextIndex();
            if (n >= 0) play(n);
            else stop();
        });
    }

    @Override
    public void levels(SourceId source, AudioLevels levels) {
        // The CD rack has no meters.
    }

    private void tick() {
        if (channel.state() == TransportState.PLAYING) rack.time.setText(LcdText.time(channel.positionMicros()));
        else if (channel.state() == TransportState.PAUSED) {
            boolean on = System.currentTimeMillis() / 500 % 2 == 0;
            rack.time.setText(on ? LcdText.time(channel.positionMicros()) : "  :  ");
        }
    }

    private int first() {
        return shuffle && !shuffleOrder.isEmpty() ? shuffleOrder.get(0) : 0;
    }

    private int nextIndex() {
        if (tracks.isEmpty()) return -1;
        if (shuffle) {
            int pos = shuffleOrder.indexOf(current);
            if (pos + 1 < shuffleOrder.size()) return shuffleOrder.get(pos + 1);
            if (!repeat) return -1;
            reshuffle();
            return shuffleOrder.get(0);
        }
        if (current + 1 < tracks.size()) return current + 1;
        return repeat ? 0 : -1;
    }

    private void reshuffle() {
        shuffleOrder = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) shuffleOrder.add(i);
        Collections.shuffle(shuffleOrder);
        if (current >= 0) {
            shuffleOrder.remove(Integer.valueOf(current));
            shuffleOrder.add(0, current);
        }
    }

    private void error(String text) {
        JOptionPane.showMessageDialog(parent, text, "Audio Station", JOptionPane.ERROR_MESSAGE);
    }

    private void message(String text) {
        JOptionPane.showMessageDialog(parent, text, "Audio Station", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void closeQuietly(PcmSource source) {
        try {
            source.close();
        } catch (IOException e) {
            LOG.debug("Close failed", e);
        }
    }
}

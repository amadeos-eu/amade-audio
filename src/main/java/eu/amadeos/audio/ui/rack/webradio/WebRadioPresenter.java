package eu.amadeos.audio.ui.rack.webradio;

import static eu.amadeos.audio.i18n.Texts.tr;

import com.google.inject.Inject;
import eu.amadeos.audio.domain.Artwork;
import eu.amadeos.audio.domain.AudioLevels;
import eu.amadeos.audio.domain.SourceId;
import eu.amadeos.audio.domain.Station;
import eu.amadeos.audio.domain.TransportState;
import eu.amadeos.audio.repository.PreferencesStore;
import eu.amadeos.audio.service.PlaybackCoordinator;
import eu.amadeos.audio.service.engine.AudioChannel;
import eu.amadeos.audio.service.engine.AudioEngine;
import eu.amadeos.audio.service.metadata.ArtworkService;
import eu.amadeos.audio.service.recording.Mp3Tagger;
import eu.amadeos.audio.service.recording.StreamRecorder;
import eu.amadeos.audio.service.webradio.RadioBrowserClient;
import eu.amadeos.audio.service.webradio.RadioPlayback;
import eu.amadeos.audio.service.webradio.RadioSession;
import eu.amadeos.audio.service.webradio.StationRepository;
import eu.amadeos.audio.service.webradio.WebRadioService;
import eu.amadeos.audio.ui.playlist.PlaylistPanel;
import eu.amadeos.audio.ui.widget.Ticker;
import eu.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Behaviour of the web radio rack, the station browser and stream recording. Runs on the EDT. */
public final class WebRadioPresenter implements AudioChannel.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(WebRadioPresenter.class);
    private static final int LIMIT = 200;

    private final AudioChannel channel;
    private final PlaybackCoordinator coordinator;
    private final WebRadioService radio;
    private final RadioBrowserClient directory;
    private final StationRepository favorites;
    private final ArtworkService artwork;
    private final PreferencesStore preferences;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final WebRadioRack rack;
    private final StationBrowserView view;
    private final PlaylistPanel.Header header;
    private Component parent;
    private Consumer<String> info = s -> {
    };
    private Consumer<String> openOptions = s -> {
    };
    private StationBrowserView.Tab tab = StationBrowserView.Tab.FAVORITES;
    /** Text of the filter field on the Favorites, Genres and Countries tabs. */
    private String filter = "";
    private List<Station> context = List.of();
    private Station current;
    private RadioPlayback playback;
    private StreamRecorder recorder;
    private String streamTitle;
    private boolean powered = true;
    private int request;
    private int choiceRequest;

    @Inject
    public WebRadioPresenter(AudioEngine engine, PlaybackCoordinator coordinator, WebRadioService radio,
                             RadioBrowserClient directory, StationRepository favorites, ArtworkService artwork,
                             PreferencesStore preferences, ExecutorService executor, EdtExecutor edt) {
        this.channel = engine.channel(SourceId.WEBRADIO);
        this.coordinator = coordinator;
        this.radio = radio;
        this.directory = directory;
        this.favorites = favorites;
        this.artwork = artwork;
        this.preferences = preferences;
        this.executor = executor;
        this.edt = edt;
        this.rack = new WebRadioRack(this);
        this.view = new StationBrowserView(this);
        this.header = new PlaylistPanel.Header(tr("radio.list.header"), view.tabs);
        channel.addListener(this);
    }

    public JComponent rack() {
        return rack;
    }

    public JComponent listView() {
        return view.component;
    }

    public PlaylistPanel.Header header() {
        return header;
    }

    public void start(Component dialogParent, Consumer<String> infoSink, Consumer<String> optionsOpener) {
        parent = dialogParent;
        info = infoSink;
        openOptions = optionsOpener;
        Ticker.every(250, this::tick);
        showTab(StationBrowserView.Tab.FAVORITES);
        showStation(null);
    }

    public void setActive(boolean active) {
        rack.setDimmed(!active || !powered);
    }

    /** True while a recording runs, e.g. to ask before quitting. */
    public boolean isRecording() {
        return recorder != null && recorder.isRecording();
    }

    /** Ends playback and a recording, e.g. on exit. */
    public void shutdown() {
        stopRecording(false);
        request++;
        channel.stop();
    }

    // ---- station browser -------------------------------------------------------------------------

    void showTab(StationBrowserView.Tab newTab) {
        tab = newTab;
        filter = "";
        view.selectTab(newTab);
        switch (newTab) {
            case FAVORITES -> {
                showFavorites();
                view.status.setText(" ");
            }
            case SEARCH -> {
                showList(List.of());
                view.status.setText(tr("stations.search.hint"));
                view.query.requestFocusInWindow();
            }
            case GENRES -> loadChoices(() -> directory.topTags(150), tag -> tag);
            case COUNTRIES -> loadChoices(directory::countries, WebRadioPresenter::countryName);
        }
        updateInfo();
    }

    /** The Stations button: shows the station list with the favorites, even while another device plays. */
    void showStations() {
        coordinator.showList(SourceId.WEBRADIO);
        showTab(StationBrowserView.Tab.FAVORITES);
        view.table.requestFocusInWindow();
    }

    /** No favorites yet: open the search. */
    private void showSearch() {
        coordinator.showList(SourceId.WEBRADIO);
        showTab(StationBrowserView.Tab.SEARCH);
    }

    private interface ChoiceSource {
        Map<String, Integer> load() throws IOException;
    }

    private void loadChoices(ChoiceSource source, java.util.function.Function<String, String> name) {
        showList(List.of());
        view.choices.clear();
        view.status.setText(tr("stations.loading"));
        int req = ++choiceRequest;
        StationBrowserView.Tab forTab = tab;
        executor.execute(() -> {
            try {
                Map<String, Integer> items = source.load();
                edt.execute(() -> {
                    if (req != choiceRequest || tab != forTab) return;
                    view.setChoices(StationBrowserView.sortedChoices(items, name), filter);
                    view.status.setText(tr(forTab == StationBrowserView.Tab.GENRES ? "stations.genres" : "stations.countries",
                            items.size()));
                });
            } catch (IOException e) {
                edt.execute(() -> view.status.setText(tr("stations.unreachable")));
            }
        });
    }

    void choose(StationBrowserView.Choice choice) {
        StationBrowserView.Tab forTab = tab;
        load(() -> forTab == StationBrowserView.Tab.GENRES ? directory.byTag(choice.key(), LIMIT)
                : directory.byCountry(choice.key(), LIMIT), choice.label());
    }

    /** Typing in the field: filters the open list (the Search tab waits for Enter). */
    void filter(String text) {
        filter = text == null ? "" : text.strip();
        switch (tab) {
            case FAVORITES -> {
                showFavorites();
                view.status.setText(filter.isEmpty() ? " " : tr("stations.found", view.model.stations.size(), "\"" + filter + "\""));
            }
            case GENRES, COUNTRIES -> view.filterChoices(filter);
            case SEARCH -> {
            }
        }
    }

    /** Enter in the field: searches on the Search tab, otherwise opens the first match of the filtered list. */
    void queryEntered(String text) {
        switch (tab) {
            case SEARCH -> search(text);
            case FAVORITES -> {
                if (view.model.getRowCount() > 0) {
                    view.table.getSelectionModel().setSelectionInterval(0, 0);
                    view.table.requestFocusInWindow();
                }
            }
            case GENRES, COUNTRIES -> {
                if (!view.choices.isEmpty()) view.choiceList.setSelectedIndex(0);
            }
        }
    }

    void search(String text) {
        if (text == null || text.isBlank()) return;
        String q = text.strip();
        // "jazz" finds names and genres; a two-letter code also searches the country.
        load(() -> {
            List<Station> byName = directory.search(q, null, null, LIMIT);
            List<Station> byTag = directory.search(null, q, null, LIMIT);
            List<Station> merged = new ArrayList<>(byName);
            for (Station s : byTag) if (merged.stream().noneMatch(m -> m.sameAs(s))) merged.add(s);
            if (q.length() == 2) {
                for (Station s : directory.search(null, null, q, LIMIT)) if (merged.stream().noneMatch(m -> m.sameAs(s))) merged.add(s);
            }
            return merged.size() > LIMIT ? merged.subList(0, LIMIT) : merged;
        }, "\"" + q + "\"");
    }

    private interface StationSource {
        List<Station> load() throws IOException;
    }

    private void load(StationSource source, String what) {
        view.status.setText(tr("stations.searching"));
        int req = ++choiceRequest;
        executor.execute(() -> {
            try {
                List<Station> found = source.load();
                edt.execute(() -> {
                    if (req != choiceRequest) return;
                    showList(found);
                    view.status.setText(tr("stations.found", found.size(), what));
                });
            } catch (IOException e) {
                LOG.info("Station search failed: {}", e.toString());
                edt.execute(() -> view.status.setText(tr("stations.unreachable")));
            }
        });
    }

    /** The favourites matching the filter; reordering is only possible on the whole list. */
    private void showFavorites() {
        List<Station> all = favorites.favorites();
        showList(filter.isEmpty() ? all : all.stream().filter(s -> StationBrowserView.contains(s.name(), filter)
                || StationBrowserView.contains(s.genre(), filter) || StationBrowserView.contains(s.country(), filter)).toList());
        view.up.setEnabled(filter.isEmpty());
        view.down.setEnabled(filter.isEmpty());
    }

    private void showList(List<Station> stations) {
        view.model.set(stations, current);
        updateInfo();
    }

    void selectionChanged() {
        if (channel.state().isActive()) return;
        Station s = view.selected();
        if (s != null) showStation(s);
    }

    void addFavorite() {
        Station s = view.selected();
        if (s == null) s = current;
        if (s == null) return;
        try {
            view.status.setText(tr(favorites.add(s) ? "stations.added" : "stations.already", s.name()));
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
        updateInfo();
    }

    void removeFavorite() {
        Station s = view.selected();
        if (s == null || tab != StationBrowserView.Tab.FAVORITES) return;
        if (JOptionPane.showConfirmDialog(parent, tr("stations.remove.confirm", s.name()), tr("source.webradio"),
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        favorites.remove(s);
        showFavorites();
    }

    void moveFavorite(int delta) {
        Station s = view.selected();
        if (s == null || tab != StationBrowserView.Tab.FAVORITES) return;
        favorites.move(s, delta);
        showFavorites();
        int row = view.model.indexOf(s);
        if (row >= 0) view.table.getSelectionModel().setSelectionInterval(row, row);
    }

    private void updateInfo() {
        int n = favorites.favorites().size();
        info.accept(tr("source.webradio") + " · " + tr("stations.favorites", n));
    }

    // ---- transport -------------------------------------------------------------------------------

    void playSelected() {
        Station s = view.selected();
        if (s != null) play(s, view.model.stations);
    }

    void play() {
        if (!powered) return;
        if (channel.state().isActive()) return;
        Station s = view.selected();
        if (s != null) {
            play(s, view.model.stations);
        } else if (current != null) {
            play(current, context.isEmpty() ? List.of(current) : context);
        } else if (!favorites.favorites().isEmpty()) {
            play(favorites.favorites().get(0), favorites.favorites());
        } else {
            showSearch();
        }
    }

    private void play(Station station, List<Station> list) {
        if (!powered) return;
        if (isRecording() && !station.sameAs(current) && !confirmStopRecording()) return;
        stopRecording(false);
        coordinator.activate(SourceId.WEBRADIO);
        context = List.copyOf(list);
        current = station;
        streamTitle = null;
        view.model.setPlaying(station);
        showStation(station);
        rack.title.setText(tr("radio.connecting"));
        rack.streamInfo.setText(station.streamInfo());
        channel.stop();
        int req = ++request;
        executor.execute(() -> {
            try {
                RadioPlayback p = radio.open(station, events(req));
                edt.execute(() -> {
                    if (req != request) {
                        try {
                            p.close();
                        } catch (IOException e) {
                            LOG.debug("Close failed", e);
                        }
                        return;
                    }
                    playback = p;
                    rack.streamInfo.setText(p.streamInfo());
                    if (streamTitle == null) rack.title.setText(" ");
                    channel.play(p, 0);
                });
                directory.click(station);
            } catch (IOException e) {
                edt.execute(() -> {
                    if (req != request) return;
                    rack.title.setText(tr("radio.no-connection"));
                    error(tr("player.error.play", station.name(), e.getMessage()));
                });
            }
        });
    }

    private RadioSession.Events events(int req) {
        return new RadioSession.Events() {
            @Override
            public void title(String title) {
                StreamRecorder r = recorder;
                if (r != null) r.titleChanged(title);
                edt.execute(() -> {
                    if (req != request) return;
                    streamTitle = title;
                    rack.title.setText(title);
                    view.cover.setText(current == null ? "" : current.name(), "♪ " + title, stationDetails(current));
                });
            }

            @Override
            public void message(String text) {
                edt.execute(() -> {
                    if (req == request) rack.title.setText(text);
                });
            }

            @Override
            public void failed(String reason) {
                edt.execute(() -> {
                    if (req == request) rack.title.setText(tr("radio.connection-lost", reason));
                });
            }
        };
    }

    void stop() {
        if (isRecording() && !confirmStopRecording()) return;
        stopRecording(true);
        request++;
        channel.stop();
        playback = null;
        rack.title.setText(" ");
        rack.buffer.setFill(0);
        view.model.setPlaying(null);
    }

    void next() {
        step(1);
    }

    void previous() {
        step(-1);
    }

    private void step(int delta) {
        List<Station> list = context.isEmpty() ? view.model.stations : context;
        if (list.isEmpty()) return;
        int i = -1;
        for (int k = 0; k < list.size(); k++) if (list.get(k).sameAs(current)) i = k;
        int n = Math.floorMod(i + delta, list.size());
        if (channel.state().isActive()) play(list.get(n), list);
        else {
            current = list.get(n);
            showStation(current);
        }
    }

    void power(boolean on) {
        if (!on) {
            if (isRecording() && !confirmStopRecording()) {
                rack.power.setSelected(true);
                return;
            }
            stopRecording(true);
            request++;
            channel.stop();
            coordinator.deactivate(SourceId.WEBRADIO);
        }
        powered = on;
        rack.setControlsEnabled(on);
        rack.setDimmed(!on || coordinator.active() != SourceId.WEBRADIO);
    }

    private void showStation(Station station) {
        if (station == null) {
            rack.station.setText(tr("radio.lcd.idle"));
            rack.streamInfo.setText(" ");
            view.cover.setText("", "", "");
            view.cover.setImage(null, null);
            return;
        }
        rack.station.setText(station.name());
        rack.streamInfo.setText(station.streamInfo());
        view.cover.setText(station.name(), streamTitle == null ? "" : "♪ " + streamTitle, stationDetails(station));
        view.cover.setImage(null, null);
        executor.execute(() -> {
            Optional<Artwork> logo = artwork.forStation(station);
            edt.execute(() -> {
                if (station != current && station != view.selected()) return;
                view.cover.setImage(logo.map(Artwork::data).orElse(null), logo.map(Artwork::source).orElse(null));
            });
        });
    }

    private static String stationDetails(Station s) {
        if (s == null) return "";
        return String.join(" · ", List.of(s.country(), s.genre(), s.streamInfo()).stream().filter(x -> !x.isBlank()).toList());
    }

    // ---- recording -------------------------------------------------------------------------------

    void record(boolean on) {
        if (on) startRecording();
        else stopRecording(true);
    }

    void recordingOptions() {
        openOptions.accept("Recording");
    }

    private void startRecording() {
        RadioPlayback p = playback;
        if (p == null || channel.state() != TransportState.PLAYING) {
            rack.record.setSelected(false);
            message(tr("radio.record.no-station"));
            return;
        }
        if (!preferences.current().recordingNoticeShown()) {
            int answer = JOptionPane.showConfirmDialog(parent, tr("radio.record.notice"), tr("transport.record"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                rack.record.setSelected(false);
                return;
            }
            preferences.update(prefs -> prefs.withRecordingNoticeShown());
        }
        StreamRecorder r = new StreamRecorder(preferences.current().recording(), p.station().name(), p.codec(),
                Clock.systemDefaultZone(), new Mp3Tagger(), new StreamRecorder.Listener() {
            @Override
            public void status(StreamRecorder.Status status) {
                edt.execute(() -> showRecordingStatus(status));
            }

            @Override
            public void stopped(String reason, List<Path> files) {
                edt.execute(() -> {
                    p.session().setTap(null);
                    recorder = null;
                    rack.record.setSelected(false);
                    rack.recording.setText(" ");
                    message(reason + "\n" + saved(files));
                });
            }
        });
        try {
            r.start(p.session().lastTitle());
        } catch (IOException e) {
            rack.record.setSelected(false);
            error(tr("radio.record.error", e.getMessage()));
            return;
        }
        recorder = r;
        p.session().setTap(r::write);
        rack.recording.setText("● REC 00:00");
    }

    private void stopRecording(boolean report) {
        StreamRecorder r = recorder;
        recorder = null;
        rack.record.setSelected(false);
        rack.recording.setText(" ");
        if (r == null) return;
        RadioPlayback p = playback;
        if (p != null) p.session().setTap(null);
        List<Path> files = r.stop();
        if (report && !files.isEmpty()) message(saved(files));
    }

    private boolean confirmStopRecording() {
        return JOptionPane.showConfirmDialog(parent, tr("radio.record.stop-confirm"), tr("source.webradio"),
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }

    private void showRecordingStatus(StreamRecorder.Status status) {
        long s = status.elapsed().toSeconds();
        String time = s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%02d:%02d", s / 60, s % 60);
        rack.recording.setText(String.format(Locale.ROOT, "● REC %s  %.1f MB", time, status.bytes() / 1_048_576.0));
    }

    private static String saved(List<Path> files) {
        if (files.isEmpty()) return tr("radio.record.nothing");
        Path dir = files.get(0).getParent();
        return files.size() == 1 ? tr("radio.record.saved-one", files.get(0).toString())
                : tr("radio.record.saved-many", files.size(), String.valueOf(dir));
    }

    // ---- playback events -------------------------------------------------------------------------

    private void tick() {
        RadioPlayback p = playback;
        if (p != null && channel.state().isActive()) rack.buffer.setFill(p.session().bufferFill());
        if (isRecording() && System.currentTimeMillis() / 600 % 2 == 0) {
            rack.recording.setForeground(new Color(0xFF, 0x4B, 0x3A));
        } else if (isRecording()) {
            rack.recording.setForeground(new Color(0x80, 0x26, 0x1D));
        }
    }

    @Override
    public void stateChanged(SourceId source, TransportState state, String message) {
        edt.execute(() -> {
            if (state == TransportState.ERROR) {
                stopRecording(true);
                rack.title.setText(tr("radio.error", message));
            }
            if (state == TransportState.STOPPED && !channel.state().isActive()) {
                stopRecording(true);
                rack.meter.setLevels(0, 0);
                rack.buffer.setFill(0);
            }
        });
    }

    @Override
    public void finished(SourceId source) {
        edt.execute(() -> {
            rack.title.setText(tr("radio.ended"));
            view.model.setPlaying(null);
        });
    }

    @Override
    public void levels(SourceId source, AudioLevels levels) {
        edt.execute(() -> rack.meter.setLevels(levels.left(), levels.right()));
    }

    private static String countryName(String code) {
        String name = Locale.of("", code).getDisplayCountry(Locale.getDefault());
        return name.isBlank() || name.equals(code) ? code : name;
    }

    private void error(String text) {
        JOptionPane.showMessageDialog(parent, text, tr("source.webradio"), JOptionPane.ERROR_MESSAGE);
    }

    private void message(String text) {
        JOptionPane.showMessageDialog(parent, text, tr("source.webradio"), JOptionPane.INFORMATION_MESSAGE);
    }
}

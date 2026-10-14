package org.datazuul.amadeos.audio.ui.main;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.SourceId;
import org.datazuul.amadeos.audio.repository.PreferencesStore;
import org.datazuul.amadeos.audio.service.PlaybackCoordinator;
import org.datazuul.amadeos.audio.service.engine.AudioEngine;
import org.datazuul.amadeos.audio.ui.options.OptionsDialog;
import org.datazuul.amadeos.audio.ui.playlist.PlaylistPanel;
import org.datazuul.amadeos.audio.ui.rack.cd.CdPresenter;
import org.datazuul.amadeos.audio.ui.rack.mixer.MixerPresenter;
import org.datazuul.amadeos.audio.ui.rack.player.PlayerPresenter;
import org.datazuul.amadeos.audio.ui.rack.webradio.WebRadioPresenter;
import org.datazuul.amadeos.audio.ui.widget.LcdPalette;
import org.datazuul.amadeos.audio.ui.widget.Ticker;
import org.datazuul.amadeos.commons.swing.EdtExecutor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Builds the window from the racks, switches the playlist panel to the active device, shows and hides
 * racks, and handles options, about and quitting. Runs on the EDT.
 */
public final class MainPresenter {
    private final PlaybackCoordinator coordinator;
    private final AudioEngine engine;
    private final MixerPresenter mixer;
    private final CdPresenter cd;
    private final PlayerPresenter player;
    private final WebRadioPresenter webRadio;
    private final PreferencesStore preferences;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final PlaylistPanel playlist = new PlaylistPanel();
    private HeaderRack header;
    private MainWindow window;

    @Inject
    public MainPresenter(PlaybackCoordinator coordinator, AudioEngine engine, MixerPresenter mixer, CdPresenter cd,
                         PlayerPresenter player, WebRadioPresenter webRadio, PreferencesStore preferences,
                         ExecutorService executor, EdtExecutor edt) {
        this.coordinator = coordinator;
        this.engine = engine;
        this.mixer = mixer;
        this.cd = cd;
        this.player = player;
        this.webRadio = webRadio;
        this.preferences = preferences;
        this.executor = executor;
        this.edt = edt;
    }

    /** Builds and shows the window; arguments are files or folders to play. */
    public void start(List<String> args) {
        EdtExecutor.requireEdt();
        LcdPalette.setSetting(preferences.current().lcdColor());
        header = new HeaderRack(this, this::isLit);
        playlist.addView(SourceId.CD, cd.header(), cd.listView());
        playlist.addView(SourceId.PLAYER, player.header(), player.listView());
        playlist.addView(SourceId.WEBRADIO, webRadio.header(), webRadio.listView());
        Map<String, JComponent> racks = new LinkedHashMap<>();
        racks.put("mixer", mixer.rack());
        racks.put("cd", cd.rack());
        racks.put("player", player.rack());
        racks.put("webradio", webRadio.rack());
        window = new MainWindow(header, racks, playlist);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });
        for (String hidden : preferences.current().hiddenRacks()) {
            JToggleButton b = header.rackButtons.get(hidden);
            if (b != null) b.setSelected(false);
            window.setRackVisible(hidden, false);
        }
        coordinator.addListener(id -> edt.execute(() -> activeChanged(id)));
        activeChanged(null);

        mixer.start();
        cd.start(window, text -> playlist.setInfo(SourceId.CD, text));
        player.start(window, text -> playlist.setInfo(SourceId.PLAYER, text));
        webRadio.start(window, text -> playlist.setInfo(SourceId.WEBRADIO, text), this::openOptions);
        window.setVisible(true);

        List<Path> files = args.stream().filter(a -> !a.isBlank() && !a.startsWith("-")).map(Path::of).toList();
        if (!files.isEmpty()) player.add(files, true);
    }

    private boolean isLit(String rack) {
        SourceId active = coordinator.active();
        return switch (rack) {
            case "mixer" -> !engine.isMuted();
            case "cd" -> active == SourceId.CD;
            case "player" -> active == SourceId.PLAYER;
            case "webradio" -> active == SourceId.WEBRADIO;
            default -> false;
        };
    }

    private void activeChanged(SourceId id) {
        cd.setActive(id == SourceId.CD);
        player.setActive(id == SourceId.PLAYER);
        webRadio.setActive(id == SourceId.WEBRADIO);
        if (id != null) playlist.show(id);
        window.setPlaylistVisible(id != null);
        header.repaint();
    }

    void setRackVisible(String rack, boolean visible) {
        window.setRackVisible(rack, visible);
        preferences.update(p -> {
            Set<String> hidden = new HashSet<>(p.hiddenRacks());
            if (visible) hidden.remove(rack);
            else hidden.add(rack);
            return p.withHiddenRacks(hidden);
        });
    }

    void openFiles() {
        showRack("player");
        player.openFiles();
    }

    void openFolder() {
        showRack("player");
        player.openFolder();
    }

    private void showRack(String rack) {
        JToggleButton b = header.rackButtons.get(rack);
        if (b != null && !b.isSelected()) {
            b.setSelected(true);
            setRackVisible(rack, true);
        }
    }

    void openOptions(String page) {
        new OptionsDialog(window, preferences.current(), page, prefs -> {
            preferences.update(p -> prefs);
            LcdPalette.setSetting(prefs.lcdColor());
            window.repaint();
        }).setVisible(true);
    }

    void minimize() {
        window.setState(Frame.ICONIFIED);
    }

    void about() {
        Icon icon = window.getIconImages().isEmpty() ? null : new ImageIcon(window.getIconImages().get(0));
        JOptionPane.showMessageDialog(window, """
                        amade-audio — Audio Station for amadeOS
                        Audio CDs, music files (MP3, Ogg Vorbis, AAC, FLAC, WAV, MIDI)
                        and internet radio in a rack.

                        Titles from MusicBrainz, covers from the Cover Art Archive,
                        stations from radio-browser.info.

                        Licensed under the EUPL-1.2.
                        https://github.com/datazuul/amade-audio""",
                "About Audio Station", JOptionPane.INFORMATION_MESSAGE, icon);
    }

    void quit() {
        if (webRadio.isRecording() && JOptionPane.showConfirmDialog(window,
                "A recording is running. Stop it and quit?", "Audio Station",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        webRadio.shutdown();
        engine.stopAll();
        preferences.flush();
        for (Window w : Window.getWindows()) w.dispose();
        Ticker.stopAll();
        executor.shutdownNow();
    }
}

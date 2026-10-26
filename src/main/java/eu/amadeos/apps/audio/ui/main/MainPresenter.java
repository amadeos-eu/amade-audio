package eu.amadeos.apps.audio.ui.main;

import static eu.amadeos.commons.i18n.Texts.tr;

import com.google.inject.Inject;
import eu.amadeos.apps.audio.domain.SourceId;
import eu.amadeos.apps.audio.repository.PreferencesStore;
import eu.amadeos.apps.audio.service.PlaybackCoordinator;
import eu.amadeos.apps.audio.service.engine.AudioEngine;
import eu.amadeos.commons.desktop.PasswordStore;
import eu.amadeos.apps.audio.ui.options.OptionsDialog;
import eu.amadeos.apps.audio.ui.playlist.PlaylistPanel;
import eu.amadeos.apps.audio.ui.rack.cd.CdPresenter;
import eu.amadeos.apps.audio.ui.rack.mixer.MixerPresenter;
import eu.amadeos.apps.audio.ui.rack.player.PlayerPresenter;
import eu.amadeos.apps.audio.ui.rack.webradio.WebRadioPresenter;
import eu.amadeos.apps.audio.ui.widget.LcdPalette;
import eu.amadeos.apps.audio.ui.widget.Ticker;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.net.Network;
import eu.amadeos.commons.net.ProxySettings;
import eu.amadeos.commons.net.ProxyStore;
import eu.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Builds the window from the racks, switches the playlist panel to the active device, shows and hides
 * racks, and handles options, about and quitting. Runs on the EDT.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    private final PlaybackCoordinator coordinator;
    private final AudioEngine engine;
    private final MixerPresenter mixer;
    private final CdPresenter cd;
    private final PlayerPresenter player;
    private final WebRadioPresenter webRadio;
    private final PreferencesStore preferences;
    private final ExecutorService executor;
    private final EdtExecutor edt;
    private final PasswordStore passwords;
    private final LocaleSettings locales;
    private final PlaylistPanel playlist = new PlaylistPanel();
    private HeaderRack header;
    private MainWindow window;

    @Inject
    public MainPresenter(PlaybackCoordinator coordinator, AudioEngine engine, MixerPresenter mixer, CdPresenter cd,
                         PlayerPresenter player, WebRadioPresenter webRadio, PreferencesStore preferences,
                         ExecutorService executor, EdtExecutor edt, PasswordStore passwords,
                         LocaleSettings locales) {
        this.locales = locales;
        this.coordinator = coordinator;
        this.engine = engine;
        this.mixer = mixer;
        this.cd = cd;
        this.player = player;
        this.webRadio = webRadio;
        this.preferences = preferences;
        this.executor = executor;
        this.edt = edt;
        this.passwords = passwords;
    }

    /** Key of the proxy password of earlier versions in the program's keyring. */
    static final String PROXY_PASSWORD = "proxy";

    /** Builds and shows the window; arguments are files or folders to play. */
    public void start(List<String> args) {
        EdtExecutor.requireEdt();
        LcdPalette.setSetting(preferences.current().lcdColor());
        moveProxyToDesktop();
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
        coordinator.addListRequestListener(id -> edt.execute(() -> {
            playlist.show(id);
            window.setPlaylistVisible(true);
        }));
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

    // --8<-- [start:move-proxy]
    /**
     * Earlier versions had a proxy of their own. Moves it to Settings &gt; Network once, unless the desktop has a
     * proxy setting already; Audio's keyring entry goes, the desktop's keyring has the password now.
     */
    private void moveProxyToDesktop() {
        Optional<ProxySettings> legacy = preferences.legacyProxy();
        if (legacy.isEmpty()) return;
        executor.execute(() -> {
            ProxyStore store = Network.store();
            if (!store.exists()) {
                String password = legacy.get().user().isEmpty() ? "" : passwords.lookup(PROXY_PASSWORD).orElse("");
                store.write(legacy.get(), password);
                LOG.info("Moved the proxy of Audio Station to Settings > Network");
            }
            passwords.delete(PROXY_PASSWORD);
            preferences.update(p -> p); // saves audio.conf without the old proxy
        });
    }
    // --8<-- [end:move-proxy]

    void openOptions(String page) {
        String proxy = Network.proxy().describe().orElse(tr("proxy.direct"));
        Runnable networkSettings = desktopSettings().map(command -> (Runnable) () -> {
            try {
                new ProcessBuilder(command, "network").start();
            } catch (IOException e) {
                LOG.warn("Cannot open Settings > Network: {}", e.toString());
            }
        }).orElse(null);
        OptionsDialog dialog = new OptionsDialog(window, preferences.current(), page, proxy, networkSettings,
                prefs -> {
                    preferences.update(p -> prefs);
                    LcdPalette.setSetting(prefs.lcdColor());
                    window.repaint();
                }, locales);
        dialog.setVisible(true);
    }

    /** {@code amadeos-settings}, if this runs on amadeOS. */
    private static Optional<String> desktopSettings() {
        Path settings = Path.of("/usr/bin/amadeos-settings");
        return Files.isExecutable(settings) ? Optional.of(settings.toString()) : Optional.empty();
    }

    void minimize() {
        window.setState(Frame.ICONIFIED);
    }

    void about() {
        Icon icon = window.getIconImages().isEmpty() ? null : new ImageIcon(window.getIconImages().get(0));
        JOptionPane.showMessageDialog(window, tr("about.text") + "\nhttps://github.com/datazuul/amade-audio",
                tr("about.title"), JOptionPane.INFORMATION_MESSAGE, icon);
    }

    void quit() {
        if (webRadio.isRecording() && JOptionPane.showConfirmDialog(window,
                tr("quit.recording"), tr("app.title"),
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        webRadio.shutdown();
        engine.stopAll();
        preferences.flush();
        for (Window w : Window.getWindows()) w.dispose();
        Ticker.stopAll();
        executor.shutdownNow();
    }
}

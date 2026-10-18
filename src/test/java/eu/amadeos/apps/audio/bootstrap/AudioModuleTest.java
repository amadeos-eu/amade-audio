package eu.amadeos.apps.audio.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import eu.amadeos.apps.audio.service.PlaybackCoordinator;
import eu.amadeos.apps.audio.service.engine.AudioEngine;
import eu.amadeos.apps.audio.ui.main.MainPresenter;
import eu.amadeos.apps.audio.ui.rack.player.PlayerPresenter;
import eu.amadeos.commons.app.CommonsModule;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.theme.ThemeRegistry;
import eu.amadeos.commons.theme.ThemeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The application can be put together: every binding resolves and the shared services are singletons. */
class AudioModuleTest {
    @TempDir
    Path home;

    @Test
    void wiresTheApplication() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run under Xvfb)");
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        XdgDirectories xdg = new XdgDirectories(Map.of(), home.toString());
        ThemeStore themes = new ThemeStore(xdg.amadeosConfig(), new ThemeRegistry());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Injector injector = Guice.createInjector(
                        new CommonsModule(AmadeAudioApplication.APP, executor, themes, xdg), new AudioModule());
                assertNotNull(injector.getInstance(MainPresenter.class));
                assertSame(injector.getInstance(AudioEngine.class), injector.getInstance(AudioEngine.class));
                assertSame(injector.getInstance(PlaybackCoordinator.class), injector.getInstance(PlaybackCoordinator.class));
                assertSame(injector.getInstance(PlayerPresenter.class), injector.getInstance(PlayerPresenter.class));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        executor.shutdownNow();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}

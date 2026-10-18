package eu.amadeos.audio.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.audio.repository.PreferencesRepository;
import eu.amadeos.audio.repository.PreferencesStore;
import eu.amadeos.audio.service.PlaybackCoordinator;
import eu.amadeos.audio.service.cd.CdDrive;
import eu.amadeos.audio.service.cd.LibCdioDrive;
import eu.amadeos.audio.service.cd.WindowsCdDrive;
import eu.amadeos.audio.service.decode.DecoderFactory;
import eu.amadeos.audio.service.engine.AudioEngine;
import eu.amadeos.audio.service.metadata.ArtworkService;
import eu.amadeos.audio.service.metadata.CdMetadataService;
import eu.amadeos.audio.service.metadata.HttpFetcher;
import eu.amadeos.audio.service.metadata.MetadataCache;
import eu.amadeos.audio.service.metadata.MusicBrainzClient;
import eu.amadeos.audio.service.mixer.SoundServer;
import eu.amadeos.audio.service.player.TagReader;
import eu.amadeos.audio.service.webradio.RadioBrowserClient;
import eu.amadeos.audio.service.webradio.StationRepository;
import eu.amadeos.audio.service.webradio.WebRadioService;
import eu.amadeos.audio.ui.main.MainPresenter;
import eu.amadeos.audio.ui.rack.cd.CdPresenter;
import eu.amadeos.audio.ui.rack.mixer.MixerPresenter;
import eu.amadeos.audio.ui.rack.player.PlayerPresenter;
import eu.amadeos.audio.ui.rack.webradio.WebRadioPresenter;
import eu.amadeos.commons.config.AppDirectories;

/**
 * Audio Station bindings; executor, directories, theme and EDT executor come from
 * {@link eu.amadeos.commons.app.CommonsModule}. Everything is a singleton: there is one
 * engine, one drive and one presenter per rack.
 */
public final class AudioModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AudioEngine.class).in(Scopes.SINGLETON);
        bind(PlaybackCoordinator.class).in(Scopes.SINGLETON);
        bind(DecoderFactory.class).in(Scopes.SINGLETON);
        bind(TagReader.class).in(Scopes.SINGLETON);
        bind(CdDrive.class).to(cdDrive()).in(Scopes.SINGLETON);
        bind(HttpFetcher.class).toInstance(HttpFetcher.system());
        bind(MusicBrainzClient.class).in(Scopes.SINGLETON);
        bind(CdMetadataService.class).in(Scopes.SINGLETON);
        bind(ArtworkService.class).in(Scopes.SINGLETON);
        bind(RadioBrowserClient.class).in(Scopes.SINGLETON);
        bind(StationRepository.class).in(Scopes.SINGLETON);
        bind(WebRadioService.class).in(Scopes.SINGLETON);
        bind(SoundServer.class).in(Scopes.SINGLETON);
        bind(PreferencesRepository.class).in(Scopes.SINGLETON);
        bind(PreferencesStore.class).in(Scopes.SINGLETON);
        bind(MixerPresenter.class).in(Scopes.SINGLETON);
        bind(CdPresenter.class).in(Scopes.SINGLETON);
        bind(PlayerPresenter.class).in(Scopes.SINGLETON);
        bind(WebRadioPresenter.class).in(Scopes.SINGLETON);
        bind(MainPresenter.class).in(Scopes.SINGLETON);
    }

    /** Windows reads CDs through its own CD-ROM driver, everything else through libcdio. */
    static Class<? extends CdDrive> cdDrive() {
        return System.getProperty("os.name", "").startsWith("Windows") ? WindowsCdDrive.class : LibCdioDrive.class;
    }

    @Provides
    @Singleton
    MetadataCache metadataCache(AppDirectories directories) {
        return new MetadataCache(directories.cache());
    }
}

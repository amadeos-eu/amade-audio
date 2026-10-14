package org.datazuul.amadeos.audio.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import org.datazuul.amadeos.audio.repository.PreferencesRepository;
import org.datazuul.amadeos.audio.repository.PreferencesStore;
import org.datazuul.amadeos.audio.service.PlaybackCoordinator;
import org.datazuul.amadeos.audio.service.cd.CdDrive;
import org.datazuul.amadeos.audio.service.cd.LibCdioDrive;
import org.datazuul.amadeos.audio.service.cd.WindowsCdDrive;
import org.datazuul.amadeos.audio.service.decode.DecoderFactory;
import org.datazuul.amadeos.audio.service.engine.AudioEngine;
import org.datazuul.amadeos.audio.service.metadata.ArtworkService;
import org.datazuul.amadeos.audio.service.metadata.CdMetadataService;
import org.datazuul.amadeos.audio.service.metadata.HttpFetcher;
import org.datazuul.amadeos.audio.service.metadata.MetadataCache;
import org.datazuul.amadeos.audio.service.metadata.MusicBrainzClient;
import org.datazuul.amadeos.audio.service.mixer.SoundServer;
import org.datazuul.amadeos.audio.service.player.TagReader;
import org.datazuul.amadeos.audio.service.webradio.RadioBrowserClient;
import org.datazuul.amadeos.audio.service.webradio.StationRepository;
import org.datazuul.amadeos.audio.service.webradio.WebRadioService;
import org.datazuul.amadeos.audio.ui.main.MainPresenter;
import org.datazuul.amadeos.audio.ui.rack.cd.CdPresenter;
import org.datazuul.amadeos.audio.ui.rack.mixer.MixerPresenter;
import org.datazuul.amadeos.audio.ui.rack.player.PlayerPresenter;
import org.datazuul.amadeos.audio.ui.rack.webradio.WebRadioPresenter;
import org.datazuul.amadeos.commons.config.AppDirectories;

/**
 * Audio Station bindings; executor, directories, theme and EDT executor come from
 * {@link org.datazuul.amadeos.commons.app.CommonsModule}. Everything is a singleton: there is one
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

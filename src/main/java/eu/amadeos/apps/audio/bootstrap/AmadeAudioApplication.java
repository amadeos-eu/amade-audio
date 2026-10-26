package eu.amadeos.apps.audio.bootstrap;

import eu.amadeos.apps.audio.domain.AudioPreferences;
import eu.amadeos.apps.audio.repository.PreferencesStore;
import eu.amadeos.apps.audio.ui.main.MainPresenter;
import eu.amadeos.apps.audio.ui.widget.Scale;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.util.List;

/** Entry point. Arguments are audio files, folders or M3U playlists to play, as passed by the desktop entry. */
public final class AmadeAudioApplication {
    public static final AppInfo APP = new AppInfo("amade-audio", "Audio Station", "audio");

    private AmadeAudioApplication() {
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            // The racks are built in the presenters' constructors, at the size from the settings.
            int size = injector.getInstance(PreferencesStore.class).current().size();
            Scale.setPercent(size == AudioPreferences.AUTO ? Scale.fittingMainScreen() : size);
            injector.getInstance(MainPresenter.class).start(List.of(args));
        }, new AudioModule());
    }
}

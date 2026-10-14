package org.datazuul.amadeos.audio.bootstrap;

import org.datazuul.amadeos.audio.ui.main.MainPresenter;
import org.datazuul.amadeos.commons.app.AmadeApplication;
import org.datazuul.amadeos.commons.app.AppInfo;

import java.util.List;

/** Entry point. Arguments are audio files, folders or M3U playlists to play, as passed by the desktop entry. */
public final class AmadeAudioApplication {
    public static final AppInfo APP = new AppInfo("amade-audio", "Audio Station", "audio");

    private AmadeAudioApplication() {
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> injector.getInstance(MainPresenter.class).start(List.of(args)),
                new AudioModule());
    }
}

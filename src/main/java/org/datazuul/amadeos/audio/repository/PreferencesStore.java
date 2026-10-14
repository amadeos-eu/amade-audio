package org.datazuul.amadeos.audio.repository;

import com.google.inject.Inject;
import org.datazuul.amadeos.audio.domain.AudioPreferences;

import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;

/** The current {@link AudioPreferences}, shared by the presenters; changes are saved in the background. */
public final class PreferencesStore {
    private final PreferencesRepository repository;
    private final ExecutorService executor;
    private volatile AudioPreferences current;

    @Inject
    public PreferencesStore(PreferencesRepository repository, ExecutorService executor) {
        this.repository = repository;
        this.executor = executor;
        this.current = repository.load();
    }

    public AudioPreferences current() {
        return current;
    }

    public synchronized void update(UnaryOperator<AudioPreferences> change) {
        current = change.apply(current);
        AudioPreferences snapshot = current;
        executor.execute(() -> repository.save(snapshot));
    }

    /** Saves now, e.g. on exit. */
    public void flush() {
        repository.save(current);
    }
}

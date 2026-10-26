package eu.amadeos.apps.audio.repository;

import eu.amadeos.commons.net.ProxySettings;
import com.google.inject.Inject;
import eu.amadeos.apps.audio.domain.AudioPreferences;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;

/** The current {@link AudioPreferences}, shared by the presenters; changes are saved in the background. */
public final class PreferencesStore {
    private final PreferencesRepository repository;
    private final ExecutorService executor;
    private final Optional<ProxySettings> legacyProxy;
    private volatile AudioPreferences current;

    @Inject
    public PreferencesStore(PreferencesRepository repository, ExecutorService executor) {
        this.repository = repository;
        this.executor = executor;
        this.legacyProxy = repository.legacyProxy();
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

    /** The proxy of earlier versions, read before the first save drops it; see {@link PreferencesRepository#legacyProxy()}. */
    public Optional<ProxySettings> legacyProxy() {
        return legacyProxy;
    }

    /** Saves now, e.g. on exit. */
    public void flush() {
        repository.save(current);
    }
}

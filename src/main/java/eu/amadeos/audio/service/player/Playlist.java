package eu.amadeos.audio.service.player;

import eu.amadeos.audio.domain.PlaybackOptions;
import eu.amadeos.audio.domain.PlaylistEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The player's list of files with the current position, shuffle and repeat. Not thread-safe; used on
 * the EDT.
 */
public final class Playlist {
    private final List<PlaylistEntry> entries = new ArrayList<>();
    private final Random random;
    private List<Integer> shuffleOrder = new ArrayList<>();
    private int current = -1;
    private PlaybackOptions options = PlaybackOptions.NORMAL;

    public Playlist() {
        this(new Random());
    }

    Playlist(Random random) {
        this.random = random;
    }

    public List<PlaylistEntry> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Index of the current entry, -1 if none. */
    public int currentIndex() {
        return current;
    }

    public PlaylistEntry current() {
        return current >= 0 && current < entries.size() ? entries.get(current) : null;
    }

    public void setCurrent(int index) {
        current = index >= 0 && index < entries.size() ? index : -1;
    }

    public PlaybackOptions options() {
        return options;
    }

    public void setOptions(PlaybackOptions value) {
        boolean reshuffle = value.shuffle() && !options.shuffle();
        options = value;
        if (reshuffle) reshuffle();
    }

    public void addAll(List<PlaylistEntry> added) {
        entries.addAll(added);
        reshuffle();
    }

    /** Replaces an entry, e.g. when its tags have been read. */
    public void replace(int index, PlaylistEntry entry) {
        if (index >= 0 && index < entries.size()) entries.set(index, entry);
    }

    public void remove(int[] indices) {
        int[] sorted = indices.clone();
        java.util.Arrays.sort(sorted);
        for (int k = sorted.length - 1; k >= 0; k--) {
            int i = sorted[k];
            if (i < 0 || i >= entries.size()) continue;
            entries.remove(i);
            if (i < current) current--;
            else if (i == current) current = -1;
        }
        reshuffle();
    }

    public void clear() {
        entries.clear();
        current = -1;
        shuffleOrder.clear();
    }

    /** Index of the next entry to play after the current one, or -1 at the end (without repeat). */
    public int next() {
        if (entries.isEmpty()) return -1;
        if (options.shuffle()) {
            int pos = shuffleOrder.indexOf(current);
            if (pos + 1 < shuffleOrder.size()) return shuffleOrder.get(pos + 1);
            if (!options.repeat()) return -1;
            reshuffle();
            return shuffleOrder.get(0);
        }
        if (current + 1 < entries.size()) return current + 1;
        return options.repeat() ? 0 : -1;
    }

    /** Index of the entry before the current one, or -1. */
    public int previous() {
        if (entries.isEmpty()) return -1;
        if (options.shuffle()) {
            int pos = shuffleOrder.indexOf(current);
            return pos > 0 ? shuffleOrder.get(pos - 1) : -1;
        }
        if (current > 0) return current - 1;
        return options.repeat() ? entries.size() - 1 : -1;
    }

    /** Where to start playing when Play is pressed without a selection. */
    public int first() {
        if (entries.isEmpty()) return -1;
        return options.shuffle() ? shuffleOrder.get(0) : 0;
    }

    public Duration totalLength() {
        return entries.stream().map(PlaylistEntry::length).reduce(Duration.ZERO, Duration::plus);
    }

    private void reshuffle() {
        shuffleOrder = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) shuffleOrder.add(i);
        Collections.shuffle(shuffleOrder, random);
        if (current >= 0) {
            // The current entry first, so "next" continues through the rest.
            shuffleOrder.remove(Integer.valueOf(current));
            shuffleOrder.add(0, current);
        }
    }
}

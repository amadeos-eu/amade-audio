package org.datazuul.amadeos.audio.ui.widget;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Repeating Swing timers of the racks, stopped together on exit so the application can end. */
public final class Ticker {
    private static final List<Timer> TIMERS = new CopyOnWriteArrayList<>();

    private Ticker() {
    }

    /** Runs the action on the EDT every {@code millis} milliseconds. */
    public static Timer every(int millis, Runnable action) {
        Timer timer = new Timer(millis, e -> action.run());
        TIMERS.add(timer);
        timer.start();
        return timer;
    }

    public static void stopAll() {
        TIMERS.forEach(Timer::stop);
        TIMERS.clear();
    }
}

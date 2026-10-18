package eu.amadeos.audio.service.decode;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Converts the events of a MIDI sequence to microsecond time stamps, following tempo changes. */
final class TempoMap {
    /** 120 BPM, the MIDI default. */
    static final int DEFAULT_TEMPO = 500_000;

    /** An event with its time from the start of the sequence. */
    record TimedEvent(long micros, MidiEvent event) {
    }

    private TempoMap() {
    }

    /** All events of all tracks in time order, with micro-second time stamps. */
    static List<TimedEvent> timed(Sequence sequence) {
        List<MidiEvent> events = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) events.add(track.get(i));
        }
        events.sort(Comparator.comparingLong(MidiEvent::getTick)); // stable: keeps track order per tick
        List<TimedEvent> result = new ArrayList<>(events.size());
        float division = sequence.getDivisionType();
        int resolution = sequence.getResolution();
        long tempo = DEFAULT_TEMPO;
        long lastTick = 0;
        double lastMicros = 0;
        for (MidiEvent event : events) {
            long tick = event.getTick();
            double micros;
            if (division == Sequence.PPQ) {
                micros = lastMicros + (tick - lastTick) * (double) tempo / resolution;
            } else {
                micros = tick * 1_000_000.0 / (division * resolution);
            }
            lastTick = tick;
            lastMicros = micros;
            result.add(new TimedEvent(Math.round(micros), event));
            if (event.getMessage() instanceof MetaMessage meta && meta.getType() == 0x51 && meta.getData().length == 3) {
                byte[] d = meta.getData();
                tempo = ((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF);
            }
        }
        return result;
    }
}

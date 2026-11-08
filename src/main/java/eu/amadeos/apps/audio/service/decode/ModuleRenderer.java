package eu.amadeos.apps.audio.service.decode;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import eu.amadeos.apps.audio.service.engine.PcmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Renders tracker modules (MOD, XM, S3M, IT and their older relatives) to PCM with libopenmpt, so they go
 * through the same mixer channel, meters and seek bar as every other format. Without libopenmpt (Debian
 * {@code libopenmpt0t64}, on Windows {@code libopenmpt.dll} next to the program) modules cannot be played;
 * everything else still works.
 */
public final class ModuleRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(ModuleRenderer.class);
    static final AudioFormat FORMAT = PcmSource.pcm16(44100f, 2);
    private static final int RATE = (int) FORMAT.getSampleRate();
    /** Modules are small (a few MB at most); anything much larger is not a module. */
    private static final long MAX_SIZE = 64L * 1024 * 1024;

    /** What libopenmpt tells about a module without playing it. */
    public record ModuleInfo(String title, String artist, Duration length, String type) {
    }

    /** Whether libopenmpt could be loaded. */
    public static boolean available() {
        return Loaded.LIB != null;
    }

    /** Opens a module for playback; it plays once to its end, without the loops some songs have. */
    public PcmSource open(Path file) throws IOException {
        return new ModuleSource(load(file));
    }

    /** Title, artist, length and type of a module, if libopenmpt can read it. */
    public Optional<ModuleInfo> info(Path file) {
        if (!available()) return Optional.empty();
        Pointer module;
        try {
            module = load(file);
        } catch (IOException e) {
            return Optional.empty();
        }
        try {
            LibOpenMpt lib = Loaded.LIB;
            double seconds = lib.openmpt_module_get_duration_seconds(module);
            Duration length = Double.isFinite(seconds) && seconds > 0
                    ? Duration.ofMillis(Math.round(seconds * 1000)) : Duration.ZERO;
            return Optional.of(new ModuleInfo(metadata(module, "title"), metadata(module, "artist"), length,
                    metadata(module, "type_long")));
        } finally {
            Loaded.LIB.openmpt_module_destroy(module);
        }
    }

    // --8<-- [start:load]
    private static Pointer load(Path file) throws IOException {
        LibOpenMpt lib = Loaded.LIB;
        if (lib == null) {
            throw new IOException("libopenmpt is not installed (Debian package libopenmpt0t64)");
        }
        if (Files.size(file) > MAX_SIZE) throw new IOException("Not a module: " + file.getFileName());
        byte[] data = Files.readAllBytes(file);
        IntByReference error = new IntByReference();
        PointerByReference message = new PointerByReference();
        Pointer module = lib.openmpt_module_create_from_memory2(data, new SizeT(data.length), Loaded.SILENT_LOG,
                null, null, null, error, message, null);
        if (module == null) {
            String reason = takeString(message.getValue());
            throw new IOException("Not a playable module: " + file.getFileName()
                    + (reason == null ? "" : " (" + reason + ")"));
        }
        return module;
    }
    // --8<-- [end:load]

    private static String metadata(Pointer module, String key) {
        String value = takeString(Loaded.LIB.openmpt_module_get_metadata(module, key));
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Reads a string libopenmpt allocated, then frees it. */
    private static String takeString(Pointer string) {
        if (string == null) return null;
        try {
            return string.getString(0, "UTF-8");
        } finally {
            Loaded.LIB.openmpt_free_string(string);
        }
    }

    // --8<-- [start:source]
    /**
     * A module being rendered. read and close are synchronized: the player may stop (close) from the event
     * thread while the playback thread is inside libopenmpt, and the module must not be freed under it.
     */
    private static final class ModuleSource implements PcmSource {
        private final LibOpenMpt lib = Loaded.LIB;
        private final long frameLength;
        private Pointer module;
        private Memory buffer;
        private long position;

        ModuleSource(Pointer module) {
            this.module = module;
            double seconds = lib.openmpt_module_get_duration_seconds(module);
            this.frameLength = Double.isFinite(seconds) && seconds > 0 ? Math.round(seconds * RATE) : -1;
        }

        @Override
        public AudioFormat format() {
            return FORMAT;
        }

        @Override
        public long frameLength() {
            return frameLength;
        }

        @Override
        public synchronized int read(byte[] target, int offset, int length) throws IOException {
            if (module == null) return -1;
            int frames = length / 4;
            if (frames == 0) return 0;
            if (buffer == null || buffer.size() < frames * 4L) buffer = new Memory(frames * 4L);
            int rendered = (int) lib.openmpt_module_read_interleaved_stereo(module, RATE, new SizeT(frames), buffer)
                    .longValue();
            if (rendered <= 0) return -1;
            int bytes = rendered * 4;
            buffer.read(0, target, offset, bytes);
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                for (int i = offset; i < offset + bytes; i += 2) {
                    byte b = target[i];
                    target[i] = target[i + 1];
                    target[i + 1] = b;
                }
            }
            position += rendered;
            return bytes;
        }

        /** Jumps instead of rendering the skipped part. */
        @Override
        public synchronized long skipFrames(long frames) {
            if (module == null || frames <= 0) return 0;
            double reached = lib.openmpt_module_set_position_seconds(module, (position + frames) / (double) RATE);
            long before = position;
            position = Math.max(0, Math.round(reached * RATE));
            return Math.max(0, position - before);
        }

        @Override
        public synchronized void close() {
            if (module == null) return;
            lib.openmpt_module_destroy(module);
            module = null;
            buffer = null;
        }
    }
    // --8<-- [end:source]

    /** The library, loaded once; null if it is not installed. */
    private static final class Loaded {
        static final LibOpenMpt LIB;
        /** libopenmpt's own "say nothing" log function: without it, warnings about odd files go to stderr. */
        static final Pointer SILENT_LOG;

        static {
            LibOpenMpt lib = null;
            Pointer silent = null;
            for (String name : new String[]{"libopenmpt.so.0", "openmpt", "libopenmpt"}) {
                try {
                    lib = Native.load(name, LibOpenMpt.class);
                    silent = NativeLibrary.getInstance(name).getFunction("openmpt_log_func_silent");
                    break;
                } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                    LOG.debug("Cannot load {}: {}", name, e.getMessage());
                }
            }
            if (lib == null) LOG.info("libopenmpt is not installed: tracker modules (MOD, XM, S3M, IT) cannot be played");
            LIB = lib;
            SILENT_LOG = silent;
        }
    }
}

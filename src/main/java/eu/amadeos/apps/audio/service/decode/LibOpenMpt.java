package eu.amadeos.apps.audio.service.decode;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

// --8<-- [start:functions]
/**
 * The parts of libopenmpt (libopenmpt.so.0, Debian {@code libopenmpt0t64}) amade-audio uses, mapped with
 * JNA. libopenmpt plays tracker modules: MOD, XM, S3M, IT and many older formats.
 */
interface LibOpenMpt extends Library {
    /**
     * Loads a module from memory. {@code logfunc} and {@code errfunc} may be null; on failure the result is
     * null and {@code errorMessage} holds a string to free with {@link #openmpt_free_string}.
     */
    Pointer openmpt_module_create_from_memory2(byte[] data, SizeT size, Pointer logfunc, Pointer loguser,
                                               Pointer errfunc, Pointer erruser, IntByReference error,
                                               PointerByReference errorMessage, Pointer ctls);

    void openmpt_module_destroy(Pointer module);

    /** Renders up to {@code count} stereo frames of 16-bit samples; returns 0 at the end of the song. */
    SizeT openmpt_module_read_interleaved_stereo(Pointer module, int sampleRate, SizeT count, Pointer buffer);

    double openmpt_module_get_duration_seconds(Pointer module);

    /** Jumps to a time in the song; returns the time it really jumped to. */
    double openmpt_module_set_position_seconds(Pointer module, double seconds);

    /** Keys such as {@code title}, {@code artist}, {@code type_long}; free the result with openmpt_free_string. */
    Pointer openmpt_module_get_metadata(Pointer module, String key);

    void openmpt_free_string(Pointer string);
}
// --8<-- [end:functions]

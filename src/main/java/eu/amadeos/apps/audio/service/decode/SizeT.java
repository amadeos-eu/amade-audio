package eu.amadeos.apps.audio.service.decode;

import com.sun.jna.IntegerType;
import com.sun.jna.Native;

/** C {@code size_t} for JNA: 64 bits on 64-bit Linux and Windows alike (unlike {@code long} on Windows). */
public final class SizeT extends IntegerType {
    public SizeT() {
        this(0);
    }

    public SizeT(long value) {
        super(Native.SIZE_T_SIZE, value, true);
    }
}

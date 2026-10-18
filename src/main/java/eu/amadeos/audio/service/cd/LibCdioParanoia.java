package eu.amadeos.audio.service.cd;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** libcdio-paranoia (error-correcting audio reads), mapped with JNA. */
interface LibCdioParanoia extends Library {
    /** Full paranoia except "never skip": like cdparanoia's default, gives up on hopeless sectors. */
    int MODE_DEFAULT = 0xFF & ~0x20;
    int SEEK_SET = 0;

    Pointer cdio_paranoia_init(Pointer drive);

    void cdio_paranoia_free(Pointer paranoia);

    void cdio_paranoia_modeset(Pointer paranoia, int mode);

    int cdio_paranoia_seek(Pointer paranoia, int sector, int whence);

    /** One sector of 1176 native-endian 16-bit samples; the callback may be null. */
    Pointer cdio_paranoia_read(Pointer paranoia, Pointer callback);

    /** libcdio-cdda, the drive layer paranoia reads through. */
    interface Cdda extends Library {
        int MESSAGE_FORGETIT = 0;

        Pointer cdio_cddap_identify(String device, int messageDestination, Pointer messages);

        int cdio_cddap_open(Pointer drive);

        int cdio_cddap_close(Pointer drive);
    }
}

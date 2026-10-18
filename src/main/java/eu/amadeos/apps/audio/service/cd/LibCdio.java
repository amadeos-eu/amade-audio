package eu.amadeos.apps.audio.service.cd;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** The parts of libcdio (libcdio.so.19) amade-audio uses, mapped with JNA. */
interface LibCdio extends Library {
    int DRIVER_UNKNOWN = 0;
    byte LEADOUT_TRACK = (byte) 0xAA;
    byte INVALID_TRACK = (byte) 0xFF;
    int TRACK_FORMAT_AUDIO = 0;
    int CDTEXT_FIELD_TITLE = 0;
    int CDTEXT_FIELD_PERFORMER = 1;

    Pointer cdio_open(String device, int driverId);

    void cdio_destroy(Pointer cdio);

    byte cdio_get_first_track_num(Pointer cdio);

    byte cdio_get_num_tracks(Pointer cdio);

    int cdio_get_track_lba(Pointer cdio, byte track);

    int cdio_get_track_format(Pointer cdio, byte track);

    Pointer cdio_get_cdtext(Pointer cdio);

    String cdtext_get_const(Pointer cdtext, int field, byte track);

    /** Reads raw 2352-byte audio sectors; returns 0 on success. */
    int cdio_read_audio_sectors(Pointer cdio, Pointer buffer, int lsn, int sectors);

    int cdio_eject_media_drive(String device);
}

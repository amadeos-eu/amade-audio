package eu.amadeos.apps.audio.service.cd;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/** The parts of kernel32.dll the Windows CD drive uses, mapped with JNA. */
interface Kernel32 extends StdCallLibrary {
    int GENERIC_READ = 0x80000000;
    int FILE_SHARE_READ = 0x1;
    int FILE_SHARE_WRITE = 0x2;
    int OPEN_EXISTING = 3;
    int DRIVE_CDROM = 5;

    /** CTL_CODE(FILE_DEVICE_CD_ROM, 0x15, METHOD_BUFFERED, FILE_READ_ACCESS) */
    int IOCTL_CDROM_READ_TOC_EX = 0x00024054;
    /** CTL_CODE(FILE_DEVICE_CD_ROM, 0x0F, METHOD_OUT_DIRECT, FILE_READ_ACCESS) */
    int IOCTL_CDROM_RAW_READ = 0x0002403E;
    /** CTL_CODE(IOCTL_STORAGE_BASE, 0x200, METHOD_BUFFERED, FILE_ANY_ACCESS); returns the media change count */
    int IOCTL_STORAGE_CHECK_VERIFY2 = 0x002D0800;
    /** CTL_CODE(IOCTL_STORAGE_BASE, 0x202, METHOD_BUFFERED, FILE_READ_ACCESS) */
    int IOCTL_STORAGE_EJECT_MEDIA = 0x002D4808;

    /** Bit mask of the existing drive letters, bit 0 = A:. */
    int GetLogicalDrives();

    int GetDriveTypeW(WString rootPath);

    /** Returns INVALID_HANDLE_VALUE (-1) on failure. */
    Pointer CreateFileW(WString fileName, int desiredAccess, int shareMode, Pointer securityAttributes,
                        int creationDisposition, int flagsAndAttributes, Pointer templateFile);

    boolean DeviceIoControl(Pointer device, int controlCode, Pointer inBuffer, int inBufferSize, Pointer outBuffer,
                            int outBufferSize, IntByReference bytesReturned, Pointer overlapped);

    boolean CloseHandle(Pointer handle);
}

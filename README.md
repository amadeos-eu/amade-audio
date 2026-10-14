# amade-audio

**amade-audio** is the Audio Station of the [amadeOS](https://github.com/datazuul/amadeos) desktop: a
rack-style player for audio CDs, music files and internet radio in Java Swing, inspired by the
hardware-like players of the 1990s and drawn in the colours of the current amadeOS desktop theme.

[![License](https://img.shields.io/badge/license-EUPL--1.2-blue)](LICENSE) ![Java](https://img.shields.io/badge/Java-21-orange) ![Debian](https://img.shields.io/badge/Debian-package-red)

> Status: early development (0.1.0).

![Audio Station in the CDE Desert theme](docs/screenshot-desert.png)

## The racks

The window has no frame of its own: the top rack is the title bar. Drag it to move the window; its
buttons show or hide the racks, and the LED in a button marks the active device.

| Rack | What it does |
|------|--------------|
| **Mixer** | A fader per device (CD, Player, Web), microphone level, output device (speakers, headphones, HDMI, ...), bass and treble, and the master volume of PipeWire. Mute silences all devices. |
| **CD** | Plays audio CDs through libcdio, with error correction by libcdio-paranoia when it is installed. Titles from CD-Text or [MusicBrainz](https://musicbrainz.org), cover from the [Cover Art Archive](https://coverartarchive.org). |
| **Player** | Plays MP3, Ogg Vorbis, AAC, FLAC, WAV/AIFF and MIDI files; position slider, 16-band spectrum. Files and folders can be dropped onto the playlist. |
| **Web Radio** | Internet radio as in [dz-radio](https://github.com/datazuul/dz-radio): favourites, search, genres and countries from [radio-browser.info](https://www.radio-browser.info), stream titles, level meters, and recording. *Stations...* opens the station list with the favourites. |

Below the racks, the **playlist panel** shows the list of the active device: the player's playlist,
the CD's track list or the stations. Next to the list is a large cover (CD cover, the image embedded
in a file or a `folder.jpg`, or the station logo). When no device is active the panel closes and the
window gets smaller.

Pressing Play on one device stops the others.

### Colours

Rack fronts, bevels, rack frame, lists and the logo use the colours of the desktop theme chosen in the
Style Manager and change with it immediately. The LCDs stay black; their glow follows the theme
(amber in *CDE Desert*, blue in *CDE Brocade* and *Dark Slate*, green otherwise) or a colour chosen
in *Options → Display*.

![Audio Station in the Dark Slate theme](docs/screenshot-dark-slate.png)

### CD titles and covers

1. libcdio reads the table of contents; from it amade-audio computes the MusicBrainz disc ID.
2. Titles come, in this order, from the local cache, CD-Text on the disc, or MusicBrainz (by disc ID,
   then a fuzzy lookup by table of contents). If several releases match, a dialog asks which one.
3. *Edit Titles...* names the tracks by hand; *Look up (MusicBrainz)* asks again.
4. The cover comes from the Cover Art Archive (release, then release group).

Titles and covers are cached in `~/.cache/amadeos/audio/discs/<disc-id>/`, so a known CD needs no
network. MusicBrainz is asked at most once per second with an amade-audio User-Agent.

### Recording internet radio

Press **Record** (●) while a station plays; press it again to stop. Right-click it for the options.

- The stream is saved as it arrives, without re-encoding: MP3 as `.mp3`, AAC as `.aac`, Ogg Vorbis
  as `.ogg`.
- Files go to `~/Music/Radio/<station>/`, named after the pattern `{date} {time} {artist} - {title}`.
  A file being written ends in `.part` until it is complete.
- *Start a new file when the title changes* splits MP3 and AAC recordings at the first frame after
  the station announces a new title, and tags MP3 files with artist and title. Stations announce
  titles a few seconds off, so announcements and adverts end up in the files.
- Recording stops after a maximum time (default 2 hours).
- Changing the station or stopping asks before ending a running recording.

Recordings are for private use only; amade-audio says so before the first recording.

### Proxy

Behind a proxy, open the menu (☰) → *Settings...* → category *Network*:

- **Use the system proxy** (default): `https_proxy`, `http_proxy`, `all_proxy` and `no_proxy` from
  the environment (also in upper case), e.g. `http://user:password@proxy.example:3128`.
- **No proxy**: always connect directly.
- **Manual proxy**: host and port for HTTP and HTTPS, optional user name and password, and hosts or
  domains reached directly (`localhost, .intranet.example`). The password is kept in the keyring
  (Secret Service via `secret-tool`), not in `audio.conf`.

*Test Connection* tries the entered settings against radio-browser.info before saving them. The proxy
applies to everything amade-audio fetches: radio streams and playlists, radio-browser.info,
MusicBrainz, the Cover Art Archive and station logos. Basic authentication also works for HTTPS
(the JDK switches it off for tunnels by default; amade-audio switches it back on).

## Architecture

Java 21, Swing, Guice; same structure as [amade-pad](https://github.com/datazuul/amade-pad) and
[amade-mail](https://github.com/datazuul/amade-mail). Theme, Motif look and start-up come from
[amade-commons](https://github.com/datazuul/amade-commons). Playback is pure Java (Java Sound);
only the CD rack uses native code, libcdio through JNA.

| Package | Content |
|---------|---------|
| `domain` | `SourceId`, `Station`, `PlaylistEntry`, `CdToc`, `CdTrack`, `AlbumInfo`, `Artwork`, `AudioLevels`, `RecordingSettings`, `AudioPreferences`, ... |
| `repository` | `PreferencesRepository` / `PreferencesStore` (`~/.config/amadeos/audio/audio.conf`) |
| `service.engine` | `AudioEngine` with an `AudioChannel` per device: plays a `PcmSource` on a virtual thread, applies fader, mute and tone control (`ToneControl`, biquad shelves), measures levels and spectrum (`LevelAnalyzer`, FFT) |
| `service.decode` | `DecoderFactory`: MP3, Ogg Vorbis, AAC/HE-AAC (SBR rate correction from dz-radio) and FLAC through Java Sound service providers; `MidiRenderer` renders MIDI with Gervill so it goes through the mixer too |
| `service.webradio` | from dz-radio: `IcyInputStream`, `BufferedRadioInputStream`, `PlaylistParser`, stream sniffing; `RadioSession` (download, reconnect, recording tap), `RadioBrowserClient` (mirror failover), `StationRepository` (favourites; takes over dz-radio's `channels.json`) |
| `service.recording` | `StreamRecorder` (split at frame boundaries, `.part` files, maximum duration), `Mp3Tagger` |
| `service.player` | `Playlist` (shuffle, repeat), `M3uPlaylist`, `TagReader` (jaudiotagger), `AudioFiles` |
| `service.cd` | `LibCdioDrive` (libcdio and libcdio-paranoia via JNA), `DiscId`, `CdPcmSource` |
| `service.metadata` | `MusicBrainzClient`, `CdMetadataService`, `ArtworkService`, `MetadataCache` |
| `service.mixer` | `SoundServer`: master, microphone and output through `wpctl` / `pactl` |
| `service.net` | `NetworkProxy`: proxy selector and authenticator for all connections (system, none, manual) |
| `service` | `PlaybackCoordinator` (active device), `Json` |
| `ui.main` | frameless `MainWindow`, `HeaderRack`, `MainPresenter` |
| `ui.rack.mixer`, `.cd`, `.player`, `.webradio` | rack panel, playlist view and presenter per device |
| `ui.playlist` | `PlaylistPanel`, `CoverColumn` |
| `ui.widget` | `SevenSegmentDisplay`, `DotMatrixDisplay` (5×7 font from dz-radio), `Fader`, `LevelMeter`, `SpectrumMeter`, `SeekBar`, `CoverView`, `TransportIcon`, `Led`, `RackPanel`, `RackRail`, `LcdPanel` |

Presenters run on the EDT; drive, decoder and network work runs on virtual threads and returns
through `EdtExecutor`. Audio events (state, levels, end of track) arrive on the audio threads and are
moved to the EDT by the presenters.

## Building and testing

Requirements: Java 21 JDK; `dpkg-deb` for the Debian package.

`amade-commons` is taken from JitPack. If it is checked out next to this repository
(`../amade-commons`), Gradle builds that checkout instead.

```bash
./gradlew test                 # unit tests (run under Xvfb in CI)
./gradlew run                  # start the Audio Station
./gradlew run --args="~/Music/Album"
./gradlew deb                  # build/distributions/amade-audio_0.1.0_all.deb
./gradlew installToAmadeOs     # copy the .deb into ../amadeos/config/packages.chroot/
```

The MIDI renderer uses Gervill's streaming interface, which `java.desktop` does not export; the
launcher, `gradle run`, the tests and the jar manifest add
`--add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED`.

### Runtime packages

The Debian package recommends:

- `libcdio19t64 | libcdio19`, `libcdio-paranoia2t64 | libcdio-paranoia2`, `libcdio-cdda2t64 | libcdio-cdda2` — audio CDs
  (without them the CD rack stays empty; without paranoia CDs are read without error correction)
- `fluid-soundfont-gm` — a General MIDI SoundFont; otherwise Gervill's built-in sounds are used
- `pulseaudio-utils` (`pactl`) and WirePlumber (`wpctl`) — master volume, microphone and output selection
- `eject` — fallback for the Eject button

The package installs `amade-audio.desktop` (`Categories=AudioVideo;Audio;Player;`), so the amadeOS
front panel lists it with the multimedia applications.

## Roadmap

- [x] Racks: mixer, CD, file player, web radio; playlist panel following the active device
- [x] CD titles from CD-Text / MusicBrainz, covers from the Cover Art Archive, editable titles
- [x] Recording internet radio, optionally split by title
- [ ] Ripping CDs to FLAC / Ogg
- [ ] Timed recordings
- [ ] Seeking within MP3/OGG without decoding from the start
- [ ] MPRIS (media keys, desktop integration)

## License

amade-audio is free software, licensed under the [European Union Public Licence v. 1.2](LICENSE)
(EUPL-1.2, SPDX identifier `EUPL-1.2`), like amadeOS.

Copyright © 2026 datazuul.com and contributors.

Parts of the web radio (ICY metadata, buffering, playlist parsing, stream sniffing, HE-AAC handling,
the 5×7 LCD font) come from [dz-radio](https://github.com/datazuul/dz-radio).

Third-party libraries keep their licences: Guice (Apache-2.0), JNA (Apache-2.0 or LGPL-2.1),
jaudiotagger (LGPL-2.1), JLayer / MP3SPI / Tritonus / JOrbis / VorbisSPI (LGPL), javasound-aac
(LGPL-2.1, based on JAAD), jFLAC (Apache-2.0), SLF4J (MIT), Logback (EPL-1.0 / LGPL-2.1); at runtime
libcdio and libcdio-paranoia (GPL-3.0-or-later), loaded only when installed.

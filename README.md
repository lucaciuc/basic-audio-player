# Basic Audio Player

A small, offline music player for Android. It lists the music stored on the phone and
plays it in the background, with the notification, lock screen and Bluetooth controls
Android users expect. The UI follows the Google Pixel look: Material You colors from the
wallpaper, the Pixel "squiggly" seek bar and a play button that changes shape.

Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 (ExoPlayer). Android 6.0+.

## Features

- **Library**: every music file MediaStore knows about, with title, artist and duration,
  sorted by title using the phone's language rules. Refreshed whenever the app comes back
  to the foreground.
- **Background playback** through a Media3 `MediaSessionService`: media notification,
  lock screen, Bluetooth and headset buttons. Tapping the notification reopens the app.
- **Well-behaved audio**: takes audio focus (pauses for calls and other apps), pauses
  when headphones are unplugged, gapless between tracks, and lets the audio DSP decode
  (hardware offload) on devices that can do it without breaking gapless playback.
- **Pixel look**:
  - Material You dynamic color from the wallpaper on Android 12+, with a matching
    fallback palette (generated from Pixel's default blue seed) on older versions.
  - Light and dark mode following the system.
  - Google Sans, read from the system on Pixel phones (nothing is bundled).
  - A wavy seek bar that flows while playing and flattens when paused, and a play button
    that morphs from a circle into a rounded square, as in the Pixel media controls.
  - Grouped, rounded list rows as in Android 16 settings, and a themed (monochrome)
    launcher icon.

## Project layout

Everything lives in `app/src/main/java/.../ui/activitys/`:

| File | What it does |
| --- | --- |
| `AudioPlayerService.kt` | Owns ExoPlayer and the `MediaSession`. Media3 does the notification and controls. |
| `AudioViewModel.kt` | Queries MediaStore, connects one `MediaController` to the service and mirrors the player as Compose state. |
| `MainActivity.kt` | The activity, the permission flow and the track list. |
| `NowPlaying.kt` | The docked player card: artwork, wavy seek bar, play/pause and skip buttons. |
| `Theme.kt` | Material You colors, fallback palette and Google Sans typography. |

```mermaid
graph TD
    MS["MediaStore"] -->|"query on Dispatchers.IO"| VM["AudioViewModel"]
    VM -->|"Compose state"| UI["Compose UI"]
    UI -->|"play, pause, seek"| VM
    VM -->|"MediaController"| S["AudioPlayerService"]
    S -->|"player events"| VM
    S --> P["ExoPlayer"]
    S --> N["Notification, lock screen, Bluetooth"]
```

## Where the efficiency comes from

The app is small, so most of the work is in not doing things.

**The playing position never recomposes the UI.** While music plays, the seek bar needs a
new position every frame. The position is read inside the seek bar's `Canvas` draw
lambda, so a change only redraws that one canvas; Compose skips composition and layout.
The elapsed-time label sits in its own small composable behind `derivedStateOf`, so it
recomposes once per second. The position comes from `MediaController.currentPosition`,
which is extrapolated locally from the last player update, so reading it every frame
costs no IPC. The frame loop stops by itself when the app is in the background, because
Compose pauses its frame clock when the activity is stopped.

**One controller, connected once.** The `MediaController` lives in the ViewModel, so it
survives rotation and is released in `onCleared`. Player state arrives through a single
`onEvents` listener that copies it into Compose state; writing an unchanged value
recomposes nothing.

**Nothing is loaded until you press play.** Opening the app does not prepare the player,
so no decoder is created and no notification appears until a song is chosen. The full
playlist is only sent to the service when it differs from what the player already holds.

**Artwork is decoded once, small.** ExoPlayer already extracts the embedded cover while
reading the file. The ViewModel decodes those bytes off the main thread at the smallest
power-of-two scale that still fits the 56dp thumbnail, instead of re-reading the file.

**The audio path stays simple.** No custom buffering (ExoPlayer's defaults suit local
files) and no audio effects attached to the session, which would stop many devices from
offloading decoding to the DSP. A local wake lock keeps playback going with the screen off.

**The build strips what is unused.** R8 runs with optimizations (`proguard-android-optimize.txt`)
plus resource shrinking. There is no AppCompat, no extended icon set (the six icons are
vector drawables) and no Palette library. A wildcard baseline profile marks all app code
for ahead-of-time compilation, alongside the profiles Compose and Media3 ship. The release
APK is about 2.0 MB.

## Building

Requires JDK 17.

```
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Unit tests run
with `./gradlew testReleaseUnitTest`.

### CI and signing

GitHub Actions builds every pull request, and every push to `master` publishes the APK
as the `latest` release.

By default CI signs with a throwaway debug key that changes on every run, so Android
refuses to install a new APK over the previous one. To sign every build with the same
key, create one once:

```
keytool -genkeypair -keystore release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000
```

and add these repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | output of `base64 -w0 release.jks` |
| `SIGNING_STORE_PASSWORD` | the keystore password |
| `SIGNING_KEY_ALIAS` | `release` |
| `SIGNING_KEY_PASSWORD` | the key password |

Keep `release.jks` out of the repository. The first APK signed with the new key needs
the old app to be uninstalled once; after that, updates install normally.

## Left out on purpose

- **Equalizer and bass boost.** The earlier version attached both at a fixed setting
  with no way to change them. They colored the sound and, on many devices, prevented
  hardware offload.
- **Colors from album art.** The app uses the wallpaper colors instead, like other Pixel
  apps. The artwork still shows in the player card.
- **A native (C++/Oboe) audio engine.** Exclusive, low-latency audio matters for
  instruments and games, not for playing files. ExoPlayer already plays through the
  platform's offload path where the hardware has one.

## License

MIT License

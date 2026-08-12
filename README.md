# Basic Audio Player

Just a blazing fast, stupidly simple music player for Android. No bloated UI frameworks, no tracking, just raw performance. 

## What it does
- Scans your phone for audio files (MP3s, FLACs, whatever).
- Plays them in the background (even if you swipe it away, wait no, it actually closes properly if you swipe it away!).
- Hooks right into your Android system notifications so it shows up natively in your quick settings.

## How to use
Grab the `BasicAudioPlayer-Latest.apk` from the [Releases](../../releases) tab. It automatically compiles a fresh APK on every single push. Install it, give it storage permission, and you're good.

If you want to build it yourself, just clone it and hit Run in Android Studio.

## Tech Stack
- **Pure Android XML Views** (We ripped out Jetpack Compose because it was lagging the playlist).
- **RecyclerView** for 60fps buttery smooth scrolling no matter how many files you have.
- **Jetpack Media3 / ExoPlayer** for the actual audio engine.

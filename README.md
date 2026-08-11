# Basic Audio Player

A simple, lightweight Android audio player built with Jetpack Compose and Media3 (ExoPlayer). It plays local audio files seamlessly in the background.

## What it does
- Scans your device for local audio files (MP3s, FLACs, whatever your phone supports)
- Plays music in the background even when you minimize or close the app
- Shows a clean, modern Material 3 UI with a progress slider and standard playback controls
- Integrates directly with Android's system media notifications (so it shows up in your quick settings panel)

## How to use
Just head over to the [Releases](../../releases) tab to grab the latest APK. Install it on your Android phone, grant the storage permission, and you're good to go. 

If you want to build it yourself from source:
1. Clone the repo
2. Open it in Android Studio
3. Hit Run

## Tech Stack
- Jetpack Compose (UI)
- Jetpack Media3 / ExoPlayer (Audio engine)
- MediaSessionService (Background playback)

*Built as a quick proof-of-concept for Media3 background audio.*

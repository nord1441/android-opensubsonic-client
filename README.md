# SubTune

A minimal Android music streaming client for Subsonic/OpenSubsonic compatible servers (Navidrome, Airsonic, etc.).

## Features

- **Streaming & Downloads** - Stream music directly or download for offline playback
- **Browse** - Albums, Artists, Genres, Playlists
- **Playback Modes** - Sequential, Shuffle, Repeat One, Repeat All
- **Media Integration** - Background playback with media notification controls
- **Downloads** - Downloaded music saved to Music/SubTune folder, accessible by other apps
- **Dark Theme** - Automatic dark/light theme based on system settings

## Design

Minimal UI inspired by Teenage Engineering and Nothing. Black & white base with red accent color. Uses the Doto dot font from Google Fonts.

## Tech Stack

- Kotlin + Jetpack Compose
- Media3 / ExoPlayer for audio playback
- Retrofit + OkHttp for networking
- Room for local database
- Hilt for dependency injection
- Coil for image loading
- Material 3

## Building

```
./gradlew assembleDebug
```

## Server Compatibility

Compatible with any server implementing the Subsonic API v1.16.1+:
- Navidrome
- Airsonic
- Gonic
- LMS
- Ampache (with Subsonic API enabled)

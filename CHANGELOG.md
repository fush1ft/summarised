# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added (M2: transcription)
- On-device Czech transcription with Parakeet TDT 0.6B v3 (whisper.cpp), downloaded
  once on request (668 MB, resumable).
- Live transcription while recording: audio is cut at the quietest moment every
  20–30 s, so words are never split; the latest line shows on the record card.
- Transcript tab in the recording detail with bookmarks shown where they fall;
  tapping a line plays it.
- Transcribing older recordings afterwards, from their Opus or WAV chunks.
- New `:core:inference` module: the native core (Parakeet/Whisper and llama.cpp
  on one ggml), with a persistent Parakeet context for live use.

### Added (M1: capture)
- Hold the power button to start recording and again to add a bookmark, also on
  the lock screen, via the digital-assistant role; haptic confirmation for start,
  bookmark, undo, stop and errors.
- Recording in a foreground service as ~5-minute Opus chunks (WAV fallback),
  saved every 30 s; a recording cut off by a crash is kept and marked as
  interrupted.
- Notification with Bookmark, Undo and Stop, usable on the lock screen.
- Recordings list, detail with bookmark playback (10 s lead-in), rename, delete.
- First-run setup: permissions, assistant role, power-button gesture, summary
  mode (Easy with an optional free Groq key, or Advanced) and vibration strength.

### Changed
- Minimum Android version is now 13 (API 33).
- Database schema rebuilt for recordings, audio chunks, bookmarks and segments.
- Hilt and Navigation removed from the app: fewer moving parts for three screens.

### Added
- Multi-module project scaffold: `:app`, `:core:designsystem`, `:core:database`.
- Material 3 theme with dynamic color and an indigo/teal fallback palette.
- Room schema for recordings, segments, speakers, summaries and chat.
- Compose navigation shell with record, library and settings destinations.
- Adaptive launcher icon and day/night splash themes.
- Backup rules excluding all app data from cloud backup and device transfer.
- CI running assemble, lint and unit tests on every pull request.

Nothing is user-facing yet: recording lands in the next milestone.

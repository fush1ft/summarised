# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Recording works.** Capture at 16 kHz mono PCM-16 straight to WAV, which is the
  format the transcription and diarization models want, so no resampling is needed
  later.
- Foreground service so recording continues with the screen off, with a persistent
  notification carrying a pause/resume action.
- Record screen: live waveform, monospace timer, pause, resume and discard, plus a
  microphone permission flow that explains itself and links to system settings
  after a refusal.
- Library screen: saved recordings with playback, rename and delete.
- Recordings are stored in app-private storage and rows are saved as QUEUED,
  awaiting the transcription stage.

### Fixed
- Dependency review used `BSL-1.1` for the Business Source Licence; the SPDX id is
  `BUSL-1.1`. One bad identifier failed the whole action, so the licence gate was
  not checking anything.

### Earlier
- Multi-module project scaffold: `:app`, `:core:designsystem`, `:core:database`.
- Material 3 theme with dynamic color and an indigo/teal fallback palette.
- Room schema for recordings, segments, speakers, summaries and chat.
- Compose navigation shell with record, library and settings destinations.
- Adaptive launcher icon and day/night splash themes.
- Backup rules excluding all app data from cloud backup and device transfer.
- CI running assemble, lint and unit tests on every pull request.

Transcription, speaker detection and summaries are still to come.

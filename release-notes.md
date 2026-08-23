Early alpha, built by CI from commit e749b75.

### What changed


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

### Before you install

This is a **debug build signed with the standard Android debug key**,
because the project has no release keystore yet. The signature proves
nothing about who built it, and the app is debuggable. Fine for trying
out; not a secure release, and not an upgrade path for later signed
builds - you will have to uninstall it first.

It installs alongside any future release build, since the debug
variant uses the application ID suffix `.debug`.

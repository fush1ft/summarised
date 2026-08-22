# Summarised — Implementation Plan

## Context

`fush1ft/summarised` is currently an empty repo (AGPL-3.0 `LICENSE`, a one-line `README.md`, an Android `.gitignore`). The goal is a **100% free and open-source Android app** that records a meeting, transcribes it, labels who said what, summarises it, and lets the user ask questions about it — with **no backend, no API keys, no registration**, and a polished look because the project will be public.

**One finding that shapes the whole design:** `ox-alpha` is an anonymous *stealth preview* model that appeared on OpenRouter on 2026-08-20. It is free only for a ~1-week preview window, has no named owner, and **still requires an OpenRouter account and API key**. It cannot carry a zero-config app — it will likely be gone or paid before v1 ships. So the plan makes **on-device inference the default and only required path**, and treats OpenRouter (ox-alpha included, discovered at runtime rather than hardcoded) as an optional bring-your-own-key power-user setting. No Azure backend is needed; the app stays fully local.

**Decisions locked with the user:** on-device default + optional BYOK · distribution via F-Droid + GitHub Releases · multilingual Whisper for STT · minimum target 6 GB RAM / Android 10 (API 29).

The whole processing chain — VAD, diarization, ASR, summarisation, Q&A — runs offline on the phone. Nothing leaves the device unless the user explicitly pastes an API key.

---

## Architecture

```
record ──► 16 kHz mono WAV ──► diarize ──► ASR per speaker turn ──► transcript
             (AudioRecord)     (sherpa-onnx)   (sherpa-onnx Whisper)      │
                                                                          ▼
                                                        summary + chat (llama.cpp)
```

Diarization runs **before** ASR: segmenting first, then transcribing each speaker turn, gives clean speaker attribution without aligning two independent timelines afterwards.

### Gradle modules

| Module | Responsibility |
|---|---|
| `:app` | Compose navigation host, DI wiring (Hilt), app entry |
| `:core:designsystem` | Material 3 Expressive theme, typography, colors, shared components |
| `:core:database` | Room entities/DAOs for recordings, segments, summaries, chat |
| `:core:audio` | `AudioRecord` capture, WAV writer, `RecordingService` (foreground) |
| `:core:stt` | sherpa-onnx wrapper: `Diarizer` + `Transcriber` |
| `:core:llm` | `LlmEngine` interface, `LocalLlamaEngine` (JNI), `OpenRouterEngine` |
| `:core:modelmanager` | Model catalog, consented downloads, SHA-256 verification, storage |
| `:feature:record` | Record screen, live waveform, timer |
| `:feature:library` | Recording list, search, rename, delete |
| `:feature:detail` | Transcript, summary, speaker naming, chat tab |
| `:feature:settings` | Model picker, BYOK, storage, about/licenses |

Kotlin + Compose + Coroutines/Flow + Hilt + Room + DataStore + WorkManager. **No Firebase, no GMS, no proprietary SDK** — an F-Droid inclusion requirement and a prerequisite for the "100% FOSS" claim.

---

## Core technical choices

### Audio capture (`:core:audio`)
- `AudioRecord` with `MediaRecorder.AudioSource.VOICE_RECOGNITION`, 16 kHz mono PCM-16 — the exact format both sherpa-onnx models expect, so no resampling stage.
- Streams straight to a WAV file; a foreground service with `foregroundServiceType="microphone"` keeps recording alive with the screen off (Android 9+ blocks background mic without one). Pause/resume, persistent notification, amplitude emitted as a `Flow<Float>` for the waveform.
- **Honest scope note:** Android blocks third-party recording of phone calls, and most VoIP apps opt out of `AudioPlaybackCapture`. v1 targets **in-room meetings via the mic**. Capturing playback audio (via `MediaProjection` consent) is a post-v1 experiment, documented as best-effort.

### Speech-to-text + diarization (`:core:stt`)
Both come from **one dependency: sherpa-onnx** (Apache-2.0, official Android AAR + Kotlin API, fully offline). This is the single most valuable reuse in the plan — it removes an entire second native stack.

- **Diarization:** `OfflineSpeakerDiarization` = `pyannote/segmentation-3.0` (ONNX, ~6 MB) + a 3D-Speaker ERes2NetV2 embedding model (~25 MB), clustered to produce `(startSec, endSec, speakerId)`. Supports either a known speaker count or a clustering threshold — expose both (auto by default, manual override in settings).
- **ASR:** sherpa-onnx offline Whisper recognizer, int8-quantised. `whisper-base` multilingual (~150 MB) as the default; `whisper-small` (~500 MB) offered as a "better accuracy" download; `whisper-tiny` as the low-end fallback.
- Adjacent same-speaker segments are merged, then split to fit Whisper's 30 s window before recognition.
- Runs as a `WorkManager` chain (diarize → transcribe → summarise) with a foreground notification and per-stage progress, so it survives the user leaving the app. Every stage is resumable and writes partial results to Room.

### On-device LLM (`:core:llm`)
- **llama.cpp** built from source via CMake/NDK, packaged as a `:core:llm` native library with a thin JNI + Kotlin wrapper — mirroring the structure of llama.cpp's own `examples/llama.android` (`lib` module + `build.gradle.kts` + CMake). Pin llama.cpp as a git submodule at a known-good tag.
- **Default model: Qwen3-1.7B, Q4_K_M GGUF (~1.1 GB), Apache-2.0, 32k context.** Apache-2.0 matters here: it keeps the "100% open source" claim defensible in a way Google's custom Gemma terms historically did not. Qwen3-4B offered as an optional download on 8 GB+ devices.
- `LlmEngine` interface with two implementations (`LocalLlamaEngine`, `OpenRouterEngine`) so the UI never knows which backend answered.
- **Summarisation:** map-reduce over transcript chunks, then a final pass producing structured output — title, TL;DR, key points, decisions, action items — constrained with a **GBNF grammar** so JSON parsing is reliable rather than hopeful.
- **Chat Q&A:** if the transcript fits in ~60% of context (a 1-hour meeting is roughly 12k tokens, so it usually does), stuff the whole thing in. Otherwise fall back to **BM25 keyword retrieval** over transcript chunks — no embedding model, no extra download, no vector DB in v1.

### Optional BYOK (off by default)
- Settings screen accepts an OpenRouter key stored in `EncryptedSharedPreferences`. The model list is **fetched from `/api/v1/models` at runtime and filtered to free models**, so `ox-alpha` appears if it still exists and nothing breaks when it disappears. Never hardcode it.
- Enabling it requires passing a clear dialog stating the transcript leaves the device; a persistent badge shows on any screen where a cloud model is active.

### Model delivery (F-Droid-compatible)
Models are **not bundled** (they'd blow past size limits) and **never fetched silently** — F-Droid's inclusion policy forbids downloading binaries without explicit user consent. First-run onboarding presents a screen naming each model, its size, its license, and its source URL, with an explicit download button. Downloads are resumable, run in `WorkManager`, and are **SHA-256 verified** against a checksum table in the repo. A device-capability check (RAM, ABI) recommends the right tier and warns instead of blocking.

---

## Data model (Room)

```
Recording(id, title, filePath, durationMs, createdAt, status, language, modelUsed)
Segment(id, recordingId, startMs, endMs, speakerId, text)
Speaker(id, recordingId, label, displayName, colorIndex)   // user-renamable
Summary(id, recordingId, tldr, keyPointsJson, decisionsJson, actionItemsJson, generatedAt)
ChatMessage(id, recordingId, role, content, createdAt)
```

`status` drives the UI state machine: `RECORDING → QUEUED → DIARIZING → TRANSCRIBING → SUMMARISING → READY | FAILED`, with a retry-from-failed-stage action.

---

## Design ("make it pretty")

- **Material 3 Expressive**, dynamic color on Android 12+ with a hand-picked fallback palette (deep indigo/teal), full dark mode, large expressive type for the record screen.
- **Record screen:** a breathing concentric ring around the record button, live amplitude waveform, big monospace timer, pause/stop. Deliberately spare.
- **Library:** cards with title, duration, speaker-count chip, relative date, and a status shimmer while processing.
- **Detail:** three tabs — Summary (hero TL;DR card, key points, checkable action items), Transcript (speaker-colored chips, timestamps, tap-to-seek with a mini player, search), Chat (message bubbles with streaming tokens, suggested starter questions).
- **Speakers** get stable colors and are renamable inline ("Speaker 1" → "Priya"), retroactively applied to the whole transcript.
- Meaningful motion: shared-element transitions list→detail, streaming text, animated processing progress.
- **Repo presentation:** adaptive app icon, README with a screenshot row, feature list, badges, and a clear "how the offline pipeline works" diagram; fastlane metadata structure for F-Droid (`metadata/en-US/` with descriptions, screenshots, changelogs).

---

## Milestones

**M0 — Foundations.** Gradle multi-module scaffold, version catalog, Hilt, Compose, design system module, Room skeleton. GitHub Actions CI (assemble + lint + unit tests). `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, issue/PR templates, `CHANGELOG.md`.

**M1 — Record & library.** A genuinely usable recorder: capture, foreground service, waveform, playback, library list, rename/delete, storage management. Ships as v0.1 alpha.

**M2 — Transcription.** sherpa-onnx AAR integration, model catalog + consented downloader + SHA-256 verification, onboarding flow, Whisper transcription pipeline in WorkManager, transcript UI with tap-to-seek.

**M3 — Diarization.** Speaker segmentation + clustering, ASR restructured to run per speaker turn, speaker colors and inline renaming, auto/manual speaker-count control.

**M4 — On-device summaries.** llama.cpp submodule + JNI + Kotlin wrapper, Qwen3-1.7B download tier, map-reduce summarisation with GBNF-constrained JSON, summary UI, action items.

**M5 — Chat Q&A.** Streaming chat over the transcript, context stuffing with BM25 fallback, suggested questions, persisted history.

**M6 — Sharing & BYOK.** Export transcript/summary as Markdown, TXT, SRT; share sheet. Optional OpenRouter key with runtime free-model discovery and an explicit privacy gate.

**M7 — Release.** Performance and thermal tuning, accessibility pass (TalkBack, contrast, font scaling), localisation scaffolding, privacy policy, reproducible-build check, F-Droid submission, signed GitHub Release.

Each milestone is a PR onto `claude/summarised-app-plan-js7004` with a working build.

---

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| `ox-alpha` vanishes or starts charging | Never a dependency — on-device is the default; cloud models are discovered at runtime and degrade gracefully to absent |
| Diarization RAM/time on long recordings | Chunk audio into ~10-min windows with cross-window speaker re-clustering; warn above ~90 min |
| Whisper + LLM too slow on mid-range phones | Model tiers by device RAM; run stages sequentially, never concurrently; foreground-service progress so slowness is visible, not mysterious |
| Thermal throttling on long jobs | Process while charging/idle where possible; batch with pauses; surface a "this may take a while" estimate |
| Model weight licenses | **Verify each weight's license before M2 ships** (Whisper MIT, 3D-Speaker Apache-2.0, pyannote segmentation-3.0 needs confirmation) and list them all in an in-app licenses screen |
| Legal exposure from recording | First-run consent notice about local recording-consent laws, non-dismissible recording indicator, no covert-recording affordances |
| APK bloat / F-Droid limits | Models downloaded post-install with consent, never bundled |

**Note on the license:** AGPL-3.0 is already committed and is F-Droid-compatible. Its network-copyleft clause does nothing for an app with no backend — plain GPL-3.0 is the more conventional fit — but this is your call and the plan assumes AGPL-3.0 stays.

---

## Verification

**Per-milestone automated checks**
- `./gradlew assembleDebug lintDebug testDebugUnitTest` green in CI on every PR.
- Unit tests: WAV writer byte-correctness, segment merging/splitting logic, BM25 ranking, GBNF-constrained JSON parsing (including malformed-output fallback), model checksum verification (correct, corrupt, and truncated files).
- Instrumented tests: Room migrations, WorkManager chain resumption after process death, permission-denied paths.

**Pipeline correctness (the part that actually matters)**
- Commit a short two-speaker audio fixture with a hand-written reference transcript. Assert diarization finds exactly 2 speakers, that speaker-turn boundaries land within ~1 s of reference, and that WER stays under a recorded threshold. Run it as an instrumented test on a real device in the M2/M3 gates.
- Log real-time factor (processing time ÷ audio duration) and peak RSS per stage; record them in the README so regressions are visible.

**Manual device matrix** (min one 6 GB and one 8 GB+ device, Android 10 and current)
1. Record a 10-minute two-person conversation with the screen off; confirm the recording survives and the notification stays.
2. Confirm the full chain completes offline **in airplane mode** — this is the single most important end-to-end check.
3. Verify speaker labels are consistent, renaming propagates, and tap-to-seek lands correctly.
4. Confirm the summary produces valid structured output, and ask 3 questions in chat including one whose answer is late in the transcript.
5. Kill the app mid-processing; confirm it resumes rather than restarting from scratch.

**Release gates**
- Fresh install → onboarding → no network call fires before the user taps download (verify with a network-logging proxy or `StrictMode`).
- Full offline run on a device that has never had network access.
- Reproducible-build verification and an F-Droid-policy self-check: no non-free dependencies, no unconsented binary downloads.

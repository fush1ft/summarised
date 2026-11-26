# Summarised

A free and open-source, lightweight voice recorder for Android that summarises
what matters. Recording and speech recognition run **entirely on your device**;
summaries come from a fast free cloud model by default (Groq, or keyless
Pollinations) with an on-device fallback, and one setting keeps everything
offline. No backend, no telemetry.

Record lectures, meetings or field work for hours. Whenever something important
comes up, **hold the power button** — even with the phone locked in your
pocket — and feel a click: that's a bookmark. A local model transcribes the
recording as it goes, summarises a few minutes around each bookmark, and writes
a summary of the whole recording when you stop.

> **Status: early development.** The project scaffold is in place; recording
> lands next. See [`docs/PLAN.md`](docs/PLAN.md) for the full architecture and
> milestone breakdown, and [`CHANGELOG.md`](CHANGELOG.md) for what exists today.

## How it will work

```
hold power ──► start / bookmark (+ haptic, works on the lock screen)

mic ──┬──► Opus audio (kept, ~10 MB/hour)
      └──► VAD ──► Whisper (live) ──► transcript
                                         │
              bookmark ±2 min ──► LLM ──► title + summary
              every 10 min    ──► LLM ──► section summary ──► final summary on stop
```

| Stage | Runs on | Powered by |
|---|---|---|
| Trigger | device | Android digital-assistant role (power-button hold) |
| Recording | device | `AudioRecord` + Opus, foreground service |
| Transcription | device | whisper.cpp (Parakeet TDT v3, Czech first) |
| Bookmark & recording summaries | cloud by default, device as fallback | Groq / Pollinations, then llama.cpp |

Audio never leaves the phone. In the default mode the transcript text is sent
to the summary provider; *on-device only* sends nothing.

## Building

Requires JDK 17. The Gradle wrapper handles everything else; Android Studio will
offer to download the SDK.

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint (warnings are errors)
```

Targets `minSdk` 29 (Android 10) and `compileSdk` 37. The on-device models are
tuned for 8 GB of RAM (reference device: Pixel 10a).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). The short version: everything must work
offline, no proprietary dependencies, and nothing is downloaded without the user
asking for it.

## Licence

[AGPL-3.0](LICENSE). Model weights are downloaded separately, with your consent,
under their own free licences.

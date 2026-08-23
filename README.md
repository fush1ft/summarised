# Summarised

A free and open-source meeting recorder, transcriber and summariser for Android.

Record a conversation, get a speaker-labelled transcript, a structured summary,
and a chat you can ask questions about what was said — **entirely on your device**.
No account, no API keys, no backend, no telemetry.

> **Status: early development.** Recording and the library work today.
> Transcription, speaker detection and summaries are still to come. See
> [`docs/PLAN.md`](docs/PLAN.md) for the architecture and milestone breakdown, and
> [`CHANGELOG.md`](CHANGELOG.md) for what exists right now.

## How it will work

```
record ──► 16 kHz mono WAV ──► diarize ──► ASR per speaker turn ──► transcript
             (AudioRecord)     (sherpa-onnx)   (Whisper, offline)        │
                                                                         ▼
                                                       summary + chat (llama.cpp)
```

| Stage | Runs on | Powered by |
|---|---|---|
| Recording | device | `AudioRecord` + foreground service |
| Speaker diarization | device | sherpa-onnx (pyannote + 3D-Speaker) |
| Transcription | device | sherpa-onnx (Whisper, multilingual) |
| Summary & Q&A | device | llama.cpp (Qwen3-1.7B, Apache-2.0) |

Everything is offline by default. An optional bring-your-own-key setting can
route summaries through a cloud model instead, but it is off unless you turn it
on and say so explicitly.

## Building

Requires JDK 17. The Gradle wrapper handles everything else; Android Studio will
offer to download the SDK.

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint (warnings are errors)
```

Targets `minSdk` 29 (Android 10) and `compileSdk` 37. The on-device models want
6 GB of RAM or more to be comfortable.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). The short version: everything must work
offline, no proprietary dependencies, and nothing is downloaded without the user
asking for it.

## Licence

[AGPL-3.0](LICENSE). Model weights are downloaded separately, with your consent,
under their own free licences.

# Summarised

A free and open-source meeting recorder, transcriber and summariser for Android.

Record a conversation, get a speaker-labelled transcript, a structured summary,
and a chat you can ask questions about what was said — **entirely on your device**.
No account, no API keys, no backend, no telemetry.

> **Status: planning.** No code yet. See [`docs/PLAN.md`](docs/PLAN.md) for the
> full architecture and milestone breakdown.

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

## Licence

[AGPL-3.0](LICENSE). Model weights are downloaded separately, with your consent,
under their own free licences.

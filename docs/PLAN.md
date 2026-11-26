# Summarised — Implementation Plan (v2: lightweight capture)

## Context

The first plan aimed at a full meeting suite: speaker diarization, per-speaker
transcripts, a chat tab and an optional cloud model. This version narrows the
app to one job and does it well:

> **Record for hours, drop bookmarks blind by holding the power button, and get
> each bookmark — and the whole recording — summarised by a model running on
> the phone.**

Recording and speech recognition always stay on the phone. Summaries use a fast cloud model by default and fall back to an on-device one; a setting keeps everything offline. No account of our own, no backend, no telemetry.

**Decisions locked with the user**

| Topic | Decision |
|---|---|
| Trigger | The app registers as Android's **default digital assistant**. Holding the power button starts a recording; holding it again while recording adds a bookmark. Works on the lock screen, confirmed by haptics. |
| Bookmark processing | Summarise the transcript **±2 minutes around the bookmark**: title, short summary, key points. |
| Language | **Czech** first. |
| Target device | **Pixel 10a** (8 GB RAM, arm64). Other 8 GB+ phones are a bonus, not a goal. |
| When to process | **Live**: transcription and bookmark summaries run during recording, so stopping leaves only a few minutes of work. |
| Scope kept from v1 | **Whole-recording summary.** Dropped: diarization, chat Q&A. |
| Summaries (decided after M0) | **Easy mode (default):** Groq (if the user adds a free key) → Pollinations GPT-OSS 20B (no key) → local model. **Advanced mode:** the user picks providers, models and order, including *on-device only*. Audio and speech recognition never leave the phone; in Easy mode the transcript text does. |
| Audio | Kept, compressed as **Opus** (~7–11 MB per hour), so any bookmark can be played back. |
| Use cases | **Lectures**, **meetings/conversations**, **field work** (hands busy, bookmarks blind). Each gets its own summary template. |

---

## How it works

```
power-button hold ──► assistant session ──► start / bookmark (+ haptic)
                                                     │
            ┌────────────────────────────────────────┘
            ▼
 AudioRecord 16 kHz mono ──┬──► Opus encoder ──► 5-min .ogg chunks (kept)
                           │
                           └──► Silero VAD ──► utterances ≤ 28 s ──► Whisper (cs)
                                                                        │
                                                             timestamped segments
                                                                        │
                  ┌─────────────────────────────┬───────────────────────┘
                  ▼                             ▼
     bookmark @t: segments [t-2m, t+2m]   every ~10 min: section summary
          └──► LLM ──► title + summary          └──► (map step)
                                                       │
                               on stop ────────────────┴──► final summary (reduce)
```

Two native engines, both open source and fully offline:

| Stage | Powered by |
|---|---|
| Voice activity detection + speech recognition | sherpa-onnx (Silero VAD + Whisper, int8) |
| Bookmark and recording summaries | llama.cpp (GGUF, Q4_K_M) |

---

## 1. Power-button trigger (the core feature)

Android never hands the power button to apps. It *does* hand it to the
**default digital assistant**: on Pixel, *Settings → System → Gestures → Press
and hold power button → Digital assistant* launches whichever app holds the
assistant role. That is how Grok and Perplexity hook the same gesture, and it
works from the lock screen.

**Implementation**

- `CaptureVoiceInteractionService : VoiceInteractionService` declared with
  `BIND_VOICE_INTERACTION` and `res/xml/voice_interaction.xml`:
  `sessionService`, `recognitionService`, `supportsAssist="true"`,
  `supportsLaunchVoiceAssistFromKeyguard="true"`, `settingsActivity`.
- A stub `RecognitionService`: the system's assistant picker only lists apps
  that declare one.
- `CaptureSessionService` → `CaptureSession : VoiceInteractionSession`. On
  `onShow()` it asks `RecordingController` for the current state:
  - **idle** → start `RecordingService` (foreground, `microphone` type),
    haptic *start*, card "● Nahrávám".
  - **recording** → add a bookmark at `now - 1.5 s` (the hold itself takes
    time), haptic *bookmark*, card "🔖 Záložka 5 · 01:23:45" with **Zpět**
    (undo) and **Stop** buttons.
  - The card is a small bottom sheet that hides itself after ~2.5 s and works
    over the keyguard, so you never have to unlock or look.
- Holds within 2 s of the previous one are ignored (debounce).
- **Stopping:** the Stop button on that card, the notification's Stop action,
  or the in-app button. A hold never stops a recording, so a stray press
  can't end a three-hour lecture.

**Haptics** (`VibrationEffect.Composition`, which the Pixel supports natively)

| Event | Pattern |
|---|---|
| Recording started | two rising ticks |
| Bookmark added | one firm click |
| Bookmark undone | one soft tick |
| Recording stopped | one long thud |
| Error (mic busy, no permission, storage full) | three short buzzes |

**Fallbacks** for when the gesture is unavailable or a different assistant is
wanted: a **Bookmark** action on the recording notification (also shown on
the lock screen), and a Quick Settings tile that starts a recording or adds a
bookmark.

**Onboarding** walks through: mic + notification permission → *set as default
assistant* (deep link to `Settings.ACTION_VOICE_INPUT_SETTINGS`; the assistant
role can't be requested with a dialog) → *set power-button hold to Digital
assistant* (with a screenshot, since the menu path varies by vendor) → a
test hold to show it works. It also says plainly that Gemini stops being the
default assistant while Summarised is selected.

---

## 2. Recording for hours (`:core:audio`)

- `AudioRecord`, `VOICE_RECOGNITION` source, 16 kHz mono PCM-16 — the format
  sherpa-onnx expects, so no resampling.
- One capture thread fans each buffer out to two consumers:
  - **Opus** via `MediaCodec` + `MediaMuxer` (Ogg, API 29+) at 16–24 kbps,
    written as **5-minute chunk files**. A crash or a killed process loses at
    most the current chunk, never the recording.
  - **ASR ring buffer** feeding the VAD.
- `RecordingService` is a foreground service of type `microphone` with a
  persistent notification (elapsed time, bookmark count, Bookmark / Stop
  actions).
- Every 30 s the state is checkpointed to Room (duration, chunk list,
  transcription progress). On restart after process death, the recording shows
  as *interrupted* and stays fully usable.
- Guards: refuse to start below ~300 MB of free space, warn at 30 minutes left,
  stop cleanly when full. Microphone conflicts (incoming call, another
  recorder) pause and resume automatically and are marked in the timeline.
- Playback with Media3 ExoPlayer over the concatenated chunks; tapping a
  bookmark seeks to `t - 10 s`.

---

## 3. Live transcription (`:core:asr`)

- **sherpa-onnx** offline Whisper with Silero VAD: speech is cut into
  utterances of up to 28 s and queued for a single low-priority worker inside
  `RecordingService`. Each utterance becomes a `Segment(startMs, endMs, text)`.
- Language is fixed to `cs` (no auto-detect, which also saves time).
- **Model tiers, picked by an M0 benchmark on the Pixel 10a:**
  - `whisper-small` int8 (~0.4 GB): the safe default, comfortably faster than
    real time.
  - `whisper-large-v3-turbo` int8 (~1 GB): much better Czech. Used live if the
    benchmark shows a real-time factor below ~0.6. Otherwise it's offered as
    *re-transcribe in better quality* after the recording, ideally while
    charging.
- If the queue falls behind (thermal throttling, low battery) it keeps
  buffering and catches up after stop. Nothing is dropped; the user only
  waits longer.

---

## 4. On-device LLM (`:core:llm`)

- **llama.cpp** built from source (git submodule at a pinned tag) with a thin
  JNI + Kotlin wrapper, **arm64-v8a only**.
- **Model:** chosen in M0 by a Czech evaluation (see Verification). Candidates
  that fit 8 GB next to Whisper:
  - **Qwen3-4B-Instruct** Q4_K_M (~2.5 GB, Apache-2.0): the expected default.
  - **Gemma 3n E4B** Q4 (~3 GB, Gemma terms): often stronger in Czech, so it
    competes on quality.
  - **Qwen3-1.7B** Q4_K_M (~1.1 GB): fallback when memory is tight.
- Loaded on demand and unloaded after 60 s idle, so Whisper and the LLM never
  compete for RAM longer than they must. One job queue with priority:
  **ASR > bookmark summaries > section summaries**.
- All structured output is constrained with a **GBNF grammar** to JSON, so
  parsing is reliable rather than hopeful.

### Summariser chain (decided after M0)

M0 measured the same bookmark prompt on the phone and in the cloud:

| Summariser | Czech | Facts | Speed |
|---|---|---|---|
| Groq, Qwen3.8 27B | 5/5 | 5/5 | ~390 tok/s, 1.2 s per bookmark |
| Groq, GPT-OSS 120B | 4/5 | 4/5 | ~400 tok/s, 2.8 s |
| Pollinations, GPT-OSS 20B (no key) | 5/5 | 4/5 | ~67 tok/s, 5 s |
| Gemma 4 E2B on the phone | 5/5 | 2/5 (old prompt) | 1.8 tok/s, > 5 min |

So the app has a `Summariser` chain instead of one engine:

- **Easy (default).** 1) Groq with the user's free key, if they added one in onboarding ("get a free key, 1 minute"); the model is discovered at runtime, preferring Qwen3.x and then GPT-OSS 120B. 2) Pollinations' keyless GPT-OSS 20B. 3) The on-device model. A step is skipped on missing key, network error, 401/402/429 or timeout, and retried later where the server gives `Retry-After`.
- **Advanced.** The user orders any of: Groq, Gemini, Cerebras, OpenRouter (own keys), Pollinations, on-device; or chooses *on-device only*.
- **Pollinations' keyless limits** (one request per 45 s per IP, 100 per day, ~16,000 characters in, 800 tokens out) shape the job queue: requests are spaced, a whole-recording summary is chunked under the input limit, and over the daily cap the chain moves on.
- **Disclosure.** Onboarding says plainly that in Easy mode the transcript text is sent to the chosen provider (never audio), shows which provider produced each summary, and offers *on-device only* in one tap. Keys stay in the app's private storage.
- **F-Droid:** a default that depends on third-party network services needs the `NonFreeNet` anti-feature; *on-device only* keeps the app fully usable without it.
- The on-device model stays as the last fallback; its 1.8 tok/s in M0 (measured on a phone heated by the Whisper turbo runs) still needs the thread-count speed test before choosing between Gemma 4 E2B and Qwen3 4B.

### Bookmark summaries

1. A bookmark at time *t* is ready once the transcript covers `t + 2 min`, or
   once the recording stops.
2. Input: the segments in `[t - 2 min, t + 2 min]`, plus the previous section
   summary for context (so "that" and "he" make sense).
3. Output: `{ title, summary (2–3 sentences), points[] }`, plus template
   extras: `term/definition` for lectures, `task/owner` for meetings,
   `numbers/measurements` for field work.
4. The result appears in the notification ("🔖 5: Výpočet derivací…"), so you
   can check it's right without opening the app.

### Whole-recording summary (map-reduce, mostly done during recording)

- **Map, live:** every ~10 minutes of transcript gets a short section summary.
  A 3-hour lecture becomes ~18 sections, already done by the time you stop.
- **Reduce, on stop:** section summaries + all bookmark summaries →
  final summary using the recording's template. Bookmarked passages get more
  weight: you marked them for a reason.
- **Templates** (default set in Settings, switchable per recording, re-run
  without re-transcribing):

| Template | Output |
|---|---|
| **Přednáška** (lecture) | outline by topic, key concepts with definitions, formulas/examples, *possible exam questions* |
| **Schůzka** (meeting) | TL;DR, decisions, action items (who, what, by when), open questions |
| **Terén** (field) | chronological log, findings, numbers and measurements, to-dos |

### Thermal and battery behaviour

Using `PowerManager.getThermalHeadroom()` and the battery level, LLM jobs are
deferred until stop when the phone is hot or below 20%. Transcription keeps
going unless the phone is critically hot.

---

## 5. Data model (Room)

The schema hasn't shipped, so it's replaced outright (version 1, no migration).
`Speaker` and `ChatMessage` are removed.

```
Recording(id, title, createdAt, durationMs, status, template, asrModel, llmModel)
AudioChunk(id, recordingId, index, filePath, startMs, durationMs)
Segment(id, recordingId, startMs, endMs, text)
Bookmark(id, recordingId, atMs, createdVia, status, title, summary, pointsJson, extrasJson)
SectionSummary(id, recordingId, startMs, endMs, summary)
Summary(id, recordingId, template, bodyJson, generatedAt)
```

`Recording.status`: `RECORDING → FINISHING → READY | INTERRUPTED | FAILED`.
`Bookmark.status`: `PENDING → WAITING_FOR_CONTEXT → SUMMARISING → READY | FAILED`.
`createdVia`: `ASSISTANT | NOTIFICATION | TILE | APP`.

---

## 6. UI

Deliberately small: three screens plus the lock-screen card.

- **Home:** a big record button with elapsed time and bookmark count while
  recording; the recordings list below (title, duration, bookmark count,
  status).
- **Recording detail:** tabs **Záložky** (default), **Shrnutí**, **Přepis**.
  - Záložky: one card per bookmark with timestamp, title, summary and points,
    plus a ▶ button that plays the surrounding audio. Swipe to delete.
  - Shrnutí: the template output, plus a template switcher and *Re-generate*.
  - Přepis: the full transcript, bookmarks shown inline as markers, search,
    tap-to-seek with a mini player.
- **Settings:** models (download, size, delete), default template, bookmark
  window (default ±2 min), Opus bitrate, assistant setup again.
- **Export / share:** Markdown (works directly in Obsidian or Notion), plain
  text, and the audio file.

Material 3 with dynamic color, dark mode by default on the lock-screen card.

---

## 7. Modules (lightweight)

| Module | Responsibility |
|---|---|
| `:app` | Screens, navigation, assistant service + session, Quick Settings tile, Hilt wiring |
| `:core:designsystem` | Theme and shared components (exists) |
| `:core:database` | Room schema above (exists, to be reshaped) |
| `:core:audio` | Capture, Opus chunk writer, `RecordingService`, `RecordingController`, haptics |
| `:core:asr` | sherpa-onnx VAD + Whisper wrapper, transcription queue |
| `:core:llm` | llama.cpp JNI, job queue, prompts and templates, GBNF grammars |
| `:core:models` | Model catalogue, consented downloads, SHA-256 verification |

The v1 `:feature:*` modules are folded into `:app`: three screens don't justify
them. APK target: **under 40 MB** (arm64-only, R8, no bundled models).

Models are never bundled or fetched silently. Onboarding shows each model's
name, size, licence and source, and downloads only on an explicit tap.
Downloads are resumable and SHA-256 verified. For the Pixel 10a the default
download is ~3 GB in total.

---

## Milestones

**M0 — Spikes on the Pixel 10a (risk first).** A throwaway build that proves
the three things everything else depends on:
1. The assistant role receives a power-button hold **on the lock screen**, can
   start a `microphone` foreground service from the session (Android 14+
   restricts background mic starts), and haptics fire with the screen off.
2. Whisper small and turbo: real-time factor and WER on Czech fixtures.
3. LLM candidates: speed (tokens/s) and quality of Czech bookmark summaries.

Output: the chosen models and numbers, written into this file.

*Status:* the lab app is in `lab/` and the test protocol is in [`M0.md`](M0.md). One change from the original plan came out of building it: speech recognition now runs on **whisper.cpp** instead of sherpa-onnx. whisper.cpp and llama.cpp then share one copy of ggml, so the app ships a single inference core. whisper.cpp also brings **Parakeet TDT v3**, which supports Czech and is a third ASR candidate in the benchmark.

#### M0 results so far (Pixel 10a, Tensor G4, Android 17, 26 Nov 2026)

| Question | Answer |
|---|---|
| Power-button trigger | **Works, locked and unlocked.** `invocation_type=6` (power-button long press) reaches the session; the mic service starts directly from it in ~50 ms (no trampoline needed), no silenced audio, debounce works, haptic primitives supported. *Still open:* a real 30-minute recording (scenario E). |
| Speech recognition | **Parakeet TDT 0.6B v3 (q8_0, 668 MB)**: real-time factor 0.12–0.14 (7–8× faster than real time), WER ~24–28 % excluding number formatting. Whisper turbo q5 is somewhat more accurate on one passage (17 % vs 23.5 %) but runs at 1.5–2.5× real time and throttled over three runs, so it can't transcribe live. Whisper small: ~50 % WER, rejected. → **Parakeet for live transcription**; turbo stays a candidate for an optional overnight re-transcription. |
| LLM | Gemma 4 E2B: Czech rated 5/5, prefill 107 tok/s, but **generation only 1.8 tok/s**, about 10× below expectations, after the phone had been heated by the turbo runs. Facts rated 2/5 because the prompt never said *where* the bookmark was. Speed cause (heat vs. threads) and the other models still to be measured. |

Fixes from these results, in the lab: the bookmark position is marked in the transcript; answers are capped and cut-off JSON is repaired; WER is also reported without numbers; a thread-count speed test records the phone's thermal state; scenario E can only pass with a ≥ 30-minute recording; cloud models (Pollinations without a key; Groq, Gemini, Cerebras, OpenRouter with a free key) are available for comparison. Pinning ggml's threads to the big cores deadlocked in ggml's barrier on a second run, so it was dropped.

**M1 — Capture.** *(done, verified on the Pixel 10a.)* Recording service, Opus chunks, checkpoints, assistant
trigger + lock-screen card + haptics, notification and tile fallbacks,
bookmark list with playback. Usable on its own as a bookmarking recorder.
*v0.1.*

**M2 — Live transcription.** *(done: Parakeet via whisper.cpp instead of Whisper; awaiting on-device test.)* Model downloader, VAD + Whisper queue, transcript
tab with inline bookmarks and search.

**M3 — Bookmark summaries.** *(done with the summariser chain: Groq → Pollinations → Gemma 4 E2B; awaiting on-device test.)* llama.cpp integration, job queue, bookmark prompt +
grammar, results in the notification and the bookmark cards.

**M4 — Whole-recording summary.** *(done: summary on stop, in parts for long recordings; templates; Markdown sharing. Live section summaries deferred: cloud summarising is fast enough at the end.)* Live section summaries, reduce on stop, the
three templates, re-generate, Markdown export. *v0.5: feature-complete.*

**M5 — Polish and release.** Thermal/battery tuning from real 3-hour sessions,
optional re-transcription in better quality, accessibility pass, F-Droid
metadata, signed GitHub Release. *v1.0.*

---

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Power-hold gesture limited on some phones or OEM skins | Notification and Quick Settings fallbacks from M1; M0 verifies the Pixel path first |
| Starting a mic foreground service from the assistant session is blocked on Android 14+ | The session shows a visible window, which counts as foreground. Verified in M0; fallback opens a transparent activity for one frame |
| User loses Gemini on the power button | Stated in onboarding; reversible in one settings screen |
| Czech ASR quality with small models | Turbo tier, live if fast enough or as post-recording re-transcription; fixed `cs` language |
| Czech LLM quality at 4B | Czech eval in M0 picks the model; prompts are written in Czech; grammar-constrained output avoids format errors |
| Battery and heat over hours | Live work is throttled by thermal headroom and battery; heavy work is deferred until stop |
| RAM: Whisper + LLM at once | On-demand LLM loading, a single priority queue, never two native jobs in parallel |
| Recording consent laws | First-run notice; a recording notification that can't be dismissed; no hidden-recording mode |

---

## Verification

**Automated (CI, every PR):** `./gradlew assembleDebug lintDebug testDebugUnitTest`.
Unit tests cover the Opus chunk writer, checkpoint/resume, bookmark window
selection (start/end of recording edges, overlapping windows), the
state machine for start/bookmark/debounce, grammar-constrained JSON parsing
with malformed-output fallback, and checksum verification.

**On device (Pixel 10a, per milestone):**
- Czech fixtures: a 10-minute lecture excerpt and a 2-person conversation with
  reference transcripts. Track WER and real-time factor per ASR model.
- Bookmark eval: 20 hand-placed bookmarks with reference summaries, rated for
  factual accuracy and Czech fluency per LLM candidate.
- Endurance: a **3-hour recording** with the screen off and locked, 15
  power-hold bookmarks. Check zero lost audio, all bookmarks summarised, and
  record battery drain and peak temperature in the README.

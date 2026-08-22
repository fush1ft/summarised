# Contributing to Summarised

Thanks for considering a contribution. Summarised is free software and intends to
stay that way, so a few of the rules below are stricter than in a typical Android
project.

## Ground rules

**Everything runs on the device.** Transcription, diarization, summarisation and
chat must work with the phone in airplane mode. The only permitted network calls
are model downloads the user explicitly asked for, and the optional
bring-your-own-key cloud backend that is off by default.

**No proprietary dependencies.** No Google Play Services, no Firebase, no
closed-source SDKs, no analytics or crash reporting that phones home. F-Droid will
reject the app otherwise, and more to the point, it would break the promise the
app makes to its users.

**Free licences only.** Every dependency, and every model weight the app offers to
download, needs a free licence. Add new models to the model catalog with their
licence and source URL recorded alongside them.

**Nothing is downloaded without consent.** Model downloads are always an explicit
user action. Never fetch anything on first launch, on a timer, or in the
background without the user having asked for it.

## Getting set up

You need JDK 17 and a recent Android Studio. The Gradle wrapper handles the rest.

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint (warnings are errors)
```

The project targets `compileSdk` 37 and `minSdk` 29. Android Studio will offer to
download the SDK if you do not have it.

## Before you open a pull request

Run all three commands above and make sure they pass. Lint runs with
`warningsAsErrors`, so a new warning fails the build; if a check is wrong for our
situation, suppress it in `app/lint.xml` with a comment explaining why, rather
than lowering the bar globally.

Keep pull requests focused. A change to the recording pipeline and a redesign of
the library screen are two pull requests, not one.

## Commit messages

Write what changed and why. If you worked around a platform quirk, say so in the
commit body — that note is usually worth more later than the diff itself.

## Reporting bugs

Please include your Android version, device model, and which models you had
downloaded. For transcription or diarization problems, the length of the
recording and the number of speakers matters a great deal; include both.

Do not attach recordings of other people without their permission.

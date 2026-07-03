# Bocal

**A backendless practice companion for wind players.** Real-time tuner, tone
feedback you can feel, and a local-first practice journal — with **zero
`INTERNET` permission**: nothing can leave your phone, and the build fails if
any dependency tries to add network access (see `:app`'s
`checkNoInternetPermission` task).

> *Bocal* (n.): the curved metal crook on a bassoon or english horn through
> which the player blows. Working name — run formal TM/domain clearance
> before shipping (build brief §7).

This directory is a **self-contained Gradle project** living inside the
`baseline` repo for now; it is designed to lift out to
`asystemofcells/bocal` unchanged when it earns its own repo.

## Hard rules

1. **Backendless.** No cloud, no accounts, no network calls, no
   `android.permission.INTERNET` — enforced at build time on the merged
   manifest. Recording is OFF by default; all data is local.
2. **Colorblind-safe (deuteranopia-class).** State is NEVER encoded in
   red/green, and never by hue alone: every stateful color ships as a
   `StateEncoding(hue, shape, icon, luminance, label)` from `:core:hyle`,
   and tests enforce it.
3. **License hygiene.** Open modules are Apache-2.0; `:core:baseline` is
   proprietary (see its LICENSE). **Never link TarsosDSP (GPLv3) or the GPL
   pYIN Vamp code** — the pitch detectors are clean-room implementations
   from the YIN (de Cheveigné & Kawahara 2002) and MPM (McLeod & Wyvill)
   papers.

## Module map

| Module | License | What it is |
|---|---|---|
| `:core:model` | Apache-2.0 | Pure-Kotlin domain models; note math; the versioned `TrendAnalyzer` seam |
| `:core:pitch` | Apache-2.0 | `PitchDetector` interface + clean-room YIN and MPM |
| `:core:timbre` | Apache-2.0 | FFT, spectral centroid, tristimulus, odd/even ratio, tone-steadiness |
| `:core:baseline` | **Proprietary** | Longitudinal trend engine (shared with the archery app) |
| `:core:data` | Apache-2.0 | `.bocalbundle` session interchange (versioned JSON + audio in zip) |
| `:core:session` | Apache-2.0 | Analysis vertical: held-note tracking, session metrics + score, intonation tendency maps, humane streaks, offline summaries |
| `:core:hyle` | Apache-2.0 | Hyle tokens + the colorblind `StateEncoding` contract |
| `:core:haptics` | Apache-2.0 | Haptics IR (the cross-app authoring contract) + tuner feedback planner |
| `:core:audio` | Apache-2.0 | `AudioEngine` seam; AudioRecord engine today, Oboe next |
| `:feature:tuner` | Apache-2.0 | MVP surface: note, cents meter, status — shape+icon+label redundant |
| `:app` | Apache-2.0 | Composition root; manifest; the no-INTERNET build guard |
| `:test-fixtures` | Apache-2.0 | Synthetic sine/saw/harmonic generators, WAV writer |

The analysis layer wires the tuner to the trend engine: `NoteHoldTracker`
turns live pitch readings into `NoteAttempt`s, `SessionAnalyzer` scores a
session (higher = more accurate) for `:core:baseline`, `IntonationTendencyMap`
gives the per-note sharp/flat bias, `StreakCalculator` implements a humane
"never miss twice" streak (one missed day is forgiven — rigid streaks
backfire), and `TemplateSummaryProvider` is the offline default behind the
`SummaryProvider` seam (the Urbana slot; nothing here needs the network). Reed
rotation/retirement lives in `:core:model`'s `equipment` package.

Planned next: `:core:pitch-neural` (SwiftF0/ONNX, MIT — the noisy-room
accuracy engine; falls back to DSP above ~2093 Hz), `:feature:journal` and
`:feature:equipment` UIs over these engines, Room storage in `:core:data`,
Oboe capture engine.

## Building

```sh
# Tier 1 — headless JVM tests (run these on every save):
./gradlew :core:model:test :core:pitch:test :core:timbre:test \
          :core:baseline:test :core:data:test :core:session:test \
          :core:hyle:test :core:haptics:test

# Everything, including Android unit tests:
./gradlew test

# Debug APK (also runs the no-INTERNET guard):
./gradlew :app:assembleDebug

# Install on the RedMagic over network adb:
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+, Android SDK (platform 35). No hosted CI by design —
human-initiated Gradle is the whole pipeline (brief §6).

## Architecture notes

- **Liftable seams:** every `:core:*` module hides implementation behind an
  interface (`PitchDetector`, `AudioEngine`, `TrendAnalyzer`,
  `HapticRenderer`, `SummaryProvider` when it lands) so modules can be
  extracted, swapped, or upgraded (AudioRecord → Oboe) without touching
  consumers.
- **Audio path:** capture thread → `FrameHopper` (4096-sample window,
  480-sample hop = 100 updates/s) → conflated channel → analysis on a
  Default-dispatcher worker → `StateFlow` → Compose. DSP never runs on the
  capture thread.
- **Haptics:** the tuner's second, non-visual feedback channel. Pitch state →
  `TunerHapticPlanner` (hysteresis + rate limiting, pure logic) → haptics IR →
  `AndroidHapticRenderer` (capability-checked `VibrationEffect.Composition`,
  one-shot fallback).
- **Coach exchange (v3) is designed now:** `SessionBundle` carries
  `annotations` anchored to timestamp + note index, `schemaVersion`, and
  tolerates unknown fields so old apps open new bundles.

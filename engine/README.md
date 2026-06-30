# Baseline Engine

The **sport-agnostic core** of the Baseline athlete-performance platform — a pure
Kotlin/JVM library that knows nothing about archery (or fencing, or hangboarding). It
implements one reusable loop:

```
signal → personal baseline → deviation score → fatigue trajectory → (feedback)
```

Sports plug in from the outside by implementing the `SportModule` seam. The first consumer
is the **Form Analyser** archery app (separate repo), which supplies the archery
rep-segmenter, feature extractor, and scoring rubric.

> This is the generalization of the original Baseline project (the MW75 EEG state-estimator,
> which lives elsewhere in this repo). There, the biosignal was EEG; here it is body / equipment
> motion. EEG returns later as one optional **mental-state channel** feeding the same engine.

## What's in here

| Package | Responsibility |
|---|---|
| `model` | `Athlete`, `Session`, `Rep`, `SessionContext`, and the generic `TimeSeries` container |
| `stats` | Welford online mean/variance, OLS linear fit + Pearson r |
| `baseline` | `BaselineBuilder` / `BaselineModel` — per-athlete "your good" distribution |
| `deviation` | `DeviationScorer` — signed per-feature z + a 0–100 stability summary |
| `fatigue` | `FatigueTracker` — session-level decay trajectory (the strength-endurance story) |
| `sport` | `SportModule` seam (`RepSegmenter`, `FeatureExtractor`, `ScoringRubric`) and `SignalScoreCorrelation` (the "this drift costs you X points" differentiator) |

Everything operates on extracted **feature vectors** (`Map<String, Double>`), so the core
stays small, sport-neutral, and deterministic. Raw time-series live in storage, not here.

## Design principles baked in

- **Honesty over confidence.** Baselines below `MIN_REPS_FOR_BASELINE` report "not ready";
  correlations carry their `n` and `R²` so the UI can refuse to over-claim on thin data;
  near-zero-variance metrics return z = 0 rather than exploding.
- **The seam is the contract.** New sports add a `SportModule`; the engine never changes.

## Build & test

```bash
cd engine
./gradlew test                 # run the unit tests
./gradlew publishToMavenLocal  # publish xyz.mdhv.baseline:baseline-engine:0.1.0 for Form Analyser
```

Requires JDK 21. The Form Analyser repo resolves this library from `mavenLocal()`, so publish
it there before building Form Analyser locally.

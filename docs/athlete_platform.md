# Baseline as a cross-sport athlete-performance engine

This repository began as the **MW75 EEG state-estimator** (see `README.md`, `research_brief.md`,
`build_plan.md`). The athlete-performance platform is the *generalization* of that idea: the
same loop — **signal → personal baseline → deviation → fatigue → feedback** — applied to body
and equipment motion instead of EEG. The name "Baseline" carries over because the core concept
is identical; only the biosignal changes.

## Two repos, one seam

| Repo | Role | Contents |
|---|---|---|
| **`baseline`** (this repo) | The **engine** — sport-agnostic | `engine/` Kotlin library: baseline, deviation, fatigue, the `SportModule` seam, signal→score correlation. Plus the original EEG project (the future mental-state channel). |
| **`form-analyser`** | The **archery app** (codename) — sport-specific | The archery `SportModule` (shot segmenter, IMU feature extractor, inverse-statics strength, scoring rubric) + the Android capture app + (later) the web review companion. |

```
 form-analyser (archery)                 baseline/engine (sport-agnostic)
 ┌───────────────────────────┐           ┌─────────────────────────────────┐
 │ ArcheryShotSegmenter       │  impl ─▶  │ RepSegmenter                     │
 │ ArcheryFeatureExtractor    │  impl ─▶  │ FeatureExtractor                 │
 │ ArcheryScoringRubric       │  impl ─▶  │ ScoringRubric                    │
 │ InverseStatics (strength)  │           │ BaselineBuilder / BaselineModel  │
 │ Android capture app        │ ──uses──▶ │ DeviationScorer                  │
 │                            │           │ FatigueTracker                   │
 │                            │           │ SignalScoreCorrelation           │
 └───────────────────────────┘           └─────────────────────────────────┘
```

The dependency points **one way**: `form-analyser` depends on `baseline/engine`; the engine
never imports anything archery. That is what lets fencing / pistol / hangboard slot in later as
new `SportModule`s without touching the core (handoff §2, §8 roadmap).

## What lives where (mapping the build handoff)

| Handoff section | Goes in |
|---|---|
| §2 the Baseline engine (capture→baseline→deviation→fatigue→feedback) | `baseline/engine` |
| §5 data model (`Athlete`/`Session`/`Shot`) | `baseline/engine/model` |
| §1.2 / §3.5 signal→score correlation (the differentiator) | `baseline/engine/sport` (generic) + archery score-capture (Form Analyser) |
| §3.1–3.3 archery error sources, signal map, governing principle | `form-analyser` archery module |
| §3.4 strength via known-load quasi-static inverse statics | `form-analyser` `statics/InverseStatics` |
| §4 Steady Aim A1 Pro integration / validation | `form-analyser` (BLE + side-by-side validation) |
| §6 pose, CameraX, BLE, Hyle design | `form-analyser` Android app |
| §11 first build target (shot-stability tracker) | `form-analyser` |

## Dependency wiring

The engine publishes as `xyz.mdhv.baseline:baseline-engine:<version>`. For local dev:

```bash
cd baseline/engine && ./gradlew publishToMavenLocal
cd form-analyser   && ./gradlew :archery-module:test
```

Form Analyser resolves the engine from `mavenLocal()`. A future move to GitHub Packages /
a real Maven repo removes the manual publish step.

## Open decisions (handoff §11) — resolved for v1

1. **KMP vs native** → engine is plain Kotlin/JVM today (headless-testable); the Android app
   is native Kotlin. The engine has no JVM-only deps, so it can move to KMP later if the web
   companion wants to share it.
2. **Pose backend** → MediaPipe/BlazePose (in the Android app, not the engine).
3. **A1 Pro** → side-by-side validation first (§4b).
4. **Score capture** → manual entry first; target-face CV later.
5. **Raw-stream store** → Room/SQLite on device; the engine only sees extracted features.

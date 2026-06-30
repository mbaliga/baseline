# Archery sensor channel (paid add-on)

Parked source for the **sensor-based** archery signals — the shot-to-shot, score-critical
channel that the **paid** Baseline add-on contributes on top of the free Form Analyser app.

Per the build-handoff governing principle (§3.3): **the sensor owns drift / release / cant /
hold-steadiness** (what decides *this* arrow), while vision owns the slow postural form. The
free [Form Analyser](https://github.com/mbaliga/Form-analyser) app does the vision side; this
is the premium sensor side, fed by a bow-mounted IMU (the **Steady Aim A1 Pro** the user owns,
or a dedicated IMU).

## What's here

Kotlin implementing the Baseline engine's `SportModule` seam from an IMU `TimeSeries`:

- `signal/Fft.kt`, `signal/Filters.kt` — radix-2 FFT + Butterworth (tremor / release spectral analysis)
- `ArcheryShot.kt` — IMU channel names + set-up/hold/release shot model
- `ArcheryShotSegmenter.kt` — segments shots from bow-IMU motion
- `ArcheryFeatureExtractor.kt` — steadiness, pin-drift, cant, release signature, tremor
- tests (`SignalTest`, `PipelineTest`) — FFT/filter correctness, segmentation, extraction, engine integration

This code is fully written and was unit-tested while it briefly lived in the Form Analyser repo.

## Status: parked, not yet a building module

It depends on the **Baseline engine**, which ships inside the free Form Analyser repo. When the
paid layer is built, wire this up as a Gradle module that consumes the engine as a published
artifact (e.g. GitHub Packages) and add the BLE transport for the A1 Pro (handoff §4: start with
side-by-side validation, then reverse-engineer the GATT profile). Until then it lives here as the
correct home for the sensor channel.

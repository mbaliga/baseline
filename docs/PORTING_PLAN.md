# Baseline — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). This file is owned by the lead planning session; a platform
> track updates only its own §4 row. This repo has no PROGRESS or STATE file (README.md is its state
> surface), so a track records its evidence in its §4 row and in the PR that produced it.
>
> Read first: `ARCHITECTURE_V2.md`, `README.md`, `docs/PR_DISPOSITION.md`. About distribution terms this
> plan restates only the README's Community/Advanced split (§3).

## 0. Evidence labels (never dropped)

`LAB` · `CI (hosted VM) evidence` · `EMULATOR EVIDENCE` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` ·
`SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` · `SYNTHETIC` · `CI-ONLY / NOT RUN` ·
`NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV) · `PLAN` · `NOT-APPLICABLE (<reason>)` ·
`CONTAINER-BUILD-ONLY` · `BROWSER-HEADLESS`.

Baseline is a library with no device behaviour of its own. When the §6 steps run, they can produce
`CI (hosted VM) evidence` (JVM on Linux, Windows, macOS; Kotlin/Native on Linux and macOS), `SIMULATOR`
(`iosSimulatorArm64`) and `CI-APPROX — NOT DEVICE EVIDENCE` (linuxArm64 on an arm64 userland). Baseline inside
a consumer app on a phone, iPad, Mac or PC is `NDV`/`NOV` and belongs to the consumer's device checklist.
Today every row in this document is `PLAN`.

## 1. What this repo is, in porting terms

- **Product.** Baseline is the local personal evidence engine shared by Crocodyl, Ebbflow and future apps: a
  temporal evidence graph with provenance, time-resolution law, contextual baselines, experiment logging
  and evidence-graded claims E0 to E5 (`README.md`, `ARCHITECTURE_V2.md`). It is a substrate, not an app.
- **State: seeded.** `main` (HEAD `4cb84a0` when read, 2026-10-06) tracks 32 files. Commit `b398aa3`
  (2026-09-01, "establish Baseline architecture v2 Community foundation") added three Gradle Kotlin/JVM
  modules and five JUnit tests; later commits touch only CI (`.github/workflows/`). Every product
  directory from `build_plan.md` §0.3 (`core/`, `ambient-controller/`, `music-controller/`, `probe-app/`,
  `streamer-android/`, `streamer-linux/`, `data/`) holds only a `.gitkeep`. `docs/session_handoff.md` (dated
  2026-06-03) still says "nothing on main yet" and Phase 0.1/0.2 "not started"; it predates the foundation
  commit and is not a reliable state source for the code.
- **Targets today.** One: a JVM library, built and tested by `ci.yml` on `ubuntu-latest` with Temurin 21 and
  Gradle 8.14.3 (configured; this plan did not check the last run's result). No packaged artefact, no
  published coordinates, no consumer: a search for `dev.baseline` across Ebbflow, Form-analyser and
  Shared-Libraries-asoc found no file (2026-10-06). Form-analyser carries a separate engine package under
  another name (`Personal-Tracker/NAMES.md`); this library is not wired to it.
- **Stack.** Kotlin 2.1.0 (JVM only) · no UI toolkit (the v0.2 React/WebView plan in `research_brief.md` §8 is
  superseded here by "substrate, not another UI") · Gradle Kotlin DSL with `gradle/libs.versions.toml`, plugin
  `org.jetbrains.kotlin.jvm` only, `mavenCentral()` only with `FAIL_ON_PROJECT_REPOS` · JUnit 4.13.2 is the
  only dependency · no Gradle wrapper committed (CI pins Gradle through `gradle/actions/setup-gradle`) · no
  `jvmToolchain` or `jvmTarget` declared · native dependencies: none.
- **Size (measured 2026-10-06).** `find . -name '*.kt' -not -path './.git/*' | xargs wc -l` gives 322 lines in
  6 files: 217 production (contracts 95, ledger 56, stats 66) and 105 test (29, 43, 33).
  `grep -rn '@Test' --include=*.kt . | wc -l` gives 5 tests. A grep of the production sources for `System.`,
  `Random`, `Locale`, `java.`, `javax.`, `Instant`, `Clock`, `File`, `Thread`, `Charset` and `format(` finds
  nothing: no clock, file, network, locale or randomness.

## 2. Portable core vs platform-bound layers

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `baseline-contracts` | Evidence enums, `TimeRange`, `Provenance`, `Measurement`, `EvidenceRecord` with invariant checks, `ResolutionLaw`, `ClaimLanguage` (`Evidence.kt`) | commonMain-safe as written | 95 | Epoch-millisecond `Long` times and a timezone id string: no `java.time`. Only the build plugin and `EvidenceTest.kt` (JUnit 4, 2 tests) are JVM-bound. |
| `baseline-ledger` | `EvidenceLedger` interface and `InMemoryEvidenceLedger` (`EvidenceLedger.kt`): idempotent append by id and content hash, append-only retractions, supersession, referential checks | commonMain-safe except one construct | 56 | Four `@Synchronized` annotations (`append`, `get`, `history`, `active`). `EvidenceLedgerTest.kt`: JUnit 4, 2 tests. Its KDoc: "Persistent adapters must preserve these semantics"; none exist. |
| `baseline-stats` | `DescriptiveStats.pearson`, `OrdinaryLeastSquares.fit` (`Regression.kt`), "for Community acceptance fixtures, not causal promotion" | commonMain-safe as written | 66 | Imports only `kotlin.math.sqrt`. It declares `implementation(project(":baseline-contracts"))` but no stats source or test imports contracts, so it is a leaf and can convert independently. 1 JUnit 4 test. |
| Scaffold dirs (`core/`, `ambient-controller/`, `music-controller/`, `probe-app/`, `streamer-android/`, `streamer-linux/`, `data/`) | v0.2 placeholders, `.gitkeep` only | nothing to port | 0 | `Personal-Tracker/DECISIONS.md` D-S records that the EEG product moves to Ebbflow and that this repo becomes the engine; marked not started. This plan does not touch these directories. |
| `docs/`, root planning docs | `research_brief.md` (456 lines), `build_plan.md` (295), `ARCHITECTURE_V2.md` (42), `docs/*.md` | docs only | 1,210 (all .md) | Not ported. |
| `.github/workflows/` | `ci.yml` (`gradle test`, Linux only), `cleanup-artifacts.yml` (six-hourly artifact and cache purge) | Linux-only matrix | n/a | No macOS or Windows leg exists. |

Platform-bound APIs that matter:

| API | Where | Porting impact |
|---|---|---|
| `@Synchronized` | `baseline-ledger/.../EvidenceLedger.kt`, four methods | The profile reading this repo reports it is unavailable in commonMain. **Unverified here:** some JVM annotations are optional expectations that compile in common code but do nothing off the JVM, which would silently drop the lock on native targets. Options in §6 K2. |
| JUnit 4 (`org.junit.Test`, `Assert`, `@Test(expected = ...)`) | the three `src/test` files | JVM-only runner. Five tests move to `commonTest` on `kotlin.test` (`assertFailsWith`); mechanical. |
| `org.jetbrains.kotlin.jvm` plugin; `src/main/kotlin` layout | root and three module `build.gradle.kts`, `gradle/libs.versions.toml` | Becomes `kotlin("multiplatform")`; sources move to `src/commonMain/kotlin`; `jvm()` stays so JVM consumers are unaffected. |
| `implementation(project(":baseline-contracts"))` in ledger and stats | module `build.gradle.kts` | The ledger's public signatures expose contracts types, so a published or exported build needs `api` (and `export(...)` in an Apple framework) or consumers cannot see `EvidenceRecord`. |
| No Gradle wrapper; no toolchain pin | repo root | macOS and Windows local builds need a wrapper. Bytecode level floats with the build JDK; the minimum for Android consumers is not known to this plan. |
| Exceptions at a Swift or C boundary | `require(...)` in every constructor | Not platform-bound today. In Kotlin/Native's Objective-C interop an exception not declared with `@Throws` aborts the process instead of reaching Swift. Plan item K5, **to verify**. |
| Floating-point results | `Regression.kt`, the stats acceptance test | The KDoc says "deterministic"; at best that holds per target. Cross-target bit-identity is **unknown**. The test tolerance is 1e-8 (`kotlin.math.abs(...) < 1e-8`), which is expected to absorb a few ulps of difference; K6 confirms per target. |
| Bluetooth RFCOMM/BLE; Python daemon, Home Assistant, VLC, React UI | docs only (`research_brief.md` §3.2/§8.2, `docs/streamer_port_plan.md`, `build_plan.md`) | No code here; nothing to port. The iOS exclusion in the brief binds EEG acquisition (Ebbflow, mw75-streamer), not this library. |

## 3. Binding rules this port must not break

- **Community/Advanced split** (`README.md`). Every open consumer must build and remain useful without
  Advanced; Advanced may not gate safety, export, retraction or athlete history. A port ships the Community
  library only. Advanced is not in this repo, and no hook for it is added.
- **Product boundary** (`ARCHITECTURE_V2.md`; `docs/PR_DISPOSITION.md`). A substrate, not another athlete or EEG
  UI. Crocodyl owns archery interpretation; Ebbflow owns acquisition, BLE/RFCOMM, Room and Compose. No target
  gains a UI module or a Bluetooth layer here. Parked branches (PR #3, #5) are never merged; no
  reverse-engineered production BLE.
- **Data law** (`ARCHITECTURE_V2.md`; `EvidenceLedger.kt` KDoc). Append-only; inferences are separate records
  that reference evidence; resolution moves only right; every record carries the full identity, time,
  provenance, quality, privacy, schema and hash fields. The KMP conversion must not change observable
  behaviour: the five existing tests keep their assertions unchanged and become the cross-target bar.
- **Evidence language** (`ARCHITECTURE_V2.md`; `ClaimLanguage`). Nothing, on any platform, words a claim above
  the estimator's grade E0 to E5; a platform presentation layer may not promote a claim.
- **Deployment law** (`ARCHITECTURE_V2.md`). Embedded Community library for phone-only operation; the phone is
  authoritative for records it created until explicit, mutually authenticated, end-to-end encrypted local
  sync; failure to reach a personal node never blocks capture or training. No target may make a node mandatory.
- **Local-first, no cloud** (`research_brief.md` §3.6, §7.5; `build_plan.md`). Durable, local, encrypted at
  rest; no cloud sync by default; the user owns export; no public release of raw personal signals.
  Program directive I-1 restates it: no telemetry, analytics or phoning home on any platform. The library has
  no network code today (measured above) and a port adds none, including no crash reporter.
- **Secrets and data** (`.gitignore`; `docs/session_handoff.md`). `data/` stays gitignored; no secrets in the repo;
  no key material in CI (program R5).
- **Working agreements** (`build_plan.md`, "Working agreements with Claude Code"). Plan before code; no
  unilateral scientific or methodology choice; architecture choices are the implementer's call with a
  one-line reason; honest uncertainty. Never push to `main` without explicit permission
  (`docs/session_handoff.md`); work on `claude/*` branches and open a PR.
- **Naming** (`Personal-Tracker/NAMES.md`, row "Baseline (the engine)"). "Baseline" is owned by this repo. The
  registry lists the engine's package as `xyz.mdhv.baseline.*`; the code here is `dev.baseline`. No
  coordinate, framework or module name is chosen in this plan (program R11; §8 Q3).
- **Licence undeclared.** No `LICENSE` file is checked in. Until it is ruled (program OQ-12) nothing leaves the
  repo: no published coordinates, no released XCFramework, no SPDX headers added.
- **Colour never carries meaning alone** (program I-3; owner is red-green colourblind). The library has no UI,
  so nothing here is colour-coded; any demo or sample UI added later must carry shape and label.
- **Environment honesty** (program I-4). This plan builds and runs nothing and claims nothing works on any
  device. Container checks are `CONTAINER-BUILD-ONLY`; hosted runs are `CI (hosted VM) evidence`.
- **Program rules** (`Personal-Tracker/PORTING_PROGRAM.md` §3). R1 disjoint directories and source sets; R2 the
  existing gate (`gradle test` in `ci.yml`) stays green; R3 new workflow files only, SHA-pinned actions,
  `ci.yml` untouched, Windows path lint before any Windows lane; R4 pure-core-first (this repo is only a core);
  R6 nothing released, no Actions artifact uploads (storage is exhausted; `cleanup-artifacts.yml` exists for
  that reason); R11 no per-platform identifier before a `NAMES.md` row.
- **Scope of the brief.** `research_brief.md` §8.1 lists Linux, Android and a Web UI for the v0.2 daemon, and
  §3.2 and §8.2 exclude iOS and pure web for the EEG streamer. The targets below extend the library's reach under
  program directive I-7 (Apple platforms in scope). They do not change §3.2, §8.1 or §8.2 for the daemon or the streamer.

## 4. Target matrix (owner's order)

Effort is the program's estimate (`PORTING_PROGRAM.md` §5, baseline row), in engineer-weeks. The cells overlap
rather than add: the one-time KMP conversion is counted in the iOS cell and the other cells are increments
over it.

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | moderate | There is no app to package; the deliverable is "a library a Lomiri click can embed". Three routes, chosen only after OQ-1, the S-UT1 verdict and the existence of a UT consumer: (1) bundle the unchanged JVM jars in F7's jlinked headless core; (2) Kotlin/Native `linuxArm64` static library behind a narrow C facade for a QML/C++ plugin; (3) schema-first reimplementation in the consumer's language, with this repo's tests as conformance vectors. The program's 2w is taken here as route (2). | No UT consumer exists (program rows: Crocodyl reframe, Ebbflow reframe); no UT device on record (OQ-1); S-UT1 not run; K/N `linuxArm64` is Tier 2 and untested in this constellation; a closed component is confined to common policy groups or sideload; no persistent adapter or at-rest encryption exists on any platform | 2 (route 2). Route 1 ≈ 0 in this repo (cost sits in F7). Route 3 not estimated here. | PLAN |
| Linux desktop | native-fit | Already configured to build and test on Linux (JDK 21). Work is packaging only: commit the Gradle wrapper, declare a toolchain, add a Kotlin/Native `linuxX64` lane that proves commonMain is platform-free, and decide the sharing mechanism. The personal-node daemon (`ARCHITECTURE_V2.md`) is unbuilt and out of scope. | No wrapper; no published artifact (gated on OQ-12); sharing mechanism undecided (PT:D-A source vs PT:D-S binary, §8 Q3) | 0.5 | PLAN |
| iOS / iPadOS | straight | Convert the three modules to Kotlin Multiplatform in place (`jvm()` kept), replace `@Synchronized`, move the 5 tests to `commonTest`, add `iosArm64` and `iosSimulatorArm64`, and produce an XCFramework for a Crocodyl or Ebbflow iOS consumer. The brief's iOS exclusion is about RFCOMM EEG streaming, not this library. | Needs a `macos-latest` job (billing on a private repo: OQ-20); no iOS consumer exists; licence undeclared (OQ-12); Kotlin 2.1.0 may predate the hosted Xcode (OQ-17, unverified); lock and Swift-surface decisions (§8 Q7, Q8) | 1.5 (includes the shared conversion) | PLAN |
| macOS | native-fit | The JVM jars run on macOS unchanged. For a native Swift consumer, add `macosArm64` to the same build and to the same XCFramework, and run its tests in the Apple lane. Intel Macs use the JVM jar; `macosX64` is not planned. | Same conversion and Apple runner as iOS; no Mac on record (OQ-5) so consumer-side validation stays NOV; no stated owner deployment on macOS | 0.5 | PLAN |
| Windows | native-fit | The JVM jars run on Windows unchanged. Add the wrapper including `gradlew.bat` (with a line-ending rule) and a `windows-latest` test lane behind the R3 path lint. `mingwX64` is not planned: no consumer. | No wrapper yet; no Windows consumer or owner deployment (the Dell is slated for Linux per `build_plan.md`; fate is OQ-5) | 0.5 | PLAN |

Sum of the five cells: 5.0 engineer-weeks. Because the conversion is paid once, the real total is lower if it
lands first; this plan does not re-estimate. The reading that fed this plan described the UT cell as already
containing a share of the conversion, while §6 budgets 2.0w for UT steps excluding it, so the UT increment may
be smaller than 2w. These are estimates, not a schedule.

## 5. Tier and sequencing

**Tier C** (thin, packaging-only), matching the program's §5 row: Baseline is not an app, it is a 322-line
Kotlin library with no UI, no Bluetooth, no persistence and no consumer wired to it. Porting it is packaging:
a Kotlin Multiplatform build plus, for non-JVM consumers, a contract they can implement. It has no
per-platform artefact of its own and no end-user value on any platform until a consumer embeds it.
Everything beyond the library (personal node, probe app, controllers) is unbuilt and stays behind the repo's
own gates (`build_plan.md` phases; D-S migration).

**Gate before any wave** (program §5 row): licence posture, meaning the README's Community/Advanced split
against PT:D-S "proprietary" (OQ-12), and the consumption model, embed against LAN node (§8 Q2).

**Waves.** `PORTING_PROGRAM.md` §7 names no baseline step and Baseline has no wave of its own. It joins a
platform's wave only as a dependency of a consumer that does:

| Platform | Wave it rides | Build-entry for this repo's work | Device-entry |
|---|---|---|---|
| Linux | P-LX | the repo's own gate (R2); **proposed deviation from P-LX build-entry:** compile-only lanes run on the repo's own catalog until F5 exists, adopting F5 (at the OQ-17 pin) is a later step. Publication waits for OQ-12. | none: a library |
| iOS / iPadOS | P-iOS | conversion landed; `macos-latest` lane approved (OQ-20); toolchain pin ruled (OQ-17) | the consumer's: Apple Developer Program and delivery route (OQ-2), the iPad Pro M4 |
| macOS | P-mac | same Apple lane | the consumer's; no Mac on record (OQ-5) |
| Windows | P-win | wrapper committed; path lint in place | the consumer's; the Dell while still Windows (OQ-5) |
| Ubuntu Touch | after the program's UT waves | OQ-1 answered or an explicit CI-only waiver; S-UT1 verdict from F7; a UT consumer scheduled; route chosen | the consumer's, on a UT device |

**Placement proposal (not in program §7, for the owner).** Do the conversion (§6 K-steps) before the first
consumer's non-Android port starts, because it is small (1.5w), every non-JVM consumer would need it, and
Baseline has no Hyle, AGP or Android coupling to wait on. It is not a prerequisite of anything the program
schedules today: the program's Crocodyl row records that the Baseline engine seam "never moved" (PT:D-S). If
no consumer commits to the embed model (§8 Q2), defer it.

## 6. Work breakdown

Ordered steps. Platform lanes follow R1 (new workflow files only; `ubuntu-touch/` for UT). The K1/L1/L2 conversion edits the root and module build files and moves the source sets, which is outside R1's disjoint directories; it is the conversion the program's §5 iOS cell for this repo prescribes, lands only through the repo's normal PR process (claude/* branch, PR, never a push to main), and R2 is its guard: `gradle test` as ci.yml runs it must report the same five tests. Nothing below edits
`ci.yml`, `cleanup-artifacts.yml`, the scaffold directories, `research_brief.md`, `build_plan.md` or the other
docs. Estimates sum to each §4 cell. `CONTAINER-BUILD-ONLY` means a later stage can check it in this
container's JVM toolchain; Kotlin/Native toolchain download here and anything Apple are unverified.

**Placement**

| Path | Step | Change |
|---|---|---|
| `baseline-*/src/commonMain`, `src/commonTest` (moved from `src/main`, `src/test`) | K1 | `git mv`, package `dev.baseline.*` unchanged |
| `baseline-*/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` | K1, L2 | plugin swap, targets, `kotlin-test` |
| `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitattributes` | L1 | new files only |
| `.github/workflows/linux.yml`, `apple.yml`, `windows.yml`, `ubuntu-touch.yml` | L4, I2/M2, W2, UT3 | new files, one per platform |
| `ubuntu-touch/` | UT2 | new, only if route (2) is chosen |
| `apple/` | none | not needed: Gradle builds the XCFramework; a consumer owns any Xcode project |

**Default gating.** Native targets are declared only when Gradle is given `-PwithNative` (the pattern
Form-analyser uses with `-PwithAndroid`). The default build, which is what `ci.yml` runs, stays JVM-only, so
its cost and network profile do not change (R2, R3). The new workflows pass the flag.

### Linux desktop (0.5w)

- **L1 wrapper, 0.1w.** Add the Gradle wrapper at 8.14.3 (the version `ci.yml` pins) and a `.gitattributes`
  with `gradlew.bat text eol=crlf` and `gradlew text eol=lf`. Add no other rules: the repo has no
  byte-exact fixtures (the tests generate their data). `ci.yml` calls `gradle`, not the wrapper, so it is
  unaffected. Done when `./gradlew test` and `gradle test` both pass on a Linux checkout. `CONTAINER-BUILD-ONLY`.
- **L2 toolchain, 0.1w.** Declare `jvmToolchain(21)` (what CI already runs) and a bytecode target of 17
  (program §4.2 compiles `--release 17`). The Android minimum of Ebbflow and Crocodyl is not known to this
  plan; confirm before publishing. Done when the class-file version is asserted in a test or CI step.
- **L3 sharing mechanism, 0.2w.** Decide, do not publish. The ruled mechanism is a git submodule plus
  `includeBuild` (PT:D-A; Maven is a later owner-gated upgrade). PT:D-S says open apps consume Baseline as a
  licensed binary, not vendored source. These two conflict if Baseline is proprietary (§8 Q3). Done when
  the owner rules; an optional `publishToMavenLocal` dry run in CI uploads nothing.
- **L4 native Linux lane, 0.1w. Needs K1.** `.github/workflows/linux.yml`: `ubuntu-latest`, SHA-pinned
  actions, `-PwithNative`, `linuxX64Test`. Its purpose is to prove commonMain compiles with no JVM API
  visible. No artifact upload (R6). Done when it passes; `CI (hosted VM) evidence`. Whether declaring native
  targets makes Gradle download the Kotlin/Native toolchain (extra minutes and network) is unverified; this is
  why the flag exists.

### Kotlin Multiplatform conversion (shared; K1 to K6 = 1.1w, carried in the iOS cell)

- **K1 conversion, 0.5w.** Plugin to `kotlin("multiplatform")` at the existing `kotlin = "2.1.0"` (a bump is
  OQ-17's business); `jvm()` always; sources and tests moved with `git mv`; `kotlin("test")` in commonTest.
  Keep module names and paths (`:baseline-contracts`, `:baseline-ledger`, `:baseline-stats`). Done when
  `gradle test --no-daemon --console=plain`, exactly as `ci.yml` runs it, reports the same 5 tests passing.
  **Unverified:** whether this plugin version provides a `test` task. If not, K1 registers a `test`
  lifecycle task depending on `jvmTest` so `ci.yml` needs no edit.
- **K2 the lock, 0.2w. Proposal, §8 Q7.** Replace the four `@Synchronized`. (A) `kotlinx-atomicfu`
  `SynchronizedObject` and `synchronized {}`: one Apache-2.0 dependency; its API stability at the pinned
  version is unverified. (B) Declare `InMemoryEvidenceLedger` single-threaded and leave locking to the
  persistent adapter: no dependency, but it changes the public class's current JVM behaviour. (C) A small
  internal expect/actual lock. Default if the owner has no preference: (A), because it keeps today's
  guarantee on every target. If thread safety is claimed, add a multi-thread append test (JVM and native):
  today no test covers the property the annotation asserts.
- **K3 tests, 0.1w.** Move the five tests to `kotlin.test`; assertions unchanged;
  `@Test(expected = IllegalArgumentException::class)` becomes `assertFailsWith`. Done when `jvmTest` lists the
  same five names and `linuxX64Test` (L4) passes the same five.
- **K4 API exposure, 0.1w.** `api(project(":baseline-contracts"))` in the ledger, and in the stats module if
  it ever uses contracts types; `export(...)` for Apple frameworks. Done when a consumer compiles against
  the ledger alone.
- **K5 Swift and C surface, 0.1w. Proposal, §8 Q8.** Invariant-checking constructors throw
  `IllegalArgumentException`. Decide between `@Throws` on the public entry points and non-throwing factories
  (`tryCreate`-style results). Either is additive but touches the public contract, so it is the owner's call.
- **K6 float check, 0.1w.** Run the stats test on every target; record any last-bit difference; keep the
  1e-8 tolerance. Do not edit the KDoc's word "deterministic" until the result is known.

### iOS / iPadOS (1.5w = K1 to K6 at 1.1w, plus I1 0.2 and I2 0.2)

- **I1 targets and framework.** Under `-PwithNative` add `iosArm64()` and `iosSimulatorArm64()` (no
  `iosX64`: hosted macOS is arm64, program §4.3) and one XCFramework carrying contracts and ledger, stats
  optional. The framework name is **not chosen** here (R11, OQ-25).
- **I2 Apple lane.** `.github/workflows/apple.yml`: `macos-latest`; `iosSimulatorArm64Test`,
  `macosArm64Test`, `assembleXCFramework`; no upload, no signing; manual dispatch while the repo is private
  (OQ-20). The first run answers the open toolchain question: Kotlin 2.1.0 against the hosted Xcode (26.x
  per the program's iOS brief). If it fails, the pin moves under OQ-17, and Baseline is not coupled to
  Hyle's lockstep. Done when the job summary is pasted into the PR. Evidence: `SIMULATOR` for the iOS test
  run; the framework is `UNSIGNED — not for release`.
- **I3 consumer hand-off (not in this repo).** A consumer's own Xcode project (generated with XcodeGen
  by its own program track) links the framework. The library has no clock, file, network, locale or random
  calls (measured), so it exercises none of Apple's required-reason APIs; the app-level privacy manifest is
  the consumer's. iPad behaviour is `NDV` via the consumer.

### macOS (0.5w)

- **M1 target, 0.3w.** Add `macosArm64()` under `-PwithNative` and the macOS slice to the XCFramework.
- **M2 lane, 0.2w.** `macosArm64Test` in `apple.yml`, plus `jvmTest` on the macOS runner. Intel Macs: JVM
  jar only; `macosX64` is deprecated in recent Kotlin and is not planned. Nothing to sign or notarise in a
  library; the consumer's app is signed by the consumer's lane (OQ-3). `CI (hosted VM) evidence`; any
  consumer-on-Mac claim stays `NOV` (OQ-5).

### Windows (0.5w)

- **W1 path lint, 0.1w. Before any Windows lane (R3).** Fail on any tracked path containing `: < > | ? * "`
  or a reserved device name. Measured at HEAD: zero offending paths, no non-ASCII bytes in any `.kt` file.
  Inline in `windows.yml` until the program's CI template (F9) exists.
- **W2 lane, 0.3w. Needs L1.** `.github/workflows/windows.yml`: `windows-latest`, `gradlew.bat test`; manual
  dispatch while private (OQ-20); no artifact. Done when the 5 tests pass. `CI (hosted VM) evidence`.
- **W3 decision record, 0.1w.** `mingwX64` is not added (Tier 3 in Kotlin/Native; no consumer). Revisit only
  if a Windows-native consumer appears.

### Ubuntu Touch (2w = UT1 0.3 + UT2 1.2 + UT3 0.5, gated; route 2 only)

- **UT0 gate, 0w.** No work until OQ-1 is answered or a CI-only waiver is recorded, the S-UT1 verdict exists
  (F7), a UT consumer is scheduled, and the owner picks a route. If S-UT1 passes, route (1) needs nothing here:
  the consumer bundles the jars from L1/L2. If neither Crocodyl nor Ebbflow targets UT, or OQ-21 (Waydroid)
  is accepted for them, skip this section.
- **UT1 `linuxArm64` target, 0.3w. Needs K1.** Add `linuxArm64()` under `-PwithNative` with a static
  library binary. Tier 2: compile-tested upstream, tests not run upstream.
- **UT2 C facade 0.8w, JSON codec 0.4w.** New `ubuntu-touch/` directory (R1) with one Gradle module exposing a
  handful of `@CName` functions that exchange text records. A JSON codec is needed, which is a new
  dependency decision (kotlinx-serialization is not in this repo). The record shape must first be settled
  against `evidence-schema` (§7, §8 Q9). The facade does not alter the root modules.
- **UT3 lane 0.3w, hand-off 0.2w.** `.github/workflows/ubuntu-touch.yml`: cross-compile `linuxArm64`
  on `ubuntu-latest`; optionally run its tests on an `ubuntu-24.04-arm` runner (availability and billing
  for private repos unverified). That run is `CI-APPROX — NOT DEVICE EVIDENCE`. The click, its manifest and
  AppArmor policy belong to the consumer (F7). The library needs no policy group: it does no I/O. A future
  persistent adapter would need app-private storage and has no keystore on UT (program §4.1; OQ-22).
  Behaviour on a UT phone is `NDV`.

## 7. Shared foundation this repo consumes or provides

| Item | Relation | Note |
|---|---|---|
| F1 hyle-kmp | not consumed | No UI. Baseline has no Hyle dependency and is outside PT:D-Q's AGP lockstep. |
| F2 crash-recovery KMP | not consumed | No UI and no crash handler; a library has nothing to show. |
| F3 cell-shell | not consumed | No UI. |
| F4 asom-client | not consumed | No inference client; no contact with asom. |
| F5 kmp-conventions | consumes later | Shared catalog at the OQ-17 pin, licence allowlist (records `kotlin-test`, and `atomicfu` if K2(A)), SPDX opt-in tied to OQ-12. The `android.*` ban is already vacuous here. Proposal: Baseline runs its compile-only lanes on its own `libs.versions.toml` until F5 exists, then adopts F5 (P-LX build-entry lists F5; this is a deviation for the owner to accept). |
| F6 platform-ports | would consume, not yet | Encryption at rest for a persistent ledger adapter (Keystore, Keychain, libsecret, DPAPI). No persistent adapter exists; gated on OQ-22. |
| F7 ubuntu-touch-shell | UT routes (1) and (2) only | S-UT1 decides whether route (1) is open. |
| F8 native-engines | not consumed | No inference. |
| F9 CI matrix template | consumes later | Replaces the inline lanes with SHA-pinned `uses:` once it exists (I-6). |
| F10 packaging templates | not consumed | No app to package; Gradle builds the XCFramework. |
| F11 evidence scheme | consumes the labels | The §4 table is this repo's evidence table. Device checklists belong to consumers. F11 adds a `platform-evidence/1` record to `evidence-schema`; keep it apart from Baseline's evidence records, since "evidence" then names two unrelated things in one module. |
| F12 web/brand kit | not consumed | No web surface. |

**`evidence-schema` (Shared-Libraries-asoc), read 2026-10-06.** It describes itself as the portable JSON schema
for evidence exchange across Baseline, Crocodyl and Ebbflow. It does not match `Evidence.kt` today: `source`
in the schema against `provenance` in Kotlin; ISO-8601 date-time strings against epoch-millisecond `Long`
fields; `clockUncertaintyMs` exists only in the schema; `measurement` is an untyped object; and the
cross-field invariants (a CLAIM needs a grade, a RETRACTION needs `retractsId`, RAW has no `derivedFromIds`)
are not expressible in it as written. Its only test checks that certain tokens appear in the JSON text, so
nothing enforces lockstep. Which one is canonical, and where a conformance test lives, is §8 Q9.

**Provides.** (a) Library artefacts for consumers: the JVM jars today; an Apple XCFramework and an optional
`linuxArm64` static library after §6. (b) The five tests as the conformance bar for any persistent
`EvidenceLedger` adapter ("must preserve these semantics"). Extracting them into a reusable test kit, like
Shared-Libraries' `search-testkit`, is worth proposing once the first adapter is written. (c) Observation,
not in program §7: with no UI, no Android SDK and no composite consumers, the conversion is the lowest-risk
place to exercise the headless-library Apple lane before a UI app does.

## 8. Open questions for the owner

This repo has no decision register beyond the numbered "Key decisions locked" list in
`docs/session_handoff.md`. Nothing here is ruled; a proposal sits inside the question that needs it, and this
change appends nothing there. Items marked *new* were found while writing this plan. Master ids refer to
`Personal-Tracker/PORTING_PROGRAM.md` §8.

1. **Licence (OQ-12).** No `LICENSE` file exists. README says Community is open and Advanced is a separate
   proprietary layer; PT:D-S and `NAMES.md` call this repo "proprietary / closed". Which stands for the code
   here? Blocks: any published coordinate, released XCFramework or static library; SPDX headers; recording
   third-party dependencies; and, on UT, whether closed code is limited to common groups or sideload.
2. **Consumption model (named as a gate in the program row; no OQ id).** `ARCHITECTURE_V2.md` already says
   "embedded Community library for phone-only operation" with an optional personal node. Should Crocodyl
   and Ebbflow ports embed this library on every target, with a LAN node only as an option, or talk to a
   node (`research_brief.md` §8.4)? Proposal: embedded primary, node optional, matching the deployment law.
   Blocks: whether and when the K-steps run; the UT route; any node API work.
3. **Sharing mechanism and names (OQ-24, OQ-25, OQ-17 option C; *new* conflict).** PT:D-A says submodule plus
   `includeBuild`; PT:D-S says open apps consume Baseline as a licensed binary, not vendored source. If the
   code is proprietary these conflict. Which group id and package: `dev.baseline` (code today),
   `dev.aarso` (Shared-Libraries convention) or `xyz.mdhv.baseline` (`NAMES.md`)? Blocks: L3, the
   XCFramework and module names, any C symbol prefix.
4. **v0.2 scope and Phase 0 (profile).** Are the Python daemon, Home Assistant and VLC controllers, the React
   probe app and the seven scaffold directories still live scope, or fully superseded by `ARCHITECTURE_V2.md`
   and PT:D-S (EEG app moves to Ebbflow, not started)? Does `build_plan.md`'s phase gating (Phase 0 on the
   Dell, still "not started" in `docs/session_handoff.md`) still govern library work? Blocks: nothing in
   this plan, which leaves those directories alone; it decides whether "library only" is final.
5. **Ubuntu Touch consumer and device (OQ-1, OQ-21).** Is Ebbflow or Crocodyl actually targeted for UT? If
   neither, or if Waydroid is accepted for them, skip all of UT. Blocks: §6 UT0 to UT3 (2w).
6. **Personal node beyond Linux (OQ-5).** Will a node ever run on macOS or Windows, or only the Linux Dell?
   Blocks: whether JVM packaging for those OSes matters beyond library tests.
7. **Thread-safety contract and dependencies (profile; *new* detail).** Is `InMemoryEvidenceLedger` meant to
   be thread-safe on every target? Choose K2 (A), (B) or (C); approve `kotlin-test` and, for (A),
   `kotlinx-atomicfu`. `build_plan.md` makes dependency choices the implementer's call with a one-line
   reason; this is listed because OQ-12 decides what must be recorded. Blocks: K2, K3.
8. **Swift and C surface (*new*).** Add `@Throws` or non-throwing factories to the public API? Additive, but
   it touches the stable contract. Blocks: K5, and a usable Swift or C consumer.
9. **Which is canonical, `Evidence.kt` or `evidence-schema` (*new*; Shared-Libraries owns the schema)?** Where
   does a conformance test live, given a test in this repo reading Shared-Libraries couples them one way
   and the reverse couples a neutral library to this repo? Proposal: `Evidence.kt` canonical, because the
   invariants live there. Blocks: UT route (3), any non-JVM consumer exchanging records, F11's addition to
   the same schema.
10. **Toolchain pin (OQ-17; *new* link).** Baseline is outside the Hyle AGP lockstep, but Kotlin 2.1.0 may be
    too old for the Xcode on the hosted macOS image (unverified). Blocks: I2 and M2; adopting F5's catalog.
11. **CI cost for a private repo (OQ-20).** macOS runners bill ten times on private repos; the account's
    artifact storage is exhausted. Make the repo public, fund minutes, or keep Apple and Windows lanes on
    manual dispatch? Blocks: PR-time Apple and Windows lanes; arm64 runner availability.
12. **Encryption at rest for a persistent adapter (OQ-22; profile).** Which custody tier per platform
    (Keystore, Keychain, libsecret, DPAPI, UT app-private file) and what the UI says about the weaker ones.
    Blocks: any persistent `EvidenceLedger` adapter on any platform; nothing in this plan.

## 9. Sources read

Repo files this plan was derived from (2026-10-06): `README.md` · `ARCHITECTURE_V2.md` · `build_plan.md` ·
`research_brief.md` (§3.2, §3.6, §7.5, §8) · `docs/PR_DISPOSITION.md` · `docs/session_handoff.md` ·
`docs/streamer_port_plan.md` (header only) · `docs/phase_0_runbook.md` · `settings.gradle.kts` · `build.gradle.kts` · `gradle.properties` ·
`gradle/libs.versions.toml` · `.github/workflows/ci.yml` · `.github/workflows/cleanup-artifacts.yml` ·
`.gitignore` · `baseline-contracts/build.gradle.kts` ·
`baseline-contracts/src/main/kotlin/dev/baseline/contracts/Evidence.kt` ·
`baseline-contracts/src/test/kotlin/dev/baseline/contracts/EvidenceTest.kt` · `baseline-ledger/build.gradle.kts` ·
`baseline-ledger/src/main/kotlin/dev/baseline/ledger/EvidenceLedger.kt` ·
`baseline-ledger/src/test/kotlin/dev/baseline/ledger/EvidenceLedgerTest.kt` · `baseline-stats/build.gradle.kts` ·
`baseline-stats/src/main/kotlin/dev/baseline/stats/Regression.kt` ·
`baseline-stats/src/test/kotlin/dev/baseline/stats/HydrationConfoundingAcceptanceTest.kt`.
`docs/phase_0_runbook.md`, the body of `docs/streamer_port_plan.md` and the rest of `research_brief.md` were read
by the profile that preceded this plan and were not re-read for it; nothing here depends on them.

Outside this repo: `Personal-Tracker/PORTING_PROGRAM.md` (§0 to §3, §4, this repo's §5 row, §6, §7, §8) ·
`Personal-Tracker/DECISIONS.md` (D-A, D-Q, D-S, D-T) · `Personal-Tracker/NAMES.md` ·
`Personal-Tracker/porting/platforms/{linux,ios,macos,framework-strategy}.md` (Kotlin/Native target tiers) ·
`Shared-Libraries-asoc/README.md`, `settings.gradle.kts`, `evidence-schema/` (schemas and test).

Measured with: `find . -name '*.kt' -not -path './.git/*' | xargs wc -l`; `grep -rn '@Test' --include=*.kt .`;
`git ls-files | wc -l`; `git ls-files | grep -P '[:<>|?*"]'`; a grep of production sources for
`System.|Random|Locale|java.|javax.|Instant|Clock|File|Thread|Charset|format(`; a grep for `dev.baseline` in
Ebbflow, Form-analyser and Shared-Libraries-asoc. Nothing was built and no Gradle task was run.

## Owner rulings and the proposed line (added 2026-10-07)

Status: PLAN. Nothing here is built, run on a device, signed or submitted. The program-level plan is Personal-Tracker `PORTING_PROGRAM.md` ([PR #10](https://github.com/mbaliga/Personal-Tracker/pull/10)), which holds the owner's rulings and section 5A, the proposed port / no-port line. The cells, estimates and open questions above are this repo's original plan and are unedited. Where the owner has since answered a question, the answer is below. Section 5A is a proposal; the owner has not yet confirmed it.

### Where baseline sits in the proposed line (program section 5A.3, a proposal)

| Target       | Verdict | Weeks and flags |
| ------------ | ------- | --------------- |
| Ubuntu Touch | no-port | -               |
| Linux        | exists  | 0.5w            |
| iOS/iPadOS   | follows | 1.5w            |
| macOS        | exists  | 0.5w            |
| Windows      | exists  | 0.5w            |

Key: `follows` means it ports only as far as the products that depend on it; `exists` means the program reads it as already running there, unverified (finish, verify and sign); flags: `g` gated on a prerequisite, `r` re-estimate or floor, `o` its own program, `s` scope note. The program's P4, P8, P12 and P13 gate whole columns or repos and are not flagged per cell. A port verdict counts the deliverable in the line; where this repo's plan calls a deliverable a reframe (program rule R12) it keeps that label. Tests cited in the reason: (a) the owner said it is needed there; (b) its job is really done on that OS by real users; (c) that OS is where it is sold or its audience is; it has no reason to exist if (x) its surface is absent or untouchable, (y) the capability is forbidden or impossible, or (z) the only form is a thin wrapper or a different product nobody asked for. P-numbers and OQ-numbers refer to the program plan (Personal-Tracker `PORTING_PROGRAM.md`, sections 5A.5 and 8).

Reason: An engine with no UI and no job of its own: it follows its consumers (Crocodyl iOS) and builds on the JVM where it already does. A linuxArm64 library on UT would serve no one.

### Owner rulings that apply here

- **OQ-17 toolchain (2026-10-06):** "B: staged pin (Recommended)": Kotlin 2.1.20 and Compose Multiplatform 1.8.2 for the first wave, 2.4.x deferred. baseline is on Kotlin 2.1.0 and uses no Compose, so only the Kotlin part of the pin applies and the move to 2.1.20 is a bump the plan leaves to OQ-17 (step K1); it is outside the PT:D-Q Hyle lockstep.
- **OQ-38 iOS pin (2026-10-07):** "Move iOS to Kotlin 2.2.21 + CMP 1.9.3 (Recommended)", with the first iOS proof on an explicitly selected Xcode 26.x. The program plan reads this as moving a repo that ships an iOS target as a whole (a Gradle build has one Kotlin version); that reading is not researched, and the bump cost for repos pinned lower is in no figure. baseline's Kotlin 2.1.0 is below the iOS pin; the bump cost is in no figure. The Compose Multiplatform half of the pin does not apply: baseline has no UI.
- **OQ-20 CI (2026-10-06):** "Linux-only CI when private (Recommended)": this repo is private, so macOS and Windows lanes do not run while it stays private (an owner Mac is a third route for Apple builds).
- **OQ-31 Mac (2026-10-06 and 2026-10-07):** "Buy a Mac", and on 2026-10-07 an Apple-silicon Mac mini, not yet bought; no Apple device gate is called checkable before then.
- **OQ-5 hardware (2026-10-06):** the owner has a Dell, a Windows desktop and an HP laptop; the Steam Deck is no longer a dependency for now. Which machine carries which device gate is not decided (OQ-33). The plan's note that the Dell is slated for Linux is superseded; whether the Dell homelab plan stands, which names baseline, is open under OQ-33.
- **Directives (2026-10-06):** "Draft amendments for approval": program directives I-1 to I-12 and rules R1 to R12 are unchanged; PROPOSED-1 to PROPOSED-4 in Personal-Tracker `DECISIONS.md` are drafts awaiting the owner.

### Prerequisites and open questions that touch this repo (program sections 5A.5 and 8)

Prerequisites (program-level; not costed here):

- program P2: Crocodyl's restructure into :core, :ui and :desktop with expect/actual seams (about 6 weeks, carried by Crocodyl Linux); the program lists Baseline iOS as waiting on it
- program P8: An Apple-silicon Mac (OQ-31: a Mac mini chosen on 2026-10-07, not yet bought)
- program P12: The iOS toolchain pin (OQ-38, ruled 2026-10-07): baseline's Kotlin 2.1.0 is below the iOS pin, Kotlin 2.2.21 (the Compose Multiplatform half does not apply); the bump cost is in no figure
- program P13: OQ-12: the README's Community and Advanced split against PT:D-S 'proprietary'

Owner questions in the program register that concern this repo (status as of 2026-10-07):

- OQ-5 (ruled): Hardware stance (names baseline in the Dell homelab-server plan; plan Q6)
- OQ-12 (open): Licences for repos without a LICENSE
- OQ-17 (ruled): Toolchain pins: the pin is ruled; the "Also" approvals (converting shared modules to kotlin("multiplatform"), asom's no-KMP rule staying asom-local) are unanswered
- OQ-20 (ruled): CI minutes, storage and repo visibility
- OQ-24 (open): Sharing mechanism for non-Gradle artefacts and prebuilt binaries
- OQ-25 (open): Identifier registry
- OQ-31 (ruled): CI for App Store builds; which Mac
- OQ-33 (open): Hardware details still open
- OQ-38 (ruled): iOS toolchain pin

When the owner confirms or changes the line, this repo's original cells above stay as the engineering detail; only the verdicts and re-costs in program section 5A change.

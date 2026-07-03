# CROCODYL — Phase 1 Build Brief: **App Shell**

**Executor:** Claude Code, on repo `Form-analyser` (baseline `91d53ba`).
**Parent document:** `CROCODYL_FEATURE_SPEC.md` rev 1.2 — governing sections §3.1 (onboarding), §3.2 (Home), §7 (settings), §8 (data model), §10 (Hyle law), §11 row 1.
**Model routing:** designed for Sonnet-class iterative sessions; no architecture decisions are left open below.

---

## 0. Mission & non-goals

**Mission.** Turn the working analyzer pipeline into an app: onboarding, a real Home, 5-tab navigation, a settings skeleton, rig v0 (equipment stub that sessions reference), and **left-handed support** via pose mirroring — all CI-green with the pipeline's math untouched.

**Explicit non-goals (do not build, even where adjacent code invites it):** identity keypairs / Pairing Card / QR (Phase 5) · coach anything (Phase 6) · check-ins, streak, readiness, calendar content (Phase 2) · body map (3) · full equipment inventory, tuning UI beyond the two poundage fields (4) · score entry (7) · weather/venues (8) · Clover (9) · AI (13) · notifications of any kind · any network call or `INTERNET` permission.

Because identity and coach-pairing are deferred, the onboarding built here is **deliberately a subset of spec §3.1**: steps 5 (identity) and 7 (coach QR) are omitted; Phase 5 adds them with a one-time "your identity was created" migration moment.

---

## 1. Ground rules

1. **Guardrails — never modify:** `engine/` math; `archery-module` feature definitions, segmenter logic, or thresholds (you will *add* a normalizer in front of them, changing nothing behind it); the MediaPipe/CameraX integration internals; CI semantics beyond *adding* a test step.
2. **Additive-only migrations.** Never drop or rename existing columns; deprecate in code comments instead.
3. **Hyle law (spec §10):** all colors via existing token contract — zero hardcoded hex in new code; red/green never semantic; touch targets ≥ 48 dp; every string in resources; motion ~300 ms cubic-bezier, collapsing to 0 ms when reduce-motion is on.
4. **Branch & commits:** work on `phase-1/app-shell`. Conventional commits (`feat:`, `refactor:`, `test:`, `chore:`), each compiling, small. Open a PR titled `Phase 1 — App Shell` at the end; do not merge it yourself.
5. **`PHASE1_NOTES.md` (repo root):** running log of recon findings, every deviation from this brief with rationale, and open questions for Madhav. This file is a deliverable.
6. **Adaptation rules:**
   - **R1 — incumbent first.** If the repo already has a DI framework, nav library, string/color conventions, or naming style, extend it; do not replace it.
   - **R2 — nearest faithful.** If a factual assumption in this brief is wrong (e.g., a column that doesn't exist), implement the closest faithful alternative and log it in NOTES.
   - **R3 — no scope creep** into later phases.
   - **R4 — timebox blockers.** Stuck > 20 min on tooling/environment: stub behind an interface, note it, move on.

---

## 2. Step 0 — Reconnaissance (read-only, ~30 min, do this first)

Produce the findings as the first section of `PHASE1_NOTES.md`:

1. **Branch topology.** The status baseline says PR #1 (app) and PR #5 (sensor channel) are drafts and the default branch may not yet be `main`. Determine which branch actually contains `app-android` at `91d53ba`. **If the app lives on the unmerged PR #1 branch:** merge PR #1 into `main` first (squash), set `main` as default (`gh repo edit --default-branch main` if permissions allow), then branch `phase-1/app-shell` from it. If merge is not possible, branch from the PR #1 head and say so in NOTES. Never base Phase 1 on PR #5.
2. **Module & package map.** Actual Gradle modules, package roots, where Capture/Review/session-setup composables live, what Hyle components already exist (inventory them by name).
3. **DI incumbency.** If Hilt/Koin/manual-locator exists → R1. If **none exists → adopt Hilt now** (`com.google.dagger:hilt-android` + KSP, `@HiltAndroidApp`, `@HiltViewModel`, a `DatabaseModule` + `RepositoryModule`). Match the repo's Kotlin/AGP versions from the version catalog.
4. **Room schema.** Current `@Database` version **V**, entity/column names and their naming convention (snake vs camel), whether `session` carries a draw-weight and distance column (the old setup screen implies yes), whether `exportSchema` is on. Enable `exportSchema = true` and commit `app-android/schemas/` if absent.
5. **Pose representation.** What coordinate space feeds `FormFeatureExtractor`: MediaPipe **normalized image landmarks** ([0,1], y-down), **world landmarks** (meters, hip-origin), or pixels — and whether visibility/presence scores ride along. This decides the mirror transform (§4).
6. **Capture flow entry.** How the current session-setup → capture → review flow is wired (single activity? nav library present?), and whether raw video is retained after analysis.
7. **CI workflow.** Which Gradle tasks run; confirm unit tests for `engine`/`archery-module` are in it.

---

## 3. Workstream A — Data layer

### A1. Schema migration V → V+1 (one migration, manual `Migration` object)

Adapt column-name style to incumbent convention; semantics below are binding.

```sql
ALTER TABLE athlete ADD COLUMN handedness TEXT NOT NULL DEFAULT 'RH';
ALTER TABLE athlete ADD COLUMN draw_length_mm INTEGER;            -- nullable
ALTER TABLE athlete ADD COLUMN avatar_seed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE athlete ADD COLUMN club TEXT;                         -- nullable
ALTER TABLE athlete ADD COLUMN pubkey TEXT;                       -- nullable, Phase 5 fills

CREATE TABLE rig (
  id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  athlete_id  INTEGER NOT NULL REFERENCES athlete(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  bow_type    TEXT NOT NULL,          -- 'RECURVE' | 'COMPOUND' | 'BAREBOW'
  tuning_json TEXT,                   -- versioned JSON, see A3
  active      INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL
);
CREATE INDEX index_rig_athlete_id ON rig(athlete_id);
-- optional hardening (SQLite partial index): at most one active rig per athlete
CREATE UNIQUE INDEX index_rig_one_active ON rig(athlete_id) WHERE active = 1;

ALTER TABLE session ADD COLUMN rig_id INTEGER;                    -- nullable FK
ALTER TABLE session ADD COLUMN handedness_override TEXT;          -- nullable 'RH'|'LH'
CREATE INDEX index_session_rig_id ON session(rig_id);
```

**Backfill (inside the same migration):** for every existing athlete, insert one rig `name='My bow'`, `bow_type='RECURVE'`, `active=1`, `tuning_json` seeded from the athlete's **most recent session's draw weight** if that legacy column exists (→ `{"v":0,"markedLbs":<value>}`), else null; set every existing `session.rig_id` to that rig; backfill `avatar_seed` with a random non-zero Long per athlete.

**Keep the legacy session draw-weight column** and keep writing it (from the rig's effective poundage) wherever the current code reads it — mark it `@Deprecated` in the entity. Phase 2's load model will switch to the rig; nothing breaks meanwhile.

### A2. Entities, DAOs, repositories

- `Handedness { RH, LH }`, `BowType { RECURVE, COMPOUND, BAREBOW }` — stored as strings via `TypeConverter` or plain `String` columns + mappers, matching incumbent style.
- `RigDao`: `observeForAthlete`, `observeActive`, `upsert`, `setActive(rigId)` **as a `@Transaction`** (clear siblings, set target — this transaction is the primary single-active guarantee; the partial index is backstop), `delete` (repository refuses to delete the active or the last rig — caller must activate another first).
- `RigRepository`, `AthleteRepository` (profile field updates), exposed as `Flow`s.
- **Effective handedness resolver** (pure function, `core`-adjacent or `archery-module`): `effective = session.handednessOverride ?: athlete.handedness`.

### A3. Tuning JSON v0 (kotlinx.serialization; add the plugin/library if absent)

```kotlin
@Serializable data class TuningV0(
  val v: Int = 0,
  val markedLbs: Double? = null,     // rated poundage printed on the limbs
  val riserLengthIn: Double? = null, // 23 / 25 / 27 typical (ILF); null for compound
  val otfLbs: Double? = null         // MEASURED on the fingers at the athlete's draw length
)
// kg conversion lives with the math (Phase 2): lbs * 0.45359237
```
Unknown-field-tolerant decoding (`ignoreUnknownKeys = true`) — Phase 4 extends this schema in place.

### A3.1 On-the-fingers poundage estimator (recurve/barebow only)

Marked limb poundage is a **rating, not a measurement**: by ILF convention it is specified at **28″ AMO draw on a 25″ riser**. What the archer actually holds scales with both. Implement as a pure function (`domain/PoundageEstimator.kt` in `app-android` for now; Phase 4 relocates it into `core-equipment`):

```kotlin
// Convention defaults — named constants, KDoc'd as conventions, all four inputs required.
// DECISION-21: constants to be confirmed/adjusted by Madhav from coaching ground truth.
const val K_DRAW_LBS_PER_IN  = 2.0   // per inch of draw beyond/below 28" AMO
const val K_RISER_LBS_PER_IN = 1.0   // per inch of riser below/above 25"

fun estimateOtfLbs(markedLbs: Double, riserLengthIn: Double, drawLengthMm: Int): Double {
  val drawIn = drawLengthMm / 25.4
  return markedLbs +
         K_DRAW_LBS_PER_IN  * (drawIn - 28.0) +
         K_RISER_LBS_PER_IN * (25.0 - riserLengthIn)
}
```

**Precedence, everywhere poundage is consumed** (legacy draw-weight write, rig summaries, Phase 2 load math):
`measured otfLbs` → `estimateOtfLbs(...)` (only when marked + riser + athlete draw length are all present, bow type ≠ COMPOUND) → `markedLbs`.
UI rule: estimates are always rendered with a leading `≈` and the caveat copy (Appendix C) — limb-bolt position shifts real OTF by up to ±10%, which the estimator ignores and admits. **Compound bows never estimate** — cam systems don't scale per-inch; measured or marked only.

---

## 4. Workstream B — Handedness normalizer (archery-module, pure JVM)

**Design intent:** exactly **one** normalization point in the pipeline. Immediately after landmark extraction and before *anything* consumes the pose — `PoseShotSegmenter`, `FormFeatureExtractor`, and the Capture screen's live bow-arm-angle readout — the pose passes through `normalize(pose, effectiveHandedness)`. For `RH` it is identity. For `LH` it mirrors into the canonical right-handed frame. Downstream code stays byte-identical; post-normalization, "draw wrist" is always the right wrist.

### B1. `HandednessNormalizer` (pure function + swap table)

BlazePose 33-landmark left↔right swap pairs (index 0 `nose` is fixed):

```
(1,4) (2,5) (3,6)   eyes inner/center/outer     (17,18) pinky
(7,8)               ears                        (19,20) index
(9,10)              mouth                       (21,22) thumb
(11,12)             shoulders                   (23,24) hips
(13,14)             elbows                      (25,26) knees
(15,16)             wrists                      (27,28) ankles
                                                (29,30) heels
                                                (31,32) foot index
```

Coordinate transform — pick per recon finding §2.5:

| Pose space | Mirror transform |
|---|---|
| Normalized image ([0,1], y-down) | `x' = 1 − x`, `y' = y`, `z' = z` |
| World landmarks (m, hip origin) | `x' = −x`, `y' = y`, `z' = z` |
| Pixels (width W) | `x' = (W − 1) − x`, `y' = y` |

Visibility/presence scores travel **with the swapped index**. Depth is unchanged by a horizontal flip in all three spaces.

### B2. Unit tests (JVM, in `archery-module`)

1. **Swap-table correctness:** every pair swaps; nose stays; a pose with distinguishable per-landmark payloads round-trips index-correctly.
2. **Involution:** `mirror(mirror(p)) == p` exactly.
3. **Non-identity:** on an asymmetric fixture, `mirror(p) != p`.
4. **Feature invariance (the exit-criterion test):** take an RH fixture *sequence* (reuse existing test fixtures if recon finds them; else synthesize a plausible draw→anchor→release sequence), construct its LH twin by applying the mirror transform, then run the **full** segmenter + extractor on both `(RH raw)` and `(normalize(LH twin, LH))`. Assert: all 8 features equal within `1e-6`, and segmentation phase timestamps identical.
5. **Resolver:** `handednessOverride` beats athlete default; null falls through.

### B3. Wiring (app-android)

Single call site injected into the capture pipeline; a code-inspection checklist item in NOTES confirms the live readout consumes the normalized pose. Session setup exposes the per-session override (see §6, Train setup).

---

## 5. Workstream C — Navigation scaffold & app structure

- **Package restructure** (no Gradle module split — spec §9 keeps `app-android` monolithic): `feature/onboarding`, `feature/home`, `feature/train` (absorbs existing setup/capture/review composables via `git mv`-style moves), `feature/settings`, plus `core/ui` if a shared-Hyle-components home doesn't already exist. Preserve incumbent package root.
- **Navigation:** `navigation-compose`, single activity, **type-safe routes** (kotlinx-serialization route objects if the repo's nav version ≥ 2.8; string routes otherwise — R1). Route table:

```
Onboarding                          Settings
Main (bottom-bar shell)             ├─ SettingsProfile
 ├─ Home                            ├─ SettingsRigs
 ├─ Train → TrainSetup              │   └─ RigEdit(rigId: Long?)   // null = create
 │          → Capture               ├─ SettingsCapture
 │          → Review(sessionId)     ├─ SettingsAppearance
 ├─ Progress   (stub)               ├─ SettingsData
 ├─ Body       (stub)               └─ SettingsAbout
 └─ Calendar   (stub)
```

- **Start destination:** `onboarded` flag false → `Onboarding`, else `Main/Home`.
- Bottom bar: 5 items (Home · Train · Progress · Body · Calendar), icons from the existing icon set; **hidden during Capture** (immersive) and Onboarding. Settings gear in the top bar of Main screens.
- **Stub tabs:** one shared `ComingSoonScreen(tab)` — Hyle empty state, icon + the two-line copy from Appendix D. No dead buttons.
- **DataStore (Preferences)** module for app prefs; keys in Appendix C. Athlete profile fields live in Room (single source of truth); settings screens edit the athlete row directly.

---

## 6. Workstream D — Onboarding flow

`OnboardingViewModel` holds all step state (`SavedStateHandle`-backed for process death); **nothing writes to Room until the final step commits atomically** (athlete row upsert + rig insert + `onboarded=true`). Back navigation allowed; progress dots; each screen one decision.

| # | Step | Fields & rules |
|---|---|---|
| 1 | **Role** | Single-select cards: Archer / Coach / Both → DataStore `role`. Selecting Coach/Both shows the inline note from Appendix D (coach workspace lands in a later build) and proceeds through the same archer onboarding. |
| 2 | **Name & avatar** | Name required, 1–40 chars, trimmed. `avatar_seed = Random.nextLong()` with a shuffle button; live `HyleAvatar` preview (§9). |
| 3 | **Handedness** | Segmented control, default RH, with the disambiguating copy (Appendix D — *draw hand*, not writing hand) and mirrored archer glyphs. |
| 4 | **Draw length** | Stepper, mm, range 500–900, step 5, default 700; helper points at a real measurement, not a folk formula (copy in Appendix C). Skippable → null. |
| 5 | **Rig v0** | Name (default "My bow"), bow type segmented (Recurve/Compound/Barebow). For recurve/barebow: **riser length** (segmented 23″ / 25″ / 27″ / Other → stepper 17–29″, skippable) and marked lbs (stepper 10–80, step 0.5, skippable). OTF lbs (measured; same range, skippable). When marked + riser + draw length are all known and OTF is empty, the **estimated OTF** renders as live helper text under the field (§A3.1). Compound: marked peak weight + measured OTF only — the estimator never applies. Creates the rig `active=1`. |
| 6 | **Permissions primer** | Static explainer: camera is requested when you first enter Train; notifications come later and default off. Single Continue. **No runtime permission request here** — camera is requested contextually on first Capture entry. |
| 7 | **Done** | Commit + route to Home. |

Validation errors inline, never blocking toasts. Rotation/process-death mid-flow resumes the same step with state intact (test this).

---

## 7. Workstream E — Home v1

**Card contract (build the registry now; later phases only append):**

```kotlin
sealed interface HomeCard {
  data class Header(val name: String, val avatarSeed: Long) : HomeCard
  data class ActiveRig(val rig: Rig) : HomeCard          // tap → rig switch sheet
  data object StartSession : HomeCard                     // primary CTA → TrainSetup
  data class StabilitySpark(val points: List<Float>) : HomeCard  // last ≤10 sessions
  data class RecentSessions(val items: List<SessionRow>) : HomeCard // tap → Review
}
```

`HomeViewModel` assembles `List<HomeCard>` from Flows; a `LazyColumn` renders via a `when`-registry composable. Rules: `StabilitySpark` hidden until ≥ 2 sessions exist; `RecentSessions` shows its friendly empty state at zero sessions (copy, Appendix D); ordering exactly as listed. Stability values come from the **existing** deviation/stability engine output — read, never recompute.

**Train setup changes (feature/train):** the old setup screen's draw-weight field is **removed**; in its place, the active-rig chip (tap → switcher bottom sheet listing rigs + "manage" deep-link into `SettingsRigs`). Distance stays a session field, unchanged. Add a collapsed "Advanced" row: per-session handedness override (Default / RH / LH → `session.handedness_override`). On session create, also write the legacy draw-weight column from `rig.effectivePoundageLbs` (§A1 compatibility rule).

---

## 8. Workstream F — Rig v0 UI

- `SettingsRigs`: list (name, bow type, poundage summary — measured values plain, estimates prefixed `≈`, ACTIVE badge as **shape/label, not color alone**), add button, swipe-free explicit Edit.
- `RigEdit(rigId?)`: same fields as onboarding step 5 + Delete. Delete rules surfaced honestly: deleting the active rig requires activating another first; the last rig can't be deleted (dialog copy in Appendix D). Activation via the `@Transaction` from §A2.
- Rig switch bottom sheet (shared component) used by Train setup and Home's `ActiveRig` card.

---

## 9. Workstream G — Settings skeleton  ·  Workstream H — Hyle atoms

**Settings sections shipped functional in Phase 1** (ordering mirrors spec §7 so later phases slot in without reshuffling):

- **Profile & Identity:** name, avatar (preview + shuffle), handedness, club, draw length. (Pairing/Flex Card entries do not appear yet.)
- **Equipment:** → `SettingsRigs` (§8).
- **Capture:** `keep_raw_video` toggle (default OFF). Recon §2.6 decides wiring: if the pipeline currently retains video, the toggle governs deletion post-analysis; if it already discards, the toggle stores the pref and the label says it applies to future builds — log which in NOTES.
- **Appearance:** reduce motion (honors system setting; app toggle ORs with it — plumb into the Hyle motion spec so animation specs collapse to 0 ms), haptic strength (Off/Low/Med/High → every log/confirm haptic in new code routes through one `HyleHaptics` helper), glow intensity (0–100% multiplier on the provenance-glow alpha).
- **Data:** **Wipe everything** — two-step confirm (dialog, then type `WIPE`), clears Room + DataStore + app files, process restarts into onboarding.
- **About:** version + build hash, licenses (use an existing plugin if present, else a static screen listing direct deps), privacy statement v0 (Appendix D — and it is *true* for this build: zero network calls), repo link.

**Hyle atoms (Workstream H):** inventory first (recon §2.2), reuse everything; add only what's missing, in the shared components package, tokens-only: `HyleSegmentedControl`, `HyleStepper` (long-press repeat, 48 dp targets), `HyleListRow`, `HyleSectionHeader`, `HyleBottomSheet`, `HyleEmptyState`, `HyleAvatar`.

**`HyleAvatar(seed, modifier)` — deterministic, pure:** splitmix64 stream from the seed → gradient angle (0–359°), three color stops sampled at distinct luminance steps along the violet↔cyan lane, 0–2 concentric ring overlays. Same seed ⇒ same render (unit-test the parameter derivation). This is the v0; the AGSL Flex Card replaces the renderer in Phase 11 behind the same function signature.

---

## 10. Test plan

### 10.1 JVM unit tests (CI-gating)

**`archery-module`** — the five tests from §B2, named:
```
HandednessNormalizerTest.swapTableIsCorrectAndComplete
HandednessNormalizerTest.mirrorIsAnInvolution
HandednessNormalizerTest.mirrorIsNotIdentityOnAsymmetricPose
HandednessNormalizerTest.effectiveHandednessResolverPrecedence
FeatureInvarianceTest.lhTwinYieldsIdenticalFeaturesAndPhases   // exit criterion
```

**`app-android` (Robolectric — add `org.robolectric:robolectric` + `androidx.test:core-ktx` as test deps; Room runs fine under it):**
```
MigrationVtoV1Test.legacyDataSurvives            // build V db with fixture athlete +
                                                 // sessions incl. legacy draw weight →
                                                 // run migration → assert new columns,
                                                 // default rig created active=1 with
                                                 // markedLbs backfilled, session.rig_id set
RigRepositoryTest.setActiveIsExclusiveTransactionally
RigRepositoryTest.cannotDeleteActiveOrLastRig
OnboardingViewModelTest.stateMachineValidationAndAtomicCommit
OnboardingViewModelTest.survivesProcessDeathMidFlow          // SavedStateHandle round-trip
HomeViewModelTest.cardAssemblyOrderingAndEmptyStates
HyleAvatarTest.sameSeedSameParams
SettingsPrefsTest.dataStoreRoundTripAllKeys
PoundageEstimatorTest.ilfConventionDefaults          // 32# @ 23" riser, 29.2" draw ⇒ 36.4
PoundageEstimatorTest.precedenceMeasuredOverEstimatedOverMarked
PoundageEstimatorTest.compoundNeverEstimates
```

Compose UI smoke tests (Robolectric + `compose-ui-test`): **attempt** one onboarding step-through and one bottom-bar navigation smoke; timebox 45 min combined — if flaky under Robolectric in this repo's setup, drop them and note it (R4). They are not exit criteria.

### 10.2 CI

Extend the existing workflow with `:app-android:testDebugUnitTest` alongside the current `engine`/`archery-module` test tasks and the `assembleDebug` step. Do not restructure the workflow otherwise. Green CI on the PR = gate.

### 10.3 Manual QA script (run on the RedMagic; results pasted into PHASE1_NOTES.md)

1. Uninstall any previous build; install fresh `app-debug.apk`.
2. Onboarding appears. Complete as **LH**, name set, shuffle avatar twice (renders change deterministically per seed), draw length 715, rig "Practice recurve", riser 25″, marked 32, OTF skipped — the ≈ estimate appears under the OTF field.
3. Land on Home: header shows name + avatar; active-rig chip reads "Practice recurve · 32 lbs"; Start Session CTA present; sessions list shows empty state; no sparkline.
4. Kill the app; relaunch → straight to Home (no onboarding re-run).
5. Progress / Body / Calendar tabs each show their ComingSoon state; no crashes, back behaves.
6. Train → camera permission requested **here** (contextual); grant.
7. Setup shows rig chip (no draw-weight field), distance field, Advanced → handedness override present, leave Default.
8. Record ~15 s of shadow draws (no bow needed) → stop → Review renders without crash (feature values may be garbage; the pipeline exercising is the point). Live bow-arm readout visibly tracked during recording.
9. Back on Home: session appears in Recent; tap → Review reopens.
10. Settings → Profile: change club, flip handedness to RH and back to LH; values persist across relaunch.
11. Settings → Equipment: add second rig, activate it, confirm Home chip updates; try deleting it while active → blocked with the explanatory dialog; activate the first, delete the second → succeeds.
12. Appearance: enable reduce motion → tab transitions and sheet animations snap; set haptics Off → rig activation produces no tick.
13. Data → Wipe: type-to-confirm; app restarts into onboarding.
14. Re-onboard as **RH**, record another shadow session — sanity that RH path is unchanged.

Step 8 across both 2 and 14 is the on-device half of the "fresh-install → session E2E" exit criterion; the LH *correctness* half is the JVM invariance test.

---

## 11. Definition of done

- [ ] `./gradlew :engine:test :archery-module:test :app-android:testDebugUnitTest :app-android:assembleDebug` green locally **and** in CI on the PR.
- [ ] All named tests in §10.1 exist and pass (Compose smokes optional per timebox).
- [ ] `git diff main -- engine/ archery-module/src/main` shows **only** the new normalizer + resolver files in `archery-module`; zero edits to existing feature/segmenter sources.
- [ ] Migration test proves V-era data survives with correct backfill.
- [ ] Manual QA script executed on-device; all 14 steps pass; results in NOTES.
- [ ] `PHASE1_NOTES.md` contains: recon findings (§2, all seven), deviations log, the capture-video wiring decision (§9), open questions.
- [ ] No new permissions beyond CAMERA (already present) — verify the merged manifest.
- [ ] PR `Phase 1 — App Shell` open against `main` with a summary that links spec sections.

---

## 12. Sequencing — three sessions, suggested commit plan

**Session 1 (headless, ends CI-green):**
`chore: recon notes + branch hygiene` → `feat(data): schema V+1, rig table, migration + backfill` → `test(data): migration + rig repository` → `feat(archery): handedness normalizer + effective resolver` → `test(archery): swap/involution/invariance suite` → `chore(ci): app unit tests in workflow`

**Session 2:**
`refactor(app): feature packages + nav scaffold + stub tabs` → `feat(onboarding): steps 1–7, atomic commit, DataStore` → `feat(train): rig chip replaces draw-weight, handedness override, contextual camera permission` → `test(app): onboarding VM suite`

**Session 3:**
`feat(home): card registry + v1 cards` → `feat(settings): profile/equipment/capture/appearance/data/about` → `feat(ui): missing Hyle atoms + HyleAvatar` → `test(app): home VM + avatar + prefs` → `chore: QA script run + NOTES final + PR`

---

## Appendix A — DataStore keys (Preferences)

| key | type | default | notes |
|---|---|---|---|
| `role` | String | `ARCHER` | `ARCHER` \| `COACH` \| `BOTH` |
| `onboarded` | Boolean | `false` | gates start destination |
| `reduce_motion` | Boolean | `false` | ORed with system setting |
| `haptic_strength` | String | `MED` | `OFF` \| `LOW` \| `MED` \| `HIGH` |
| `glow_intensity` | Int | `100` | 0–100, provenance-glow alpha multiplier |
| `keep_raw_video` | Boolean | `false` | wiring per recon §2.6 |

## Appendix B — Reference tables

**Bow types:** `RECURVE`, `COMPOUND`, `BAREBOW`. **Handedness:** `RH`, `LH`.
**Units:** poundage entered/displayed in **lbs**, riser length in **inches** (archers' language); kg conversion (`× 0.45359237`) is Phase 2's concern, at the math layer only.
**Draw length:** stored **mm**, Int, nullable.

## Appendix C — Copy strings (English source; all via resources)

- **Handedness step title:** "Which hand draws the string?"
  - RH option: "Right-handed — bow in the left hand, draw with the right"
  - LH option: "Left-handed — bow in the right hand, draw with the left"
  - helper: "This is about your draw hand, not your writing hand. You can change it later in Settings, or override it for a single session."
- **Coach-role note (step 1):** "The coach workspace arrives in a later build — everything for training yourself is ready now."
- **Draw-length helper:** "Best measured at full draw — nock groove to the pivot point of the grip, plus 1¾″ (AMO). A coach or pro shop does it in a minute. Skip if unsure — you can add it later."
- **OTF helper (measured):** "Poundage measured on the fingers at your draw length — a bow scale at the club does it."
- **Riser helper:** "Limb poundage is rated at 28″ draw on a 25″ riser (ILF convention) — riser length shifts what you actually hold."
- **OTF estimate template:** "≈ {est} lbs — from {marked}# limbs, {riser}″ riser, {draw}″ draw. Convention estimate; limb bolts shift it up to ±10%. A measured value replaces this."
- **Permissions primer:** "Crocodyl asks for the camera the first time you enter Train — it's how your form gets analyzed, entirely on this phone. Notifications come later, and every one of them defaults to off."
- **ComingSoon — Progress:** "Your baseline lives here soon — stability trends, projections, and what your form is telling you."
- **ComingSoon — Body:** "A map of how your body's doing — pain, injuries, physio — arrives in an upcoming build."
- **ComingSoon — Calendar:** "Sessions, rest days, and your streak will live here."
- **Recent sessions, empty:** "No sessions yet. Your first recorded end will show up here."
- **Delete-active-rig dialog:** "This rig is active. Activate another rig first, then delete this one."
- **Delete-last-rig dialog:** "You need at least one rig. Add another before deleting this one."
- **Wipe dialog:** "This erases everything on this device — sessions, rigs, profile. Nothing is stored anywhere else, so there is no undo. Type WIPE to continue."
- **Privacy statement v0 (About):** "Crocodyl runs entirely on this device. Sessions, poses, scores, and your profile never leave it. This build makes zero network calls — there's nothing to opt out of."

## Appendix D — Known unknowns this brief expects recon to settle

1. Which branch truly holds the app (PR #1 status) and whether default-branch can be set to `main`.
2. DI incumbency → Hilt adoption or extension of the incumbent.
3. Pose coordinate space → which mirror transform row applies (§4, table).
4. Legacy session draw-weight column name (feeds the migration backfill) — or its absence (R2).
5. Whether raw video is currently retained post-analysis (feeds the `keep_raw_video` wiring decision).

**And one for Madhav, not recon:** the estimator constants in §A3.1 (2.0 lbs/in draw, 1.0 lb/in riser) are convention defaults — confirm or correct them from coaching practice (DECISION-21). They ship as named constants either way.

Everything else in this brief is decided. Build it.

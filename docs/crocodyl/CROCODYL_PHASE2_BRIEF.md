# CROCODYL — Phase 2 Build Brief: **Wellness + Life Layer**

**Executor:** Claude Code, on `Form-analyser`, branch `phase-2/wellness-life` **based on the merged Phase 1 PR**.
**Parent doc:** `CROCODYL_FEATURE_SPEC.md` rev 1.3 — §3.2, §3.3 (check-ins), §3.5 (all), §3.7, §3.8 (load/ACWR/readiness), §7, §8, §10, §11 row 2.
**Standing rules:** §1 of `CROCODYL_PHASE1_BRIEF.md` applies verbatim (guardrails, Hyle law, additive migrations, commit style, adaptation rules R1–R4). Deliverable notes file: `PHASE2_NOTES.md`.

---

## 0. Mission & non-goals

**Mission.** Give the app a life outside the shot: pre/post/standalone check-ins, rest days, a formally-specified streak with hiatus mode, the calendar tab, the private-class life layer (mood · life events · cycle · medication), and `core-wellness` — the pure-JVM module holding load, ACWR, readiness, and duration math that every later phase leans on.

**Non-goals:** body map SVG, pain logs, injuries, physio, vault (Phase 3 — but see §B: the **region-ID contract ships now**) · exchange/consent enforcement (Phase 5 — but the **privacy registry ships now**) · workouts/Clover (9) · external-load lane & medication-masking analytics (10) · Progress tab content (10) · notifications/reminders of any kind · any network call.

## 1. Recon-lite (first 15 min, into NOTES)

1. Confirm Phase 1 merged; record its final schema version **V₁** and any deviations its NOTES logged that touch this phase (column naming, DI choice, nav style).
2. Locate segmenter output timestamps per shot (draw-start / release) as persisted or derivable — feeds duration math (§A4).
3. Confirm where session arrow-count lives post-Phase-1 (detected shot count).

---

## 2. Workstream A — `core-wellness` (new Gradle module, pure JVM, zero Android deps)

Everything below is pure functions + small data classes, exhaustively unit-tested. All constants live in one `WellnessConstants` object, KDoc'd, referenced by tests.

### A1. Load model

```
shot_load(session)  = arrows × otf_kg          // otf_kg = effectivePoundageLbs × 0.45359237
srpe_load(session)  = duration_min × rpe       // rpe from POST check-in (CR10)
```
**Poundage resolution per session:** session's rig `effectivePoundageLbs` (measured > estimated > marked, per Phase 1 §A3.1) → if null, athlete's *current active* rig → if still null, the session is **excluded from the load series** and flagged `loadIncomplete` (surfaced in the calendar load view as a hollow marker, never silently zeroed).
**Daily aggregation:** loads summed per `LocalDate` (device zone); zero-days are real zeros in the series.

### A2. ACWR (EWMA)

```
λ_acute   = 2 / (7 + 1)   = 0.25
λ_chronic = 2 / (28 + 1)  ≈ 0.06897
ewma_t    = λ·load_t + (1 − λ)·ewma_{t−1}      // seeded at first training day
acwr_t    = ewma_acute_t / ewma_chronic_t       // guard: chronic < 1e-6 ⇒ undefined
```
**Warm-up gate:** ACWR renders only after ≥ 21 calendar days of history containing ≥ 10 training days; before that the UI shows "building your baseline (d/21)". Zones (spec §3.8): `<0.8` · `0.8–1.3` · `1.3–1.5` · `>1.5` — encoded shape + luminance + numerals, never hue-alone.
**Primary series = shot_load.** `srpe_load` is computed in parallel and shown as the secondary lane in the load view; ACWR itself is never mixed-unit.

### A3. Streak engine (formal)

Input: per-day facts for completed days (today is **provisional**, never breaks anything):
`S` session logged · `R` rest logged · `P` day matches the planned-rest pattern · `C` any check-in that day · `H` day inside a hiatus span.

```
qualifies(day) = S ∨ R ∨ (P ∧ C) ∨ H
```
Evaluation oldest→newest over completed days: qualifying day → streak +1. Non-qualifying day: if the **previous** day qualified → day is a **grace day**: streak preserved, `patched += 1`. Two consecutive non-qualifying days → streak resets to 0. Output: `StreakState(length, patchedCount, brokenAt?, provisionalToday: Boolean)`.
Notes: planned-rest **without** a check-in is a plain unlogged day (grace applies); hiatus days qualify unconditionally and don't increment `patched`; day boundary = device-local midnight via `LocalDate` (DST-safe by construction — test it anyway).

### A4. Session duration (auto, idle-trimmed)

```
duration_auto = Σ shot_span_i + Σ min(gap_j, GAP_CAP)      // GAP_CAP = 180 s
shot_span_i   = release_i − draw_start_i
gap_j         = draw_start_{j+1} − release_j
```
Persisted as `duration_auto_s`; effective `duration_s = override ?? auto`. Zero shots detected ⇒ auto = recording length, flagged.

### A5. Readiness rubric v2 (deterministic cascade; v3 adds injuries in Phase 3)

Precedence, first section that matches wins the **level**; *all* matching clauses contribute **reason strings**:

| Level | Trigger clauses |
|---|---|
| QUIET | active hiatus |
| REST_ADVISED | acwr > 1.5 · energy ≤ 1 |
| CAUTION | acwr ∈ [1.3, 1.5] · sleep ≤ 2 · energy = 2 · soreness regions ≥ 3 · active life-event impact = 3 · latest check-in older than 36 h ("log a check-in") |
| READY | otherwise |

Life-event clause emits a **context** reason ("high-stress window"), never event content. Missing data never crashes the cascade — absent inputs simply don't fire clauses. Constants (36 h, thresholds) in `WellnessConstants`; log any tuning as DECISION-P2 items in NOTES.

### A6. Cycle estimator (pure; UI in §F3)

Gate: ≥ 3 completed cycles. `cycle_len = median(last ≤6 start-to-start intervals)`; `uncertainty = MAD(intervals)`. Day-in-cycle from latest start; buckets: menstrual = days 1..bleed_len · ovulatory = (cycle_len − 14) ± 1 · follicular = between · luteal = remainder. Output always carries the uncertainty and an `ESTIMATE` tag — the consuming copy is pattern-discovery framed (Appendix C), never prescriptive.

### A7. Privacy registry (ships here, enforced in Phase 5)

```kotlin
enum class PrivacyClass { SHAREABLE, MEDICAL, PRIVATE }
object PrivacyRegistry { val byTable: Map<String, PrivacyClass> = ... }  // every table, no default
```
Phase 2 registers **all** tables (Phase 1's included, as SHAREABLE). Robolectric test in app: reflect over the Room database's entity list, assert every table name has a registry entry — the test that keeps future phases honest.

## 3. Workstream B — `core-body` (new module, IDs only this phase)

`RegionIds`: the 52-region contract from spec §3.6 as string constants + `side` metadata. **Geometry arrives in Phase 3**; Phase 2 consumes IDs for soreness. Plus the handedness-aware chip resolver:

```kotlin
fun resolveChip(chip: SorenessChip, handedness: Handedness): List<RegionId>
```
Chips are **functional**, not anatomical — "Draw shoulder" means the right shoulder for RH and the left for LH (mapping table, Appendix B). Unit tests: resolver for both handednesses; every chip maps to ≥1 valid RegionId.

## 4. Workstream C — Schema V₁ → V₁+1

```sql
CREATE TABLE checkin (id PK, athlete_id FK, ts, kind TEXT /*PRE|POST|STANDALONE*/,
  skipped INTEGER NOT NULL DEFAULT 0, energy INT?, sleep INT?, motivation INT?,
  rpe REAL?, feel INT?, note TEXT?);
CREATE TABLE soreness (checkin_id FK, region_id TEXT, PRIMARY KEY(checkin_id, region_id));
CREATE TABLE rest_day (date TEXT PK /*ISO LocalDate*/, planned INTEGER, note TEXT?);
CREATE TABLE hiatus (id PK, start_date TEXT, end_date TEXT?, life_event_id INTEGER?);
CREATE TABLE mood_entry (id PK, ts, mood INT, tags TEXT /*json*/, note TEXT?);          -- PRIVATE
CREATE TABLE life_event (id PK, start_date TEXT, end_date TEXT?, category TEXT,
  impact INT /*1..3*/, title TEXT);                                                     -- PRIVATE
CREATE TABLE cycle_entry (id PK, start_date TEXT, end_date TEXT?, flow INT?,
  symptoms TEXT /*json*/);                                                              -- PRIVATE
CREATE TABLE medication_entry (id PK, ts, name TEXT, dose TEXT?, schedule TEXT?,
  taken INTEGER NOT NULL DEFAULT 1);                                                    -- MEDICAL
CREATE TABLE event (id PK, ts, title TEXT, icon TEXT?, tags TEXT /*json*/);

ALTER TABLE session ADD COLUMN pre_checkin_id INTEGER;
ALTER TABLE session ADD COLUMN post_checkin_id INTEGER;
ALTER TABLE session ADD COLUMN duration_auto_s INTEGER;
ALTER TABLE session ADD COLUMN duration_s INTEGER;
ALTER TABLE session ADD COLUMN arrows_actual INTEGER;
```
Class markers land in `PrivacyRegistry` (§A7) + `-- PRIVATE/MEDICAL` comments in the entity KDoc. Migration test (Robolectric): V₁ fixture db with Phase-1 data → migrate → new tables exist, sessions keep working.

## 5. Workstream D — Check-in flows

- **Pre-gate:** TrainSetup "Start" → bottom sheet, ≤15 s by construction: three 1–5 dials (energy/sleep/motivation, 48 dp targets, haptic detents), soreness **chip row** (§B, multi-select), optional note, **Start session** + **Skip** (Skip writes a `kind=PRE, skipped=1` row — spec: skips are recorded). Session links `pre_checkin_id` either way.
- **Post:** on capture stop, before Review: RPE dial 0–10 with Borg CR10 anchor labels (Appendix C), feel 1–5, duration card (`auto` shown with per-shot derivation caption, tap to override), arrow reconciliation (`detected N` prefilled → editable `arrows_actual`). Writes `post_checkin_id`, `duration_*`, legacy compatibility fields per Phase 1 rules.
- **Standalone "+ Log":** FAB/entry on Home and Calendar → type picker grid: Wellness check-in · Rest day · Mood · Life event · Cycle (only if enabled) · Medication · Event. (Pain / Injury / Physio / Document slots exist in the picker model but are hidden until Phase 3.) Each type = one small sheet, one decision per row, all writes atomic per entry.

## 6. Workstream E — Calendar tab (replaces the stub)

- Month grid (`LocalDate` math, Mon-start per locale), day cells stack up to 3 icon glyphs: session · rest · check-in · event; hiatus renders as a **continuous band** across its span; today outlined. Tap → day sheet listing all entries with type icons + add-from-this-day.
- **Load view toggle** (top-right segmented Calendar | Load): weekly bars (shot_load primary, srpe as thin secondary lane), ACWR line with the 0.8–1.3 sweet band as a luminance region + numeral labels; `loadIncomplete` days = hollow markers; warm-up state shows the "building your baseline (d/21)" card.
- Streak strip above the grid: current length, patched count as a small `~n` tag, frozen glyph during hiatus.

## 7. Workstream F — Life layer

**F1. Mood & life events.** Mood sheet: 1–5 + tag chips (stressed / anxious / flat / okay / great) + note. Life-event sheet: category (bereavement / illness / exams / work / travel / relationship / other), impact 1–3, title, ongoing toggle. **Impact = 3 triggers the hiatus offer dialog** (copy, Appendix C) — accepting opens a hiatus starting today linked to the event; declining just saves.

**F2. Hiatus.** Manual start/end in Settings → Streak & Rest; at most one open hiatus. While active: streak frozen via §A3, Home enters the **quiet state** (§G), calendar band renders. Ending sets `end_date = today`.

**F3. Cycle module.** Off until enabled in Settings (creates no UI surface before that). When on: "+ Log" gains the Cycle type; a Cycle screen (Settings → Cycle → Open, and from the picker) shows history, current day-in-cycle + phase **estimate** with uncertainty (§A6) once gated, and the pattern-discovery framing copy persistently. No notifications, no predictions pushed.

**F4. Medication.** Sheet: name (autosuggest from history), dose, optional schedule text, taken/skipped toggle, ts editable. List view under Settings → Medication. Analytics consumption is Phase 10; this phase only records.

## 8. Workstream G — Home upgrades  ·  Workstream H — Settings additions

**G.** Card registry gains, final order: `Header · Readiness · StreakWeek · ActiveRig · StartSession · StabilitySpark · RecentSessions`.
- `Readiness(level, reasons)`: shape + luminance (filled / half / hollow circle) + first reason line; tap expands all reasons. QUIET replaces it with the quiet card (copy, Appendix C) and demotes StartSession to secondary styling — available, unpushy.
- `StreakWeek(streak, patched, week: 7 DayGlyphs)`: glyphs = session / rest / check-in / hiatus / empty; today ringed.

**H.** Settings tree grows (ordering per spec §7): **Wellness & Check-ins** (pre/post item toggles — hiding a dial removes it from sheets; soreness chips on/off), **Streak & Rest** (planned-rest weekly pattern picker → DataStore `planned_rest_days` CSV e.g. `MO,TH`; hiatus start/end), **Cycle Tracking** (enable toggle + Open), **Medication** (list + add). DataStore additions: `planned_rest_days: String = ""`, `cycle_enabled: Boolean = false`.

---

## 9. Tests (all CI-gating)

**core-wellness (JVM):**
```
LoadModelTest.shotLoadPoundageResolutionChain      LoadModelTest.incompleteSessionsExcludedAndFlagged
AcwrTest.ewmaAgainstHandComputedFixture            AcwrTest.warmupGateAndChronicGuard
StreakEngineTest.truthTableAllClauses              // table-driven: S,R,P∧C,H rows
StreakEngineTest.singleGracePatches_doubleBreaks   StreakEngineTest.hiatusFreezesWithoutPatching
StreakEngineTest.plannedRestWithoutCheckinIsUnlogged
StreakEngineTest.todayIsProvisional                StreakEngineTest.dstBoundaryDays
DurationTest.idleTrimGapCap                        DurationTest.zeroShotsFallsBackFlagged
ReadinessTest.cascadePrecedenceAndReasonAccumulation
ReadinessTest.lifeEventEmitsContextNotContent      ReadinessTest.missingInputsNeverFire
CycleEstimatorTest.gateBelowThreeCycles            CycleEstimatorTest.medianMadAndBuckets
```
**core-body (JVM):** `RegionIdsTest.fiftyTwoUniqueIdsWithSides`, `ChipResolverTest.handednessAwareBothWays`.
**app (Robolectric):** `MigrationV1toV2Test.phase1DataSurvives`, `PrivacyRegistryTest.everyRoomTableClassified`, `CheckinViewModelTest.preSkipRecordedAndLinked`, `PostCheckinTest.durationOverrideAndReconciliation`, `HomeViewModelTest.readinessQuietStateAndOrdering`, `HiatusTest.singleOpenSpanInvariant`.

## 10. Manual QA (RedMagic; results → NOTES)

1. Update-install over Phase 1 build (migration path, not fresh). Existing session + rigs intact.
2. Train → pre-gate appears; set energy 2, sleep 2, tap "Draw shoulder" chip; start; stop after a few draws → post sheet: RPE 4, duration auto shown, override to a round number, arrows 12 → Review.
3. Home: Readiness = CAUTION with sleep + energy reasons; StreakWeek shows today's session glyph.
4. Skip-path: start another session, Skip the pre-gate → session still links a skipped check-in (verify via day sheet).
5. Calendar: both sessions on today; log a Rest day tomorrow-minus-tricks (log for yesterday via day sheet) — streak math visible in strip.
6. Load view: bars render; warm-up card shows (d/21) since history is young.
7. "+ Log" → Life event, impact 3 → hiatus offer → accept → Home quiet state, streak frozen glyph, calendar band. End hiatus in Settings → normal Home returns.
8. Enable Cycle in Settings → picker gains Cycle; log a start; screen shows history and the gated "needs 3 cycles" state.
9. Medication: log "Ibuprofen 400 mg" taken; appears in Settings → Medication list.
10. Reduce-motion on: all new sheets snap. Haptics Off: dials silent.
11. Relaunch: everything persists; readiness recomputes with the 36 h staleness reason after… note as time-dependent, verify logic via unit test instead.

## 11. Definition of done

- [ ] CI green: engine, archery-module, **core-wellness, core-body**, app unit tests + assembleDebug (add new modules to the workflow's test task list).
- [ ] Every named test in §9 exists and passes; streak truth-table test covers all clause combinations.
- [ ] `PrivacyRegistryTest` proves every table classified; PRIVATE/MEDICAL markers on the four sensitive tables.
- [ ] Migration test proves Phase-1 data survives.
- [ ] QA script executed; results in `PHASE2_NOTES.md` with any DECISION-P2 constant-tuning notes.
- [ ] Zero new permissions; zero network; `git diff` guard on engine/segmenter still clean.
- [ ] PR `Phase 2 — Wellness + Life Layer` open, spec-section-linked.

## 12. Sequencing — three sessions

**S1 (headless):** `feat(core-wellness): module + load/acwr/streak/duration/readiness/cycle + constants` → full JVM suite → `feat(core-body): RegionIds + chip resolver + tests` → `chore(ci): new module test tasks`.
**S2:** `feat(data): schema V₁+1 + privacy registry + migration test` → `feat(checkins): pre-gate, post sheet, standalone picker` → `test(app): checkin + migration + registry`.
**S3:** `feat(calendar): grid, day sheet, load view` → `feat(life): mood, life events, hiatus, cycle, medication` → `feat(home): readiness + streak cards, quiet state` → `feat(settings): four new nodes` → QA + NOTES + PR.

---

## Appendix A — Constants (single source: `WellnessConstants`)

`λ_acute 0.25 · λ_chronic 2/29 · ACWR zones 0.8/1.3/1.5 · warm-up 21 d & 10 training days · GAP_CAP 180 s · staleness 36 h · readiness thresholds per §A5 table · cycle gate 3 · luteal offset 14 d · ovulatory window ±1 d`

## Appendix B — Soreness chips → region IDs (handedness-resolved)

| Chip | RH resolves to | LH mirrors |
|---|---|---|
| Neck | neck_ant, neck_post | same |
| Draw shoulder | delt_ant_r, delt_lat_r, delt_post_r, rotator_cuff_r | …_l |
| Bow shoulder | delt_ant_l, delt_lat_l, delt_post_l, rotator_cuff_l | …_r |
| Upper back | trap_upper_l/r, trap_mid_l/r, rhomboid_l/r | same |
| Lower back | erector_l/r | same |
| Draw forearm | forearm_flex_r, forearm_ext_r | …_l |
| Bow arm | biceps_l, triceps_l, forearm_ext_l | …_r |
| Core | abs_upper, abs_lower, oblique_l/r | same |
| Legs | quad_l/r, hamstring_l/r, calf_l/r | same |

## Appendix C — Copy (resources)

- **CR10 anchors:** 0 Rest · 1 Very, very easy · 2 Easy · 3 Moderate · 4 Somewhat hard · 5 Hard · 7 Very hard · 10 Maximal (6, 8, 9 unlabeled).
- **Hiatus offer:** "That's a heavy one. Want to pause the streak and quiet the app for a while? Nothing breaks, nothing nags — pick it back up whenever."
- **Quiet card:** "On pause. Everything's here when you want it."
- **Cycle framing (persistent on the Cycle screen):** "This is pattern discovery from your own logs — the science on cycle and training is genuinely mixed, and this app won't pretend otherwise. Estimates carry uncertainty on purpose."
- **Warm-up card:** "Building your load baseline — {d} of 21 days."
- **Load-incomplete marker caption:** "No poundage on record for this session — it's not counted in load."
- **Staleness reason:** "No recent check-in — log one for a truer read."

## Appendix D — Open items for Madhav

1. Readiness thresholds (§A5) and Appendix A constants — sane defaults, tune from lived use (DECISION-P2).
2. Soreness chip set — confirm the nine cover coaching reality; additions are one map entry each.

# CROCODYL — Phase 3 Build Brief: **Body Layer**

**Executor:** Claude Code, on `Form-analyser`, branch `phase-3/body` **based on the merged Phase 2 PR**.
**Parent doc:** `CROCODYL_FEATURE_SPEC.md` rev 1.3 — §3.5.1–3.5.2, §3.6, §7, §8, §10, §11 row 3.
**Standing rules:** `CROCODYL_PHASE1_BRIEF.md` §1 verbatim. Notes file: `PHASE3_NOTES.md`.

---

## 0. Mission & non-goals

**Mission.** The body becomes a first-class surface: a tappable two-sided muscle atlas over the 52-region contract from Phase 2, pain logging with the exact colorblind-law encodings, structured injuries, physio plans + session logging, and the encrypted document vault. Readiness learns about injuries.

**Non-goals:** exchange of any body data (Phase 5 — classes already registered) · coach-authored physio (6) · intervention/rehab analytics (10) · target photos (7 — the vault is for *medical* documents only) · a Physio role · OCR/interpretation of documents, ever.

## 1. Recon-lite (into NOTES)

1. Phase 2 merged; schema **V₂**; RegionIds + chip resolver in `core-body` as expected; any P1/P2 NOTES deviations touching this phase.
2. **Atlas drop-in check:** if `app-android/src/main/assets/body_atlas.override.json` exists (Madhav may supply hand-drawn geometry later), load region paths from it instead of the built-in geometry — same IDs, same viewport contract (§A1). Build the loader either way; log which source is live.
3. Confirm Tink is not yet a dependency (Phase 5 brings it for signing; this phase introduces it first, for the vault — align versions with a note for Phase 5).

---

## 2. Workstream A — Atlas geometry (`core-body` grows)

### A1. Geometry contract

- **Viewport:** 1000 × 2000 units per side (FRONT, BACK), body centered, head at top. All paths are SVG path strings in viewport units.
- **Author once, mirror right:** hand-author only **left-side** and **centerline** regions as path strings; right-side paths are generated at object-construction by mirroring `x′ = 1000 − x` (parse via `androidx.core.graphics.PathParser`, transform with `Matrix(scaleX = −1)` + `postTranslate(1000, 0)`). Symmetry is therefore guaranteed, not hoped for.
- **Style:** schematic Hyle line-work — a clean stylized silhouette with region boundaries, **not** anatomical realism. Silhouette outline is a separate non-tappable path per side.
- **Region record:** `BodyRegion(id: RegionId, side: FRONT|BACK, path: String, labelAnchor: Point)` — labelAnchor is where the numeral badge sits (centroid by default, hand-adjustable).
- **Hit-testing:** build one `android.graphics.Region` per BodyRegion at init (path → Region against viewport clip); tap → composable-local point → viewport transform → first (only) containing region.

### A2. Geometry acceptance tests (these gate the phase — headless, JVM/Robolectric)

```
AtlasIntegrityTest.all52IdsPresentExactlyOnceAcrossSides
AtlasIntegrityTest.leftRightPathsAreExactMirrors
AtlasIntegrityTest.everyRegionBoundingBoxAtLeast60x60Units      // finger-sized
AtlasIntegrityTest.noOverlaps_gridSample200x400HitsAtMostOne
AtlasIntegrityTest.everyCentroidHitTestsToItsOwnRegion
AtlasIntegrityTest.overrideJsonRoundTripsIdentically            // drop-in seam (§1.2)
```
If the schematic proves fiddly, iterate the *paths*, never the tests — the tests are the definition of "tappable atlas."

## 3. Workstream B — Schema V₂ → V₂+1

```sql
CREATE TABLE pain_log (id PK, athlete_id FK, ts, region_id TEXT, intensity INT /*0..10*/,
  tags TEXT /*json: sharp|dull|ache|tingling|stiff*/, injury_id INTEGER?);
CREATE TABLE injury (id PK, athlete_id FK, onset TEXT, regions TEXT /*json ids*/,
  severity INT /*1..3*/, mechanism TEXT /*ACUTE|OVERUSE|UNKNOWN*/,
  status TEXT /*ACTIVE|RECOVERING|RESOLVED*/, resolved_date TEXT?, notes TEXT?);
CREATE TABLE physio_plan (id PK, athlete_id FK, title TEXT, target_regions TEXT /*json*/,
  schedule TEXT /*json days-of-week*/, start_date TEXT, end_date TEXT?,
  source TEXT /*SELF*/, notes TEXT?);
CREATE TABLE physio_exercise (id PK, plan_id FK, name TEXT, sets INT, reps INT?, hold_s INT?);
CREATE TABLE physio_session (id PK, plan_id FK, ts, completed TEXT /*json exercise ids*/, note TEXT?);
CREATE TABLE document (id PK, athlete_id FK, ts, title TEXT, mime TEXT,
  enc_path TEXT, sha256 TEXT, size_bytes INTEGER, injury_id INTEGER?);      -- MEDICAL
```
PrivacyRegistry additions: `pain_log/injury/physio_* = SHAREABLE` (Full-tier per spec §4.3), `document = MEDICAL`. `injury.status=RESOLVED` requires `resolved_date` (repository invariant + test). Migration test: V₂ fixture survives.

## 4. Workstream C — Document vault (Tink, not the deprecated Jetpack lib)

`androidx.security:security-crypto` is deprecated — do **not** add it. Use **Tink directly**:

- **Keyset:** `AndroidKeysetManager` with master-key URI `android-keystore://crocodyl_vault_master`, keyset in private shared prefs, template `AES256_GCM_HKDF_4KB` **StreamingAead**.
- **Write path:** SAF `OpenDocument` (pdf/image mimes) or camera `TakePicture` → temp file in `cacheDir/vault-intake/` → stream-encrypt to `filesDir/vault/{uuid}` with **associated data = the document row id** (bind ciphertext to its metadata) → compute plaintext sha256 during the stream → insert row → delete intake temp. Max size 25 MB v1 (guarded, friendly error).
- **Read path:** decrypt to `cacheDir/vault-view/{uuid}.{ext}`; images via the incumbent image loader from that file; PDFs via `android.graphics.pdf.PdfRenderer` page-bitmap pager. `vault-view/` wiped in `onStop` of the viewer **and** on every app start.
- **Deletion:** row + ciphertext file; note in code that flash overwrite is theater — plain delete, no pretend shredding.
- **Tests:** `VaultTest.encryptDecryptRoundTripSha256`, `VaultTest.ciphertextLacksPlaintextMagicBytes` (encrypt a PNG fixture; assert no `89 50 4E 47` anywhere in the first 4 KB and file is not image-decodable), `VaultTest.wrongAssociatedDataFailsClosed`, `VaultTest.sizeGuard`.

## 5. Workstream D — Body tab UI (replaces the stub)

- **Layout:** side flip (Front | Back segmented, or horizontal swipe with the segmented as source of truth), view chips: **Today · History · Injuries · Physio**.
- **Today:** atlas with current-day pain rendered; tap region → **intensity dial** (radial 0–10, haptic detents, big numeral) + quality-tag chips + Save; long-press region → history sheet (that region's pain over time, sparkline + entries).
- **Encodings (the law, exact):**
  - *Pain fill:* single-hue violet luminance ramp, linear interpolation between anchor stops — `0 → transparent · 2 → #2E2354 · 5 → #4A3A8C · 8 → #6C5BD1 · 10 → #8E7BFF` — plus a numeral badge at `labelAnchor`. Brighter = worse.
  - *Physio target:* cyan `#08FED5` **cross-hatch** clipped to the region path — 45°, stroke 4 units, spacing 14 units, plus a 5-unit outline. No fill.
  - *Both:* ramp fill + hatch + split badge (numeral | physio glyph).
  - Hue is never the only channel; nothing red/green-semantic anywhere. (QA step 9 makes this literal — see §10.)
- **History:** grid of mini-atlases, one per week (last 8, lazy), each region tinted by that week's **max** intensity; tap a week → expanded.
- **Injuries view:** atlas overlay of active-injury regions (distinct dashed outline + severity numeral) + list below → injury detail.
- **Physio view:** hatch overlay of all active plans' targets + plan list.

## 6. Workstream E — Injuries, physio, integrations

- **Injury CRUD:** form per schema; region multi-select **on the atlas**; status chips with the resolved-date invariant; link/unlink documents; injury detail shows linked pain logs (auto-suggest linking when a pain log's region ∩ an active injury's regions).
- **Physio plans:** create/edit — title, atlas multi-select targets, exercise rows (name, sets, reps *or* hold seconds), days-of-week schedule, start/end. **Session logging** from "+ Log" (slot unhides): pick plan → tick completed exercises → note.
- **"+ Log" picker:** Pain · Injury · Physio session · Document slots unhide (Phase 2 built the hidden slots).
- **Pre-check-in upgrade:** soreness chips → **mini-atlas quick-tap** (multi-select regions, no dial). Same `soreness` table, same region IDs — Phase 2's seam pays off; chips remain as an accessibility fallback behind a Settings toggle.
- **Readiness v3 (`core-wellness`, additive param):** `activeInjuries: List<InjurySummary> = emptyList()` — severity 3 ACTIVE → REST_ADVISED reason; severity ≥ 2 ACTIVE → CAUTION reason ("active injury: {regions}"). The app never overrules the athlete — reasons, not lockouts. Tests extend the cascade suite.
- **Home:** Body-tab bar icon gains a shape-coded badge when any ACTIVE injury exists; readiness reasons flow through automatically.
- **Settings:** Data node gains "Document vault: {n} files · {size}"; Wellness node gains the chips-vs-atlas pre-check-in toggle.

---

## 7. Tests — consolidated gate list

§A2 atlas suite · §B migration + `InjuryRepositoryTest.resolvedRequiresDate` · §C vault suite · `RampTest.anchorStopsExactAndInterpolationMonotonic` · `HatchTest.rendersWithinRegionClip` (paparazzi/screenshot if incumbent, else geometry assertion) · `ReadinessTest.injuryClausesV3` · `PainLogTest.roundTripAndInjuryAutoLinkSuggestion` · `PrivacyRegistryTest` still green with new tables (it will fail on its own if one is missed — that's the point).

## 8. CI

Add nothing structural; new tests ride existing tasks. Assemble + full unit suite green = gate.

## 9. Sequencing — four sessions

**S1 (headless):** atlas geometry + integrity suite until green (`feat(core-body): atlas paths + mirror build + hit-testing`, `test(core-body): integrity suite`). This session is done when the six A2 tests pass — expect path iteration.
**S2 (headless-ish):** `feat(data): schema V₂+1 + registry + migration test` → `feat(vault): Tink streaming vault + suite`.
**S3:** `feat(body): tab, today view, dial, encodings, history` → `test(app): ramp/encoding`.
**S4:** `feat(injury+physio): CRUD, sessions, atlas pickers, readiness v3, pre-check-in atlas` → QA → NOTES → PR `Phase 3 — Body Layer`.

## 10. Manual QA (RedMagic; results → NOTES)

1. Update-install over Phase 2; migration clean, Phase 2 data intact.
2. Body tab: front/back flip; tap `rotator_cuff_r` (back) → dial to 7, tags *ache + stiff*, save → region fills bright-violet with a 7 badge.
3. Long-press same region → history sheet shows the entry.
4. Log pain 3 on `forearm_flex_l` (front) → visibly dimmer fill than the 7 — the ramp reads as *severity by brightness*.
5. Create injury: draw-side rotator cuff, severity 2, ACTIVE, mechanism OVERUSE → Home readiness now CAUTION with the injury reason; Body tab icon badged.
6. Create physio plan "Cuff series", targets = rotator_cuff_r + delt_post_r, 2 exercises, Mon/Thu → Physio view shows cyan hatching; the region carrying both pain and physio renders fill + hatch + split badge.
7. Log a physio session from "+ Log", tick one exercise.
8. Documents: import a PDF via SAF into the injury; open it (pages render); background the app; relaunch → viewer cache empty, re-open decrypts again. Camera-capture an image doc; both listed with sizes; Settings → Data shows the vault meter.
9. **CVD validation (the author is the instrument):** with pain fills and physio hatches on adjacent regions, confirm every state pair is distinguishable to *your* eyes at arm's length outdoors-brightness — fill-vs-hatch, dim-vs-bright, badge legibility. Any failure is a bug against §5, not a preference.
10. Pre-check-in now shows the mini-atlas; multi-tap two regions; entry lands in `soreness` with correct IDs; flip the Settings toggle → chips return.
11. Resolve the injury (resolved date required by the form) → badge clears, readiness reason gone.
12. Wipe-and-reonboard smoke: vault files gone with the wipe.

## 11. Definition of done

- [ ] CI green across all modules; every §7 test present and passing; atlas integrity suite is the loudest gate.
- [ ] Vault ciphertext proven opaque (magic-bytes test) and AD-bound; viewer cache lifecycle verified.
- [ ] Encodings match §5 to the hex and unit; QA step 9 signed off by Madhav personally.
- [ ] Migration proves V₂ survival; PrivacyRegistry test green with `document = MEDICAL`.
- [ ] Readiness v3 additive — Phase 2 call sites compile unchanged.
- [ ] Zero new permissions beyond what SAF/camera already grant; zero network; engine/segmenter diff guard clean.
- [ ] `PHASE3_NOTES.md`: recon, geometry-source (built-in vs override), deviations; PR open.

## Appendix — Region ID master list (52; single source is `core-body/RegionIds`)

```
FRONT (25): neck_ant · delt_ant_l/r · delt_lat_l/r · pec_l/r · biceps_l/r ·
  forearm_flex_l/r · hand_l/r · abs_upper · abs_lower · oblique_l/r ·
  hip_l/r · quad_l/r · knee_l/r · shin_l/r
BACK (27): neck_post · trap_upper_l/r · trap_mid_l/r · trap_lower_l/r ·
  rhomboid_l/r · delt_post_l/r · rotator_cuff_l/r · lat_l/r · erector_l/r ·
  triceps_l/r · forearm_ext_l/r · glute_l/r · hamstring_l/r · calf_l/r
```
(The spec's "~40" was an estimate; **this list is the contract** — 52 IDs, already shipped in Phase 2.)

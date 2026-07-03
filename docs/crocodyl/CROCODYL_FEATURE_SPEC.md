# CROCODYL — Complete App Spec (Archery v1) · **rev 1.3**

**Status baseline:** repo `Form-analyser` @ `91d53ba` (engine + archery-module unit-tested, app compiles to APK in CI, batch capture→review loop working, right-handed only, never run on device).
**rev 1.1:** integrates nine feature clusters — deep equipment inventory & wear, life/personal layer (cycle, mood, life events), device & sensor support, Flex Card, training conditions + weather fetch, competitions & time-to-goal, venue book & maps handoff.
**rev 1.2:** adds health sync (Health Connect — the Google Fit successor: external activity, sleep, cycle, medication context), live HR × shot-cycle physiology, the archery-specific workout log + four-petal Training Clover, and target-photo arrow plotting. Phases renumbered 0–14.
**rev 1.3:** draw-length guidance switched to real measurement (AMO) — no folk formulas; rig tuning gains riser length and an ILF on-the-fingers poundage estimator (convention constants, DECISION-21). Phase 1–3 build briefs issued as companion docs.
**Sport scope:** archery only. The `SportModule` seam stays; nothing here may leak archery types into `engine/` or the `core-*` modules.

---

## 0. Product frame & tiering

One-liner: *a sovereign, on-device archery training system — the archer owns every byte; the coach pays for the multi-athlete view.*

| Surface | Tier | Rationale |
|---|---|---|
| Archer app (capture, analysis, equipment, wellness, body map, calendar, scores, competitions, venues, export) | **Free, open core** | Adoption wedge; sovereignty story; zero marginal cost |
| Coach Mode (roster, dashboards, flags, assignments, score book) | **Paid** (license mechanics = DECISION-07) | One coach license monetizes a whole club of free archers |
| Bow-IMU sensor channel (A1 Pro BLE, parked in `baseline`) | **Paid add-on, later** | Hardware-gated anyway |
| AI coach — on-device (Gemma) | Free | No marginal cost |
| AI coach — cloud | **BYOK** | User pays their provider directly; we never proxy |

> **DECISION-01 (proposed):** archer-side fully free forever; Coach Mode is the paid product. Injury-risk and health-adjacent analytics are never paywalled away from the athlete they concern.

**Network posture (whole app):** exactly two outbound call classes exist, both opt-in — weather fetch (Open-Meteo, §3.10) and BYOK AI (§6). The privacy statement enumerates them verbatim. Health Connect and BLE are device-local — they add zero network calls. Everything else is airplane-mode-complete.

---

## 1. Roles & identity (no accounts, ever)

- **Roles:** `ARCHER`, `COACH`, or both; chosen at onboarding, switchable in Settings. Both-role users get a workspace switcher.
- **Identity:** first run generates an **Ed25519 keypair** (Google Tink; private key wrapped by Android Keystore via `EncryptedSharedPreferences`). Identity = `{pubkey, display name, handedness, avatar_seed}`.
- **Pairing Card (functional):** `{v, pubkey, name, handedness, club?, avatar_seed}` as QR + exportable `.croc`. Coach scans/imports to add an archer.
- **Flex Card (visual, Arc-style):** a generative Hyle identity card — AGSL shader background seeded by `avatar_seed`, name, club, bow-type glyph, and **user-toggled stats** (PB per round, streak, stability score, next competition). Rendered as shareable PNG (1080×1920 story + 1080×1080 square). Rules: private-class data (§3.5.4) can never appear on a card; QR overlay optional and off by default (flex ≠ pairing). Season-recap variant ("wrapped") is a later delight.
- **Trust model:** TOFU. Coach pins the pubkey on first import; every report is signature-verified against it. Mismatch → hard warning, import quarantined.
- **Key rotation:** Settings → Identity → Rotate (danger zone); emits a rotation card signed by the *old* key.

No server, no phone number, no email.

---

## 2. Information architecture

**Archer nav (bottom bar, 5):** Home · Train · Progress · Body · Calendar. Top bar: gear (Settings), quiver icon (Equipment), coach entry if role includes COACH.

**Coach workspace:** Roster · Flags · Scores · Library · (Settings shared).

Screen inventory (★ = exists, extend; ☆ = new):

```
★ Capture         ★ Review           ☆ Onboarding       ☆ Home
☆ Progress        ☆ Body             ☆ Calendar         ☆ Score entry
☆ Wellness log    ☆ Injury detail    ☆ Physio plan      ☆ Document vault
☆ Equipment: inventory · rig detail · tuning log · wear dashboard
☆ Competition detail                 ☆ Goals            ☆ Venue book
☆ Conditions sheet ☆ Cycle view      ☆ Life events      ☆ Flex Card studio
☆ Workout log · exercise catalog     ☆ Training Clover  ☆ Plot mode (photo/face)
☆ Medication log   ☆ HR live lane + shot-cycle plots
☆ Export sheet    ☆ Import flow      ☆ Settings tree    ☆ AI coach panel
☆ Coach: Roster · Athlete · Flags · Scores · Compare · Library · Composer
```

---

## 3. Archer features

### 3.1 Onboarding (☆)

1. Role select (Archer / Coach / Both).
2. Name + avatar seed (procedural Hyle avatar — no photos).
3. **Handedness** (RH / LH mirror). See §3.1.1.
4. Body measurement: **draw length (mm)** — athlete-level, referenced by every rig.
5. Rig v0: bow type (recurve / compound / barebow), marked poundage, on-the-fingers poundage if known (skippable → defaults; deep inventory arrives in §3.9).
6. Identity generation (automatic, one-line explainer).
7. Permissions: camera (required for Train), notifications (optional).
8. Optional: pair with coach (scan QR) or "later".

**3.1.1 Handedness (closes a known limitation).** `handedness` on the athlete entity. At pose-ingestion, before `PoseShotSegmenter`/`FormFeatureExtractor`: if `LH`, swap L/R landmark indices and negate x about the frame midline. Downstream stays in the RH canonical frame — zero changes to features or thresholds. Per-session override flag. Unit test: mirrored synthetic pose ≡ its RH twin's features.

### 3.2 Home / Landing (☆)

1. **Readiness card** — from last check-in + load model + life context (§3.5.3): shape + luminance status (filled circle = ready, half = caution, hollow = rest advised) with a one-line reason. Never hue-only.
2. **Streak chip + week strip** (7 icon-coded dots). Hiatus state renders here quietly (§3.5.5).
3. **Training Clover (mini)** — today's four petals at a glance (§3.14).
4. **Next competition chip** — countdown + venue, when one is scheduled (§3.11).
5. **Assignment card** — pending coach drills, ✓ complete.
6. **Wear nudge** (only when an item crosses 80% service life, §3.9.4).
7. **Quick actions:** Start Session · Log Wellness · Log Workout · Log Rest · Enter Scores.
8. **Trend sparkline** — stability, last 10 sessions.

### 3.3 Session flow (★ extend)

```
Pre-check-in → Capture (existing) → Post-check-in → Review (existing) → Export
```

- **Pre-check-in (≤15 s):** energy 1–5, sleep 1–5 (auto-filled from Health Connect when enabled, §3.13), soreness quick-tap mini body map, motivation 1–5, note. Skippable; skips are recorded.
- **Capture (★):** unchanged batch model. Session records active **rig** and **venue** (last-used defaults, one-tap to change). With an HR source connected (§3.15), a live HR lane renders on the capture screen — radium glow, fully on-device.
- **Post-check-in:** RPE (Borg CR10), feel 1–5, **duration** auto (first draw → last release, idle-trimmed) + override, arrow-count reconciliation, and a **conditions confirm sheet** — fetched weather shown as best-guess for the archer to accept or correct (§3.10).
- **Review (★ extend):** existing cards + charts, plus round-score attach (§3.4), AI panel (§6), Export (§4).

### 3.4 Scores & rounds (☆)

- **Presets:** WA 720 (70 m/72), WA 60 m, WA 50 m barebow, WA 18 m indoor (60), practice (custom ends × arrows), custom rubric (10-ring / 6-zone / custom max).
- **Entry:** end-by-end grid, per-arrow values (X, 10, 9 … M), running totals, X-count; big cold-thumb targets, no typing.
- **Per-arrow tagging (optional, §3.9.3):** after entering an end's values, an optional chip row tags which physical arrow (by number) shot each value. Skippable per end — never a data-entry tax. Feeds per-arrow stats.
- **Attachment:** round → captured session (real end-level form↔score data), → competition (§3.11), or standalone "paper session" (counts for streak/load/PB).
- **Derived:** PB per round type, rolling per-distance averages, condition-bucketed averages (§3.10).

**3.4.1 Target photos & arrow plotting (☆).** Per end (or per round), two options, both manual — automatic arrow detection stays out of scope:

- **Keep the picture:** photograph the face; stored with the end (normal storage, downscaled by default), rendered on score cards. The zero-effort floor.
- **Plot mode:** tap arrow positions — on the photo (two-tap calibration: face center + any ring edge → normalized face coordinates) or on a clean standard-face rendering (plotting on the graphic is often faster than on a photo). Either way the output is `(x, y)` per arrow in face-normalized units.

Plotting unlocks **group analysis** — group-center offset (windage/elevation bias), mean radius / extreme spread, drift across ends — and it composes with what already exists: bias direction against the session's **crosswind component** (§3.10), vertical/horizontal bias against **tuning changes** (§3.9.5), per-arrow tags (§3.9.3) → per-arrow group tendency ("#7 groups low-left"). Ring-implied scores are offered as a cross-check against entered values — **suggest-only, never auto-scored** (DECISION-19). Plots are tiny and ride score payloads; photos are never exported by default (per-export attach only).

### 3.5 Wellness, life & personal events (☆)

One logging surface ("+ Log"), entry types:

| Type | Fields | Privacy class |
|---|---|---|
| Wellness check-in | energy, sleep, soreness regions, motivation, RPE/feel, note | shareable (Full tier) |
| Rest day | planned / unplanned, note | shareable |
| Injury | §3.5.1 structured record | shareable (Full tier) |
| Pain log | regions + intensity 0–10 + quality tags | shareable (Full tier) |
| Physio session | linked plan, completed exercises, note | shareable (Full tier) |
| Document | scan/report → encrypted vault | **medical** — per-file explicit only |
| Event | freeform: title, icon, tags ("new string", "tournament") | shareable |
| **Mood** (☆) | standalone mood 1–5, tags (stressed/anxious/flat/great/…), note | **private** |
| **Life event** (☆) | category (bereavement/illness/exams/work/travel/relationship/other), impact 1–3, start, end?/ongoing, title | **private** |
| **Cycle entry** (☆) | §3.5.4 | **private** |
| **Workout** (☆) | §3.14 — S&C / SPT / mobility / mental, catalog exercises | shareable (Full tier) |
| **Medication** (☆) | name, dose, schedule?, taken/skipped | **medical** — per-item explicit only |

**3.5.1 Injury record.** `{id, onset, regions[], severity 1–3, mechanism (acute/overuse/unknown), status (active/recovering/resolved), resolved?, linked_physio_plan?, linked_documents[], notes}`. Active injuries surface on Home and gate readiness.

**3.5.2 Document vault.** SAF/camera import → Jetpack Security `EncryptedFile` (Keystore-wrapped), Room metadata. Never leaves the device unless explicitly attached to a specific export, one file at a time, own confirmation. Viewing decrypts to app-private cache, wiped on background.

**3.5.3 Life layer.** Mood entries and life events become **timeline markers** on every Progress chart — a dip gets its context ("bereavement, impact 3") instead of looking like unexplained regression. Effects: (a) readiness is dampened by active high-impact events; (b) the risk engine **suppresses form-regression flags** inside flagged high-stress windows (context-aware flagging, §3.8); (c) the app never interprets — no sentiment analysis of notes, ever; it renders exactly what was logged.

**3.5.4 Cycle tracking (optional module, off until enabled).** Period start/end, flow, symptom tags; after enough cycles, phase estimate (menstrual/follicular/ovulatory/luteal) with visible uncertainty. Renders as an optional overlay lane on Progress and as readiness context. Framing is **personal-pattern discovery, never prescription** — the sports-science literature is mixed and the copy says so. No predictive notifications by default.

**Privacy classes (data-model-wide law):** `shareable` · `medical` (vault documents · medication — per-item explicit) · **`private` (cycle, mood, life events)**. Private-class data is excluded from **every** export tier; sharing any of it requires a per-export, per-category explicit toggle with its own confirmation — same ceremony as medical documents. The AI coach never receives private-class data unless per-category context sharing is enabled in Settings → AI (§6). Enforced in one place: the `core-exchange` consent filter, unit-tested.

**3.5.5 Hiatus mode (streak shield).** Logging a high-impact life event offers hiatus: streak **frozen** (not broken), all nudges and reminders silenced, Home switches to a quiet state with no gamification pressure. End anytime; the calendar shows the span honestly. Grief-aware copy throughout.

### 3.6 Body map (☆) — unchanged contract from rev 1.0

Two SVGs (anterior/posterior), ~40 tappable regions with **stable string IDs** — the shared contract for pain, injuries, physio, and exchange payloads:

```
Anterior:  neck_ant · delt_ant_l/r · delt_lat_l/r · pec_l/r · biceps_l/r ·
           forearm_flex_l/r · hand_l/r · abs_upper · abs_lower · oblique_l/r ·
           hip_l/r · quad_l/r · knee_l/r · shin_l/r
Posterior: neck_post · trap_upper_l/r · trap_mid_l/r · trap_lower_l/r ·
           rhomboid_l/r · delt_post_l/r · rotator_cuff_l/r · lat_l/r ·
           erector_l/r · triceps_l/r · forearm_ext_l/r · glute_l/r ·
           hamstring_l/r · calf_l/r
```

Tap → intensity dial 0–10 + quality tags (sharp/dull/ache/tingling/stiff); long-press → region history. Views: Today · History (weekly small multiples) · Injuries overlay · Physio overlay.

**Encoding law (hard constraint):** pain = violet luminance ramp fill + numeral (0 transparent → 2 `#2E2354` → 5 `#4A3A8C` → 8 `#6C5BD1` → 10 `#8E7BFF`; brighter = worse). Physio target = cyan `#08FED5` cross-hatch outline. Both = ramp + hatch + split badge. Hue never the sole channel; red/green never semantic.

**Physio plans:** `{id, title, target_regions[], exercises[{name, sets, reps/hold}], schedule, start, end?, source (self/coach), notes}`. Physio as a first-class role = future (§12).

### 3.7 Calendar, rest days, streak (☆)

- Month grid; day icons: session · rest · wellness · score · competition · injury/pain · event · hiatus span. Tap → day sheet.
- **Streak rule:** a day preserves iff `session ∨ rest_logged ∨ (planned_rest ∧ any_checkin)`, **or the day falls inside a hiatus**. One unlogged grace day preserves-but-flags; two break it. Rest is a first-class streak citizen.
- Planned rest weekly pattern in Settings; feeds rule + load model.
- **Load toggle:** weekly load bars + ACWR band overlay.

### 3.8 Progress — the Baseline surface (☆, consumes `baseline` repo)

Tabs: **Stability · Features · Scores · Fatigue · Projection · Risk · Interventions.**

**Load model (`core-wellness`):** `shot_load = arrows × otf_poundage_kg` (primary; pulled from the session's rig), `srpe_load = duration_min × RPE`. ACWR via EWMA (acute λ=2/8, chronic λ=2/29). Zones `<0.8` detraining · `0.8–1.3` sweet · `1.3–1.5` caution · `>1.5` spike — shape + luminance + numerals. **External load** (workouts §3.14, health-sync sessions §3.13) is a parallel lane — `ext_load = duration_min × intensity` (RPE when logged, sync intensity proxy otherwise); readiness and a **combined ACWR** consume both lanes, so yesterday's 10 k run legitimately dampens today's readiness.

**Projection:** Theil–Sen robust linear vs exponential-approach `y = c − d·e^(−t/τ)`, AICc-selected; residual-bootstrap p10/p90 band; 12-week horizon; data gate ≥ 8 sessions across ≥ 4 weeks (else honest insufficient-data state). *Also powers time-to-goal (§3.11).*

**Injury risk (explainable heuristics, not medicine):** inputs = ACWR zone (combined lanes) · 14-day pain slope per region · coupled form-deviation (rising `shoulder_tilt` z + same-side `rotator_cuff_*` pain) · readiness dips · active injuries · **medication masking** (pain logged inside an analgesic/NSAID window is annotated "possibly masked" and never read as improvement). **Context-aware:** flags suppressed or annotated inside high-impact life-event windows (§3.5.3). Output = per-region level 0–3 **with reason strings**. Fixed disclaimer: *"Heuristic flags from your own data. Not medical advice."*

**Interventions (generalized from rev 1.0's rehab-compare):** one engine, three intervention kinds — **physio | equipment | technique**. Anchor = plan start / rig change / tuning change / declared technique change. Pre/post windows (default 21 d each), metrics = pain in linked regions (physio), deviation z of linked features, stability, load-normalized variants; effect = Cliff's delta with plain-language verdict bands. Equipment interventions additionally get a **settling estimate** — sessions until deviation returns inside the personal baseline band (§3.9.5). Timeline renders every intervention marker so no analysis ever pretends the world stood still.

### 3.9 Equipment & inventory (☆) — down to the last arrow

Two-level model: **items** (physical things) assembled into **rigs** (shootable configurations). Sessions reference a rig; analysis reasons about rigs; wear accrues on items.

**3.9.1 Items.** `equipment_item = {id, category, brand, model, spec (category schema), acquired, cost?, status (active/backup/retired), photos?, notes}`. Categories + spec schemas (the "last detail"):

```
riser        size_in · hand · grip · weight_g
limbs        length_in · marked_lbs · fitting (ILF/Formula) · material
string       material (8125/BCY-X/…) · strands · serving · target_brace_mm · install_date
arrow_set    shaft model · spine · cut_length_mm · point_gr · nock · fletch{type,size,angle} · count
plunger      model · spring · notes          rest        model
clicker      model                            sight       model · aperture
stab_long    length_in · weight_oz            stab_side   length_in · weight_oz
extender     length_in                        damper/weights  oz
tab          model · plate size               release     model (compound)
cam/peep/scope (compound)                     armguard · chestguard · quiver · other
```

**3.9.2 Rigs.** `rig = {name, bow_type, slots→item refs, tuning}` where **tuning** = `{riser_length_in, otf_poundage (measured on fingers at the athlete's draw length), brace_height_mm, tiller_top/bottom_mm, nocking_point_mm, plunger_tension, clicker_pos, sight_marks[{distance, mark}]}`. Draw length itself lives on the athlete (it's a body measurement); rigs reference it. Multiple rigs; one active; switchable at session start.

**OTF estimator (recurve/barebow).** Marked limb poundage is a *rating*, specified by ILF convention at **28″ AMO draw on a 25″ riser**. When OTF is unmeasured, the app shows a convention estimate: `marked + 2.0 lbs × (draw_in − 28) + 1.0 lb × (25 − riser_in)` — constants named and editable (**DECISION-21**; limb-bolt position shifts real OTF up to ±10%, which the estimate ignores and admits in copy). Precedence everywhere poundage is consumed: **measured > estimated > marked**. Compound is excluded — cam systems don't scale per-inch; measured or marked only.

**3.9.3 Individual arrows.** Every arrow in a set is its own record `{set_id, number, status, shots, notes}` with an event log (damage/repair/retire — "#7 robin-hooded, retired"). Score-grid tagging (§3.4) yields per-arrow lifetime stats: average score, shot count, incident history. Untagged ends distribute shot counts evenly across the set.

**3.9.4 Wear model.** Wear-tracked categories carry counters: each session increments `shots_lifetime` on the active rig's string, limbs, and arrows (tagged individually or set-averaged). **Service-life defaults (all editable):** string 10,000 shots or 18 months · serving inspection every 2,000 · limbs time + visual inspection · tab ~2 years · per-arrow by incident. Wear renders as % consumed — violet luminance ramp + numeral, per the law — with a Home nudge at 80% ("string at 85% — order a spare") and an inspection log per item.

**3.9.5 Tuning log & acclimatization.** Every tuning-field change = timestamped `tuning_change {field, from, to, reason}`. Component swaps and tuning changes past a threshold mark an **equipment intervention** (§3.8): the intervention engine emits the **new-equipment acclimatization report** — pre/post stability, affected-feature deviations, score trend, and the settling estimate ("acclimatized to the new limbs after ~6 sessions; hold duration still 12% above baseline"). This is the honest answer to "did the new string actually help, or did I just want it to."

### 3.10 Training conditions & weather (☆)

- **Per-session conditions block:** venue ref, environment (indoor/outdoor), `{temp_c, wind_ms, gust_ms, wind_dir_deg, precip, humidity, light (bright/overcast/dusk/artificial), source (fetched|manual)}`.
- **Auto-fetch (opt-in):** **Open-Meteo** — free, keyless, no account, historical + forecast endpoints — fetched at session end (or backfilled by timestamp + venue coordinates). Always presented as *best guess* in the post-session confirm sheet; one-tap correction, because station wind ≠ range wind and the flags don't lie. This is one of the app's only two network calls (§0). **DECISION-09.**
- **Crosswind decomposition:** venues store an optional **shooting azimuth**; wind decomposes into crosswind/headwind components — the numbers archers actually reason about — shown on the session card.
- **Analysis:** condition buckets (calm < 2 · breezy 2–4 · windy > 4 m/s; indoor/outdoor) filter every Progress view; a "70 m average, calm vs windy" card falls out for free. Formal partialling-out of conditions in the correlation engine = later `baseline` work.

### 3.11 Competitions & goals (☆)

- **Competition entity:** `{name, dates, venue?, level (club/district/state/national/international), rounds[] (score_round refs), result {rank?, field_size?}, rig_used, conditions, notes, attachments (non-medical)}`. Calendar entries + the Home countdown chip; a load-vs-competition-date view gives taper awareness (informational — the coach decides).
- **Pressure delta:** competition score vs trailing practice average for the same round type — the clutch/choke number — trended across competitions and per level.
- **Goals:** `{metric (round score | stability), round_type?, target, deadline?, status}`.
- **Time-to-goal indicator:** the projection engine (§3.8) intersects the fitted trajectory with the target → **ETA + p10/p90 band** ("on trend: 560 @ WA 720 around late Sep; band Aug–Dec"). With a deadline: **required rate vs current rate** ("need +2.1 pts/wk; gaining +1.4 — behind pace"). Data gate applies; the app never fakes certainty it doesn't have.

### 3.12 Venues & map (☆)

- **Venue book (local-first):** `{name, type (range/club/shop/physio), lat/lng?, address, shooting_azimuth?, indoor/outdoor, notes}`. Created manually; used by conditions (§3.10) and competitions (§3.11). List shows distance-from-here when location permission is granted.
- **Maps handoff (your "redirect to whatever" instinct, ratified):** every venue → `geo:` URI → whichever maps app the user has (Google, Organic Maps, …). **Discovery** = deep-linked searches: "archery range near me", "archery club near me", "archery shop near me" buttons fire maps-app search intents. Zero infrastructure, always current.
- **`venue_pack` (☆ exchange kind):** a shareable venue list as `.croc` — a coach ships the club's venue book to every archer over WhatsApp; import merges with dedupe on name+coords. **Community POI without a server.**
- In-app tile map (osmdroid, keyless OSM) = v1.1 nicety; v1 is list + distances + deep links. **DECISION-11.**

### 3.13 Device & sensor support (☆)

- **Capture hardware:** CameraX + BlazePose already run on any reasonable Android device — no flagship dependency; document a minimum-viable tier (SD 7-series-class, 1080p30) in Phase 0 notes.
- **Form factors:** adaptive layouts via `WindowSizeClass` — tablet/foldable two-pane matters most in Coach Mode (roster | athlete detail).
- **Wear OS companion (scoped small, DECISION-13):** session remote (start/stop, per-end arrow tick, post-RPE from the wrist) + a **streak/readiness complication** — your watch-face lane. Sync over the Wearable Data Layer: local, no cloud.
- **Health sync via Health Connect (opt-in, read-only v1, DECISION-14):** the "Google Fit sync" answer, done right — the Fit APIs are deprecated (end-of-service 2026) and **Health Connect is their designated successor**, the on-device hub where Fitbit, Samsung Health, Garmin, WHOOP, and cycle-tracking apps already deposit data. Read scopes, each individually toggled: **sleep** (auto-fills check-in sleep quality, source-labeled) · **resting + session HR** (backfill lane for §3.15 — HC is not a live stream) · **exercise sessions, steps, active energy** → the **external-load lane** ("yesterday's 10 k run" enters readiness and the combined ACWR, §3.8), with a one-tap tagged prefill into the workout log (§3.14) · **menstrual-cycle records** (optional feed into §3.5.4 instead of manual entry — private class regardless of source). External integrations happen through Health Connect or not at all — no vendor cloud APIs, ever (**DECISION-17**).
- **Medication log (☆, in-app, medical class):** name, dose, schedule, taken/skipped. Exists for *context*, not adherence-nagging: its one analytical consumer is the risk engine's masking rule (§3.8), and it never leaves the device except by the medical-class ceremony.
- **Generic BLE HR** (standard GATT heart-rate service) as a direct chest-strap fallback.
- **A1 Pro** bow-IMU stays parked in `baseline` as the paid add-on (unchanged).

### 3.14 Archery-specific training log & the Training Clover (☆)

**Workout log.** The entry type for everything that trains the archer but isn't arrows: `{type (strength | SPT | mobility | mental | cardio), duration, sets[{exercise, sets × reps | hold_s, load?}], source (manual | assignment | health-sync), note}`. Backed by a seeded **exercise catalog** where every exercise carries target regions (body-map ID contract) and a **dimension tag** — face pulls, band external rotations, rows, dead hangs, core braces; SPT power holds, reversals, structure holds; thoracic mobility, shoulder CARs; visualization blocks, breath work, gold-game pressure sets. Custom exercises welcome. Completing a coach assignment auto-logs; a synced external session (§3.13) offers a one-tap tagged prefill.

**The Training Clover — daily balance across archery's dimensions.** Four petals, fixed positions, each with an icon and label:

- **Technical** (top) — arrows shot + form-work minutes, vs target
- **Physical** (right) — S&C / SPT volume, vs target
- **Mental** (bottom) — visualization / breathing / pressure-game minutes, vs target
- **Recovery** (left) — sleep hours + rest-plan adherence + mobility minutes, vs target

Fill fraction is the encoding — **geometry, not hue** — so the colorblind law holds by construction (petal identity = position + icon + label; hue stays decorative inside the violet↔cyan gamut). Petal targets are personal, daily and weekly (Settings). Mini clover on Home; full clover with history in Progress. Hiatus (§3.5.5) freezes targets, not the archer.

**Weekly companion:** small-multiples **radar** (one per week, four axes) in Progress — the clover answers *today*, the radar answers *lately*, and dimensional lopsidedness (all Technical, no Recovery) becomes visible across months. **DECISION-16/20.**

### 3.15 Live physiology — heart rate × shot cycle (☆)

The broadcast-finals view, turned into a training instrument: HR measured live and aligned to the shot cycle the segmenter already timestamps.

- **Live sources:** BLE chest strap (standard GATT heart-rate service — best fidelity; RR intervals captured when offered) or the Wear companion streaming over the Data Layer (§3.13). **Health Connect is backfill-only** — it is not a live stream.
- **Live lane:** an HR sparkline on the capture screen during recording — radium glow, on-device by definition.
- **Shot-cycle alignment (post-session):** HR samples joined to the segmenter's phase timestamps → per-shot traces on a release-relative axis (−10 s → +5 s), overlaid with mean ± band. The canonical pattern renders itself: climb through the draw, the **pre-release cardiac deceleration** elite shooters show, recovery between arrows.
- **Metrics:** HR at release · pre-release slope (deceleration = composure) · inter-arrow recovery · session HR drift — each correlatable with arrow score and form deviation via the existing correlation engine.
- **Competition tie-in:** the pressure delta (§3.11) gains its physiological twin — HR-at-release, competition vs practice.
- **Architecture:** the alignment math is sport-agnostic — a `PhaseAlignedSeries` utility lands in `engine/signals` (events in, aligned stats out); archery merely supplies the phase events. HRV proper stays out of v1 (§13) — the RR intervals are banked for it.
- **Sharing:** HR *summaries* ride the Full tier; raw series export is a per-export explicit toggle (**DECISION-18**).

---

## 4. Crocodyl Exchange — backendless sync over any chat app

Signed files moved by whatever transport the humans already use (WhatsApp in practice). No server, no relay, no account.

### 4.1 File format (`.croc`)

```
file = "CROC1" magic (5 bytes) + gzip( header_json + "\n" + payload_json )
```

- **`payload_json`** — content, UTF-8, exact bytes preserved.
- **`header_json`** — `{v:1, kind, created_at, sender:{pubkey, name, role}, payload_sha256, sig}`; `sig` = Ed25519 over `kind + "|" + created_at + "|" + payload_sha256` (detached style — **no JSON canonicalization needed**: the payload bytes shipped are the payload bytes signed).
- MIME `application/x-crocodyl`; filename `{name}-{kind}-{yyyyMMdd}.croc`.
- Import: recompute hash → verify sig → check pinned key (TOFU) → dedupe on `(sender_pubkey, kind, payload_sha256)`.

### 4.2 Kinds

| kind | direction | payload |
|---|---|---|
| `athlete_card` | archer → coach | identity + handedness + club (pairing) |
| `session_report` | archer → coach | per-shot features, deviations, scores, check-ins*, duration, **rig summary + tuning snapshot**, **conditions**, HR summary*, arrow plots |
| `progress_digest` | archer → coach | rolling aggregates, baseline state, clover balance, last-N session summaries, flags, assignment acks |
| `wellness_share` | archer → coach | pain/injury/wellness window* |
| `score_book` | either | rounds + ends (paper sessions supported), competition results, arrow plots |
| `assignment` | coach → archer | drills and/or physio plan (region-ID contract) + due dates |
| `coach_note` | coach → archer | freeform annotation pinned to a session/date |
| `venue_pack` (☆) | either | venue list — club POI sharing (§3.12) |

\* = consent-gated, below.

### 4.3 Consent tiers (chosen at every export; sticky default in Settings)

- **Summary** — aggregates only (stability trend, scores, load).
- **Standard** — + per-shot features and deviations, rig summary, conditions, arrow plots. *Default.*
- **Full** — + wellness check-ins, pain logs, injury records.
- **Medical class (vault documents · medication log):** never bulk-included; per-item, per-export explicit action with its own confirmation.
- **HR raw series & end photos:** per-export explicit toggles (HR *summaries* ride Full; plots ride Standard).
- **Private class (cycle · mood · life events): in no tier, ever.** Sharing any of it is a per-export, per-category explicit toggle with its own confirmation — same ceremony as medical documents. Enforced solely in the `core-exchange` consent filter, unit-tested against every kind. **DECISION-10.**

Digest strategy: coaches mostly consume weekly-ish `progress_digest`s; full reports on request. A season digest is tens of KB gzipped against WhatsApp's 2 GB document cap.

### 4.4 Transport

- **Out:** `ACTION_SEND` + `FileProvider` → system share sheet → WhatsApp / Telegram / Drive / email / Quick Share. Channel-agnostic; offline-capable via Quick Share.
- **WhatsApp rule:** send as **document** (byte-exact); never encode data into images (image channel recompresses).
- **In (three redundant paths):** ① Share-to-app (`ACTION_SEND` receiver for `application/x-crocodyl` + `application/octet-stream`) — primary; ② Open-with (`ACTION_VIEW`, `scheme=content`, octet-stream + the classic `pathPattern` workaround set `.*\\.croc`, `.*\\..*\\.croc`, …); ③ in-app **Import** (SAF picker) — the always-works fallback, on Home (coach) and Settings → Data.
- Import UI: verify → preview card (sender, kind, date, size, tier) → Accept / Quarantine.

### 4.5 Encryption note

v1 = **signed plaintext** (the transport is already a private chat; plaintext keeps debugging honest). v1.1 = optional **encrypt-to-coach** (X25519 sealed box against the coach-card pubkey). **DECISION-04.**

---

## 5. Coach Mode (paid surface)

### 5.1 Roster
Add athletes via QR, first-import auto-offer, or **manual creation** (name only — coach hand-enters scores for club archers not on the app; the score book is useful to the whole club on day one, before a single archer installs anything). Card: avatar, last-report age, readiness shape, sparkline, flag count.

### 5.2 Athlete detail
Tabs mirror the archer app: **Progress · Sessions · Body · Scores · Notes.** Standard-tier reports carry raw features, so the coach app **recomputes locally with the same engine**; session cards show the **rig + tuning snapshot and conditions** they arrived with (a coach reading "windy, new limbs, 3rd session" reads the data differently — that context is the point). Body tab renders only what consent shipped, and says exactly that otherwise.

### 5.3 Flags inbox
Union of digest-shipped and locally recomputed flags, sorted by severity: load spikes, pain trends, deviation coupling, streak breaks, "no report in 14 d", **wear warnings** ("string past service life"). Life-event context arrives only as an anonymous "context: high-stress window" marker *if* the archer shared it — never the event itself by default. Tap → evidence.

### 5.4 Score book
Per-round tables across the roster, PBs, **competition results and pressure deltas**, optional coach-local leaderboard. End-level entry grid for manual athletes. Arrow-plot **group views** (bias + spread trends, rendered against shipped conditions and tuning context) appear for athletes who share plots.

### 5.5 Compare
Overlay ≤ 4 athletes: stability, per-distance scores, load. Squad honesty.

### 5.6 Library & assignment composer
- **Drill library (seeded):** blank-boss form · SPT power holds · SPT flexibility · reversals · bridging · rhythm/metronome ends · close-far alternation · gold game · one-arrow match sim · balance-board stance · band warm-up · letdown discipline — each tagged with form features + muscle regions (shared ID contracts).
- **Physio templates:** region-targeted sets (rotator-cuff band series → `rotator_cuff_r`, `delt_post_r`).
- **Composer:** drills/plan → sets/schedule/due → `assignment.croc` → share sheet. Archer imports → Home card → completions → **acks ride back in the next digest** (`acks:[{assignment_id, completed, dates[]}]`). Closed loop, no server.

### 5.7 Data hygiene
Per-athlete purge, per-kind retention, storage meter. The coach's copy is a cache and the UI says so.

---

## 6. AI coach layer (proposed defaults)

> **DECISION-02:** v1 = (a) end-of-session summary + (b) drill suggestions grounded in the tagged library. No free chat in v1 (small eval surface); chat in v1.1.
> **DECISION-03:** backends = on-device **Gemma via MediaPipe LLM Inference** + **BYOK Anthropic** (default `claude-haiku-4-5`; Sonnet/Opus selectable). More providers later behind the same interface.

- **`CoachModel`** module: `suspend fun coach(input: SessionContext): CoachOutput`; impls `OnDeviceCoachModel`, `AnthropicCoachModel`. Context = structured JSON of features/deviations/scores/flags/conditions/rig-change markers/HR metrics/clover balance — **never frames or video**; **never private-class data** (cycle/mood/life events) unless per-category context sharing is switched on in Settings → AI, in which case only coarse markers ("high-stress window") are passed, not content.
- **Provenance glow:** radium-warm = on-device, alien-cyan = cloud — panel border + per-message origin chip. No unlabeled AI text anywhere.
- **BYOK storage:** `EncryptedSharedPreferences`; paste/test/clear; never in exports, backups, or logs.
- **On-device model management:** download-on-demand (size shown), delete anytime, Wi-Fi-only toggle.
- **Prompt contract:** archery-form-coach role; medical topics deflect to the Risk tab's disclaimer + a professional; drill suggestions must cite library drill IDs; markdown-lite, capped length.
- **Offline:** cloud unreachable → inline on-device fallback offer.

---

## 7. Settings tree (complete)

```
Profile & Identity
  name · avatar seed · handedness · club · draw length (mm)
  show Pairing Card (QR + .croc) · Flex Card studio (stat toggles, render PNG)
  rotate identity key (danger zone)
Equipment
  inventory (items by category, specs, photos, status)
  rigs (slots, tuning block, sight marks) · active rig
  tuning log · service-life defaults (per category) · counters & inspections
Rounds & Scoring
  default round preset · custom rubrics · units (m/yd) · per-arrow tagging on/off
  plot mode default (photo / face / off) · photo downscale quality
Competitions & Goals
  goals (metric, target, deadline) · competition levels list
Training Dimensions
  petal targets, daily + weekly (Technical · Physical · Mental · Recovery)
  exercise catalog (seeded, editable, custom entries)
Venues & Weather
  venue book · auto-fetch weather (opt-in, Open-Meteo — keyless, no account)
  location permission state · shooting azimuth per venue
Capture
  camera · resolution/fps · segmenter sensitivity (advanced)
  keep raw video (default OFF) · storage cap
Wellness & Check-ins
  pre/post items on/off · reminder times · body-map region set
Cycle Tracking
  enable module · phase overlay on/off · (no predictive notifications by default)
Privacy & Sensitive Data
  privacy-class review (what's private/medical/shareable)
  AI context sharing per category (default OFF)
Streak & Rest
  planned rest pattern · grace day on/off · hiatus (start/end)
Coach Mode
  enable (license) · default view · retention per kind · storage meter
Sharing
  default consent tier · filename pattern · auto-attach score book on/off
AI Coach
  provider · model · API key (paste/test/clear) · on-device model (download/delete, Wi-Fi-only)
Devices & Health Sync
  Health Connect scopes, each toggled (read-only): sleep · HR · exercise + steps · cycle
  BLE HR strap pairing · Wear companion
Medication
  entries · schedules (reminders default OFF)
Data
  export everything (.crocbak) · import archive · wipe
  document vault usage
Appearance
  Hyle: glow intensity · reduce motion · haptic strength
Notifications
  check-in reminders · assignment due · streak nudge · wear nudge · medication (all default OFF)
About
  version · licenses · privacy statement (exact network calls enumerated:
  Open-Meteo when enabled, chosen AI provider when BYOK) · repo links
```

---

## 8. Data model — Room additions

Existing: athlete / session / shot (untouched). New (key columns; FK'd to athlete unless noted; `[P]` = private class, `[M]` = medical):

```
athlete            + handedness, pubkey, avatar_seed, club, draw_length_mm
equipment_item     (id, category, brand, model, spec_json, acquired, cost?, status, notes)
arrow              (id, set_item_id, number, status, shots, notes)
arrow_event        (arrow_id, ts, kind[damage|repair|retire], note)
rig                (id, name, bow_type, tuning_json, active)
rig_slot           (rig_id, slot, item_id)
tuning_change      (id, rig_id, ts, field, from, to, reason)
item_counter       (item_id, shots_lifetime, last_inspect_ts)
inspection         (item_id, ts, ok, note)
session            + rig_id, venue_id?, duration_s, conditions_json{…, source},
                     pre_checkin_id?, post_checkin_id?
checkin            (id, ts, kind[pre|post|standalone], energy, sleep, sleep_source,
                     motivation, rpe?, feel?, note)
soreness           (checkin_id, region_id)
pain_log           (id, ts, region_id, intensity, tags[], injury_id?)
injury             (id, onset, regions[], severity, mechanism, status, resolved?,
                     physio_plan_id?, notes)
physio_plan        (id, title, target_regions[], schedule, start, end?, source, assignment_id?)
physio_exercise    (plan_id, name, sets, reps_or_hold)
physio_session     (id, ts, plan_id, completed[], note)
document      [M]  (id, ts, title, mime, enc_path, sha256, injury_id?)
event              (id, ts, title, icon, tags[])
mood_entry    [P]  (id, ts, mood, tags[], note)
life_event    [P]  (id, start, end?, category, impact, title)
cycle_entry   [P]  (id, start, end?, flow, symptoms[])
rest_day           (date, planned, note)
hiatus             (start, end?, life_event_id?)
venue              (id, name, type, lat?, lng?, address?, azimuth_deg?, indoor, notes)
competition        (id, name, start, end, venue_id?, level, rank?, field_size?, rig_id?, notes)
score_round        (id, ts, session_id?, competition_id?, round_type, distance, rubric,
                     total, x_count)
score_end          (round_id, idx, arrows[], arrow_numbers[]?)
goal               (id, metric, round_type?, target, deadline?, status)
workout            (id, ts, type[strength|spt|mobility|mental|cardio], duration_min,
                     source[manual|assignment|health_sync], note)
workout_set        (workout_id, exercise_id, sets, reps?, hold_s?, load?)
exercise           (id, name, type, regions[], dimension)             — seeded catalog
dimension_target   (petal, daily, weekly)
external_activity  (id, start, duration_min, kind, kcal?, source)     — health sync
medication_entry [M] (id, ts, name, dose, schedule?, taken)
end_photo          (round_id, end_idx, path, downscaled, face_calib?)
arrow_plot         (round_id, end_idx, arrow_idx, x_norm, y_norm, source[photo|face])
hr_sample          (session_id, ts, bpm, rr_ms?, source[ble|wear|hc])
assignment         (id, from_pubkey, title, items[], due, status, ack_sent)
roster_athlete     (pubkey PK, name, handedness, pinned_at, manual bool)      — coach
import_record      (sender_pubkey, kind, payload_sha256, ts) UNIQUE           — dedupe
flag               (id, ts, athlete_ref, kind, severity, reason, source, context_note?)
```

Every privacy-class rule keys off the `[P]`/`[M]` markers and is enforced in one place: the `core-exchange` consent filter. Migrations additive throughout; Room and exchange schemas may drift — the versioned mapper in `core-exchange` is the contract.

---

## 9. Modules & repos

```
Form-analyser/                        (this repo)
  engine/                ★ pure JVM, sport-agnostic (+ ☆ signals/: PhaseAlignedSeries)
  archery-module/        ★ pure JVM                          (+ handedness mirror tests)
  core-identity/         ☆ pure JVM + Tink (keys, cards, sign/verify)
  core-exchange/         ☆ pure JVM (croc format, kinds, consent + privacy-class
                            filter, dedupe)
  core-wellness/         ☆ pure JVM (load, ACWR, streak+hiatus, readiness w/ life
                            context — unit-tested against fixture calendars)
  core-equipment/        ☆ pure JVM (spec schemas, wear/service-life math, counters)
  core-health/           ☆ pure JVM (health-sync mappers, external-load math, HR sample types)
  app-android/           ★ Compose app
    feature/{home, capture★, review★, progress, body, calendar, scores,
             competitions, equipment, venues, wellness, life, workouts,
             physiology, coach, ai, settings, exchange-ui, cards}
  wear/                  ☆ Wear OS companion (Phase 12)

baseline/                             (separate repo, pure Kotlin lib)
  projection/    (Theil–Sen, exp-approach, AICc, bootstrap bands)
  risk/          (heuristic engine + reason strings + life-context suppression)
  intervention/  (window compare, Cliff's delta, verdicts; kinds: physio |
                  equipment | technique; settling estimator)
  goals/         (trajectory∩target → ETA band; required-rate vs current-rate)
```

**Consuming `baseline`:** Gradle `includeBuild` for dev; CI pins a published artifact (GitHub Packages). Everything below `app-android` stays headless-testable.

**Distribution:** GitHub **Release** workflow attaches a public no-login `app-debug.apk` to tags — the coach's install path until a Play internal-testing track exists. Required by Phase 5.

---

## 10. Hyle & accessibility law (hard constraints, every phase)

- Tokens: violet `#8E7BFF` · cyan `#08FED5` · AMOLED true black · upper-left key light · ~300 ms cubic-bezier. All surfaces Hyle-agnostic against the token contract.
- **Encoding law:** luminance, shape, pattern, numerals first; violet/cyan the only semantic hue pair; **red/green never semantic** — charts, flags, readiness, ACWR, wear %, body map, everything.
- Provenance glow: radium-warm = on-device compute, alien-cyan = cloud (capture today; AI in §6).
- Touch targets ≥ 48 dp; score grid and check-ins cold-thumb/glove-friendly; haptic tick on log actions; reduce-motion honors system + toggle.
- Flex Card inherits the AGSL shader lane from Hyle; grain and key light per system.

---

## 11. Phased build plan (each phase: CI green · unit tests on all pure modules · APK artifact)

| # | Phase | Scope | Size | Exit criteria |
|---|---|---|---|---|
| 0 | **Range validation** | Device-test checklist for the existing APK: lighting, tripod geometry, segmenter hit-rate log, threshold notes, min-device tier note. | S | First real-session footage analyzed; segmenter verdict documented |
| 1 | **App shell** | Onboarding (+ draw length), handedness mirror + tests, rig v0, Home v1, nav, Settings skeleton | M | LH athlete mirrors correctly in tests; fresh-install → session E2E |
| 2 | **Wellness + life layer** | Check-ins, rest, streak + hiatus, calendar, mood, life events, privacy classes, cycle module, **medication log**, `core-wellness` | M–L | Streak/hiatus/ACWR fixture-tested; private/medical rows flagged in schema |
| 3 | **Body layer** | SVG atlas (region contract), pain logs, injuries, physio plans, encrypted vault | M–L | Region tap→log→history round-trip; vault unreadable outside app |
| 4 | **Equipment depth** | `core-equipment`, inventory + spec schemas, full rigs + tuning block, tuning log, individual arrows, wear counters + nudges, inspections | L | Rig swap emits intervention marker; wear % correct on fixture histories |
| 5 | **Exchange** | `core-identity` + `core-exchange`, export sheet (tiers + private/medical ceremonies), 3 import paths, pairing cards, `venue_pack`, **Release APK workflow** | M | Two phones swap report + card over actual WhatsApp; tamper fails; class-leak tests fail closed for every kind |
| 6 | **Coach mode** | Roster (incl. manual), athlete detail w/ recompute + rig/conditions context, flags inbox, compare, two-pane tablet layout | L | Coach device renders 3 athletes (1 manual) from imports only |
| 7 | **Scores, comps & plotting** | Round presets, per-arrow tagging, PBs, drill library + composer + ack loop, competition entity, pressure delta, **plot mode + end photos + group metrics** | L | Assignment round-trips coach→archer→ack; fixture plot yields correct bias/spread; comp-vs-practice delta renders |
| 8 | **Conditions, venues & weather** | Venue book + maps deep-links, Open-Meteo opt-in fetch + confirm sheet, azimuth/crosswind, condition buckets in Progress | M | Backfilled weather on a fixture session; crosswind math unit-tested; airplane-mode path clean |
| 9 | **Training dimensions** | Workout log, exercise catalog (seeded), Clover + weekly radar, petal targets, mental sessions | M | Fixture day fills correct petals; assignment completion auto-logs a workout |
| 10 | **Baseline surface** | `baseline` repo: projection, risk (context-aware + **medication masking**), intervention engine (3 kinds) + settling; **external-load lane + combined ACWR**; acclimatization report | L | Fixture tests for all engines; "possibly masked" annotation renders; insufficient-data states render |
| 11 | **Goals & cards** | `goals/` (ETA band, required-rate), time-to-goal UI, Flex Card studio + renderer | M | Goal ETA matches fixture projections; card renders both ratios; private-class exclusion tested |
| 12 | **Physiology** | BLE HR live lane, `engine/signals` PhaseAlignedSeries, shot-cycle plots, release metrics (deceleration · recovery · drift), competition HR twin | M | Fixture HR aligns to segmented phases; deceleration metric unit-tested on synthetic traces |
| 13 | **AI coach** | `CoachModel`, Gemma on-device, BYOK Anthropic, settings, provenance glows, private-class firewall; context gains HR metrics + clover balance | M | Summary + drill suggestion from both backends; key and private data never in logs/exports |
| 14 | **Health sync & Wear** | HC scopes (sleep · HR · exercise/steps · cycle), `external_activity` ingestion + tag-it prefill, Wear remote + complication | M | HC fixture session lands in external lane + combined ACWR; sleep autofill source-labeled; wrist arrow tick lands in session |

Order rationale: 0 de-risks everything (thresholds are guesses until real footage). Life layer rides with wellness because privacy classes must exist *before* exchange ships. Equipment precedes exchange (reports carry rig snapshots) and scores (per-arrow tagging needs arrows). The coach's eyeballs are the first analytics engine, so Coach Mode lands before the baseline surface. Dimensions (9) precede baseline (10) so the external-load lane has manually logged workouts to chew on — health sync (14) only deletes the typing later. Physiology follows baseline because its metrics ride the correlation machinery; AI garnishes what everything else grows.

---

## 12. Open decisions register

| ID | Question | Proposed | Status |
|---|---|---|---|
| 01 | Tiering | Archer free forever; Coach Mode paid; sensor add-on later | proposed |
| 02 | AI v1 scope | Summary + drill suggestions; chat v1.1 | proposed |
| 03 | AI backends | On-device Gemma + BYOK Anthropic (Haiku default) | proposed |
| 04 | Encrypt-to-coach | v1.1 (v1 = signed plaintext) | proposed |
| 05 | Region granularity | ~40 regions, rotator cuff unified | proposed |
| 06 | Physio as a role | Out for v1; coach composes plans, archer self-logs | proposed |
| 07 | Coach license mechanics | one-time vs yearly; Play billing vs key file | **open** |
| 08 | crocodyl.app landing timing | — | **open** |
| 09 | Weather provider | Open-Meteo (keyless, no account), opt-in, session-end fetch + backfill | proposed |
| 10 | Private-class export rule | cycle/mood/life-events in **no** tier; per-export per-category ceremony only | proposed |
| 11 | In-app map | v1 = list + `geo:` deep links; osmdroid tile map v1.1 | proposed |
| 12 | Per-arrow tagging UX | optional chip row per end, skippable, never blocking | proposed |
| 13 | Wear scope | session remote + streak/readiness complication only | proposed |
| 14 | Health Connect | read-only (sleep, HR) in v1 | proposed |
| 15 | Service-life defaults | string 10k shots/18 mo, serving 2k inspect, etc. — editable | proposed |
| 16 | Dimensions viz | four-petal Clover daily (geometric fill) + weekly radar small-multiples | proposed |
| 17 | External integrations | Health Connect only — Fit APIs end-of-service 2026; no vendor cloud APIs | proposed |
| 18 | HR sharing | summaries in Full tier; raw series per-export explicit | proposed |
| 19 | Plot score assist | suggest-only cross-check, never auto-score | proposed |
| 20 | Dimension set | Technical · Physical · Mental · Recovery | proposed |
| 21 | OTF estimator constants | 2.0 lbs/in draw (from 28″ AMO) · 1.0 lb/in riser (from 25″) · ±10% bolt caveat | proposed — confirm from coaching practice |

## 13. Explicitly out of scope for v1

Automatic arrow detection on target faces (manual plotting §3.4.1 is in; CV stays out) · real-time skeleton overlay / live per-shot detection · flexibility & ROM protocols · inverse-statics strength in the feature set (needs metric calibration) · HRV analysis (RR intervals are banked; analysis deferred) · A1 Pro BLE wiring · second sport module · web companion / homelab sync · in-app tile maps · sentiment analysis of any user text (privacy stance, permanent) · vendor cloud fitness APIs & wearable SDKs (Health Connect only) · country-specific classification tables · any server, relay, or account system.

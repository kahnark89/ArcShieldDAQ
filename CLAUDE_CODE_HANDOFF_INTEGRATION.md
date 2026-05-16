# Claude Code Handoff — ArcShield Integration Architecture

**Project:** ArcShield (CIAER+ capture platform)
**Document scope:** how the four prior architectural documents compose into one runtime system
**Generated:** 2026-05-16
**Supersedes / extends:** consolidates decisions across `CLAUDE_CODE_HANDOFF.md`, `CLAUDE_CODE_HANDOFF_SENSORY.md`, and `arcshield-detection-spec.md` without replacing them.

---

## Why this document exists

The repository had four architectural documents and they read as three
competing trigger designs:

- `CLAUDE_CODE_HANDOFF.md` — HRV-centric Pixel Watch + Health Connect,
  multi-signal corroboration.
- `CLAUDE_CODE_HANDOFF_SENSORY.md` — five-channel environmental delta
  detection (acoustic, vibration, visual, olfactory, thermal).
- `arcshield-detection-spec.md` — weighted fusion (gaze 0.35 + hand 0.25
  + HRV 0.20 + acoustic 0.20) with dynamic thresholds.
- `CLAUDE.md` — project memory, references the above.

They are not three alternatives. They are three layers of one pipeline.
This document writes that down so the next session starts from a
coherent architecture rather than re-deriving the reconciliation.

---

## The three-layer trigger architecture

### Layer 1 — Pre-ENV baseline (SENSORY handoff)
- **Five environmental channels:** acoustic, vibration, visual,
  olfactory, thermal.
- **Code:** `com.arcshield.app.sensory.*` (16 files, extracted from
  `arcshield_sensory_module.zip` on 2026-05-16 with package rename).
- **Captured once at shift start** as a `SensoryBundle` baseline.
- **Produces deltas at event time** → `SensoryDeltaBundle.toElicitationHints()`
  feeds the PIE prompt builder.
- **Schema:** `PreEnv.sensory_baseline` (added 2026-05-16) and full
  $defs for `SensoryBundle`, `AcousticSnapshot`, `VibrationSnapshot`,
  `VisualSnapshot`, `OlfactorySnapshot`, `ThermalSnapshot`.
- **Role:** *environmental context*. Answers "what changed in the
  operator's surroundings between baseline and the moment the trigger
  fired?"

### Layer 2 — Operator fusion trigger (detection spec)
- **Four operator-state signals:** gaze (0.35), hand (0.25), HRV
  (0.20), acoustic (0.20). Weighted sum → adjusted score → threshold
  (0.65/0.75 with counter and 10 s lockout).
- **Code:** *not yet implemented*. Target package
  `com.arcshield.app.trigger.FusionEngine`. Consumes `BiometricSource`,
  `VisualFrameProvider`, `AcousticChannel`. Emits a
  `Flow<TriggerEvent>` consumed by the state machine.
- **Evaluation cadence:** every 200 ms over a rolling 2 s window.
- **Schema:** `Cause.trigger_context` (added 2026-05-16) with
  `signal_scores`, `temporal_modifier`, `operational_mode`,
  `sensor_context`, `feedback`.
- **Role:** *firing decision*. Answers "did the operator just have an
  insight moment worth interrupting them for?"

### Layer 3 — CIAER+ capture flow (HANDOFF + schema)
- **Five-phase state machine:** Cause → Intuition → Action → Effect →
  Result, with ShadowActions sibling to action, SRK-gated elicitation.
- **Code:** `com.arcshield.app.capture.*` (state machine and screens
  partially in place; specifics TBD).
- **Entered when Layer 2 fires.** At Cause firing, attaches the
  Pre-ENV snapshot (Layer 1) + biometric snapshot + trigger_context.
- **Role:** *structured articulation*. Answers "what was the operator
  thinking, what did they consider rejecting, what happened next?"

### Runtime composition

```
Shift start
  Layer 1 captureBaseline() → SensoryBundle baseline
  Layer 3 ManualPreEnvSource collects operator_id, shift_phase,
          material_batch_id, ambient_temp_f, recent_events_summary
  → PreEnvSnapshot { manual_fields + sensoryBaseline }

Continuous (every 200 ms)
  Layer 2 FusionEngine reads gaze/hand/HRV/acoustic
  adjusted_score = weighted_sum × temporal_modifier
  if score ≥ threshold within window:
    → FIRE

On fire
  Layer 1 captureEventWithDeltas() → SensoryDeltaBundle
  Layer 3 state machine enters CAUSE
    attaches PreEnvSnapshot + delta hints + trigger_context
    progresses I → A (SRK-gated ShadowActions) → E → R
  CiaerPlusEvent → CorpusSink (local Room → background sync to
    Python server when it exists)
```

---

## Resolved conflicts

These were the disagreements between the source documents. Each is now
decided. Future sessions should not re-open them silently.

### C1. Canonical package root: `com.arcshield.app` (not `com.capps.arcshield`)
- All 55 pre-existing Kotlin files use `com.arcshield.app`.
- SENSORY handoff originally specified `com.capps.arcshield`; corrected
  in-place on 2026-05-16. Sensory module extracted with package rewrite.

### C2. Biometric path: dual-path behind `BiometricSource`
- Keep `PixelWatchBiometrics` (Health Connect, vendor neutrality).
- Add `PolarBiometrics` (research-grade raw HRV) as a sibling
  implementation when the fusion engine is built.
- Validate empirically whether Health Connect HRV-RMSSD is usable for
  Layer 2's HRV signal. If not, Polar is primary, Pixel Watch is the
  daily-wear fallback. *Decision deferred until validation data exists.*

### C3. Video capture: `VisualFrameProvider` abstraction (already in place)
- Gen 1: `PhoneCameraFrameProvider` (CameraX + frame diff motion).
- Gen 2: `MetaGlassesFrameProvider` (stub until hardware arrives).
- Known limitation: phone-mounted POV approximates glasses POV — Layer
  2's hand-pose and gaze-fixation analysis will degrade. Documented;
  not a blocker for first deployment.

### C4. Schema extension done: `trigger_context` is on Cause
- The detection spec's per-event fields (signal_scores, temporal_modifier,
  operational_mode, sensor_context, feedback) are now in
  `schema/ciaer_plus_v1.json` under `Cause.trigger_context`.
- This honors "schema is the single source of truth." No forking.

### C5. Fusion runs on-device, not on the backend
- Latency budget is tight (300–500 ms) and the plant network is not
  guaranteed low-latency.
- Graceful degradation when the corpus server is unreachable.
- Math is small (weighted sum + threshold); no need for backend
  round-trip.
- Backend (`arcshield-corpus`) focuses on storage + RAG + later
  training, not real-time inference.

### C6. Single backend repo: `arcshield-corpus`
- Out of scope for this repo. To be created when Python server work
  starts. TimescaleDB (from detection spec) is appropriate for raw
  sensor stream replay; Postgres for CIAER records. Both live in that
  repo.

### C7. Pre-ENV scope: manual fields + sensory baseline (additive)
- HANDOFF's five manual fields (operator_id, shift_phase,
  material_batch_id, ambient_temp_f, recent_events_summary) stay.
- SENSORY's `SensoryBundle` is added as a nullable
  `PreEnvSnapshot.sensoryBaseline` field plus schema $defs.
- Both layers coexist; sensory does not replace manual.

### C8. Documentation: `docs/` folder, reference .docx files moved in
- `docs/` created on 2026-05-16.
- The four reference .docx files moved from repo root into `docs/`:
  - `ArcShield_Whitepaper_v10.1.docx`
  - `ArcShield_Whitepaper_v10_3.docx`
  - `The_Digitization_of_Tacit_Knowledge_v2-1.docx`
  - `arcshield_marketplace_addendum-1.docx`
- The four session-facing `.md` handoff docs stay at repo root (this
  document, `CLAUDE.md`, `CLAUDE_CODE_HANDOFF.md`,
  `CLAUDE_CODE_HANDOFF_SENSORY.md`, `arcshield-detection-spec.md`).

### C9. Helena-West Helena coordinates: lat=34.5294, lon=-90.5912
- Open-Meteo takes lat/lon. SENSORY handoff's values win over
  HANDOFF's ZIP 72390 reference (which Open-Meteo cannot use directly).

---

## What still needs to be built

In priority order:

1. **FusionEngine (Layer 2).** Target package
   `com.arcshield.app.trigger`. New files:
   - `FusionEngine.kt` — weighted-sum loop, threshold evaluator,
     lockout, dynamic threshold adjustment per detection spec §7.5.
   - `TriggerEvent.kt` — sealed type carrying confidence_score,
     signal_scores, temporal_modifier, operational_mode.
   - `OperationalModeDetector.kt` — setup / steady / troubleshooting
     classifier driving mode-based threshold per §7.7.
   - `BaselineTracker` extensions in `com.arcshield.app.bio` for
     gaze / hand / HRV / acoustic rolling baselines per §5.2.
2. **`PolarBiometrics` implementation.** Behind existing
   `BiometricSource` interface. Validates the dual-path decision (C2).
3. **`ChipAndWakeWordAnnotationProvider` + `PhoneCameraFrameProvider`
   + `OpenMeteoThermalProvider`** — the three Gen 1 provider
   implementations the SENSORY handoff §3.1 calls for. Stub the Gen 2
   counterparts.
4. **`SensoryEventRepository`** at
   `com.arcshield.app.data.repository.SensoryEventRepository` — wires
   `SensoryCaptureManager` + `PreEnvSource` + Room DAO + LLM client.
   Computes `graph_weight` per whitepaper §10.12.
5. **Wire `ManualPreEnvSource.captureShiftStart` to call
   `SensoryCaptureManager.captureBaseline()`** and stitch the result
   into `PreEnvSnapshot.sensoryBaseline`.
6. **Backend repo `arcshield-corpus`.** Separate repo. FastAPI +
   Postgres + TimescaleDB. Unblocks `LocalServerSink` and `TwinClient`.
7. **EEG (detection spec Phase 8).** Defer until 200 events captured;
   pure logging mode against the existing capture pipeline.

---

## Critical files touched on 2026-05-16

- `schema/ciaer_plus_v1.json` — added `Cause.trigger_context`,
  `PreEnv.sensory_baseline`, and $defs for `TriggerContext`,
  `SensoryBundle`, `AcousticSnapshot`, `VibrationSnapshot`,
  `VisualSnapshot`, `OlfactorySnapshot`, `ThermalSnapshot`.
- `app/src/main/java/com/arcshield/app/preenv/PreEnvSnapshot.kt` —
  added nullable `sensoryBaseline: SensoryBundle?` field.
- `app/src/main/java/com/arcshield/app/sensory/**` — 16 Kotlin files,
  unpacked from `arcshield_sensory_module.zip`, package rewritten from
  `com.capps.arcshield` to `com.arcshield.app`.
- `docs/` — new folder; four reference .docx files moved in.
- `CLAUDE.md` — rewritten to reflect actual repo state.
- `CLAUDE_CODE_HANDOFF_SENSORY.md` — package references corrected,
  on-disk reality noted.
- `CLAUDE_CODE_HANDOFF_INTEGRATION.md` — this file.

---

## Open questions for Kahn

These came up during reconciliation and remain unanswered:

1. **Pixel Watch HRV usability for fusion.** Detection spec asserts
   consumer wearables hide the signal that Polar exposes. Does Health
   Connect on Pixel Watch 4 expose HRV-RMSSD at sufficient temporal
   resolution for Layer 2's HRV signal? Test path: log raw HRV-RMSSD
   on a worn Pixel Watch during simulated insight moments; compare
   variance to Polar H10.
2. **Olfactory chip vocabulary.** SENSORY handoff §7.2 proposes
   `["burnt PVC", "metallic", "smoke", "normal", "other"]`. Confirm or
   extend for PPVC Line 1.
3. **Wake-word phrase.** SENSORY handoff §7.4 proposes `"smell that"`
   for olfactory. Single phrase or set?
4. **Vibration mount point.** Phone clipped to extruder housing vs
   handheld? Determines baseline noise floor.
5. **arcshield-corpus repo bootstrap.** When does that work start? It
   blocks the Twin and the LocalServerSink wiring.
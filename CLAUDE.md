# ArcShield — Claude Code Project Memory

> **⚠ ALWAYS UPDATE THIS FILE BEFORE ENDING A SESSION.**
>
> Every architectural change, module addition, package rename, schema
> extension, dependency swap, resolved design conflict, or removed
> component must be reflected here before the session ends.
> Specifically: if you change a `.kt` file's package, add or delete a
> module, extend the schema, swap a build dependency, move docs, or
> resolve an open question with Kahn — update the relevant section
> below **and** append a dated entry to the "Change log" at the bottom.
>
> Treat CLAUDE.md as part of the deliverable, not as documentation
> afterthought. The next session's accuracy depends entirely on this
> file matching on-disk reality. Aspirational claims (describing work
> as done when it isn't) waste an entire session's context — verify
> with `ls`, `grep`, or `Read` before writing here.
>
> Same rule applies to the three handoff docs (`CLAUDE_CODE_HANDOFF.md`,
> `CLAUDE_CODE_HANDOFF_SENSORY.md`, `CLAUDE_CODE_HANDOFF_INTEGRATION.md`)
> and any `CLAUDE_CODE_HANDOFF_*.md` added later: when their content
> drifts from reality, fix it in the same commit as the code change.

This file is loaded automatically by Claude Code on every session in this
repository. It establishes the architectural context and points at the
handoff documents and reference materials.

**Before doing anything in this project, read the handoff documents:**
@CLAUDE_CODE_HANDOFF.md
@CLAUDE_CODE_HANDOFF_SENSORY.md
@CLAUDE_CODE_HANDOFF_INTEGRATION.md
@CLAUDE_CODE_HANDOFF_POLAR.md
@arcshield-detection-spec.md

The **integration handoff** reconciles the three trigger architectures
across the other documents into one layered pipeline. The **Polar
handoff** records the 2026-05-16 biometric path swap and supersedes the
Pixel Watch + Empatica framing in the original April handoff and §C2 of
the integration handoff. Read in this order: original handoff →
sensory → detection spec → integration → polar.

## Project at a glance

ArcShield is a native Android app (Kotlin, Compose, Hilt) that captures
expert operator decision-making on industrial manufacturing lines,
structures events into the CIAER+ schema, and syncs them to a Python
corpus server (separate repo `arcshield-corpus`, not yet existing).
Current deployment target is PPVC Line 1 at Hollowell Industries.

**The system is exactly two units:** this Android app and one server
(`arcshield-corpus`). The server holds the event corpus and hosts the
web dashboard used for review, tuning, and validation feedback. The
app does no review UI — capture-only. See "Unified architecture"
under Non-negotiable architectural principles below.

## Non-negotiable architectural principles

1. **CIAER+ schema is the single source of truth.** It lives at
   `schema/ciaer_plus_v1.json`. Kotlin and Python types both generate
   from this file. Never edit generated classes directly. The schema
   was extended on 2026-05-16 with `Cause.trigger_context` and
   `PreEnv.sensory_baseline` — see `CLAUDE_CODE_HANDOFF_INTEGRATION.md`.

2. **Abstraction layers for all device inputs.** Capture source,
   biometric source, corpus sink, LLM provider, PreEnv source,
   sensory channel providers — all behind interfaces. Gen 1
   implementations today; Gen 2 stubs for later. The rest of the app
   does not know what physical device produced a given input.

3. **Biometric trigger is HRV-centric, Polar-only.** Operational
   trigger is HR + HRV-RMSSD + accelerometer-gated activity
   classification + gaze dwell, with multi-signal corroboration
   required. Pixel Watch 4 was evaluated and **removed** on
   2026-05-16: it surfaces HRV only during sleep with no path to raw
   inter-beat intervals, which is fatal for an on-shift fusion
   trigger. `BiometricSource` is now bound to `PolarBiometrics`
   (Polar BLE SDK), which supports both **Polar H10** (chest strap,
   research-grade beat-to-beat HRV) and **Polar Verity Sense**
   (armband, optical HRV + skin temperature). Raw cEDA is not
   exposed by either; reserved for a future Empatica-class device
   added behind the same interface. See
   `CLAUDE_CODE_HANDOFF_POLAR.md` for the swap details.

4. **Local server, not cloud.** Event corpus syncs to a local Python
   server on the plant network. `CorpusSink` abstraction supports
   later cloud migration without touching the app layer.

5. **PreEnv is manually captured on Line 1, plus sensory baseline.**
   PLC is not networked. Manual fields (`operator_id`, `shift_phase`,
   `material_batch_id`, `ambient_temp_f`, `recent_events_summary`)
   come from `ManualPreEnvSource`. Sensory baseline (5-channel
   `SensoryBundle`) comes from `SensoryCaptureManager.captureBaseline()`
   at shift start. `PreEnvSource` interface supports future
   `OpcUaPreEnvSource` for networked facilities.

6. **Twin is RAG-first, LoRA-later.** Android-side Twin client is a
   thin HTTP client to the Python server. All model logic server-side.

7. **Fusion runs on-device, not on the backend.** Layer 2 trigger
   logic (weighted-sum fusion per detection spec §4) executes on the
   phone for latency and offline resilience. Backend role is storage,
   RAG, web dashboard, and later LoRA training only.

8. **Unified architecture: one app + one server, no in-app dashboard.**
   The system is exactly two units. This Android repo is capture-only
   — there is no in-app review UI, no in-app threshold tuning, no
   in-app sensor replay. Operator review, true/false-positive
   labeling, threshold tuning, and analytics all live in the web
   dashboard hosted by `arcshield-corpus`. This keeps the app focused
   on a single role (run on shift, fire fusion, capture CIAER) and
   puts everything that benefits from a desktop browser (sensor
   replay, knowledge graph) where the screen is large enough. The
   server is therefore needed earlier than originally framed — the
   dashboard depends on it. (Decided 2026-05-17.)

For full rationale on each principle, see the handoff documents.

## Three-layer trigger architecture (summary)

The system has one trigger pipeline composed of three cooperating layers.
The full diagram is in `CLAUDE_CODE_HANDOFF_INTEGRATION.md`.

- **Layer 1 — Pre-ENV baseline:** five-channel sensory (acoustic,
  vibration, visual, olfactory, thermal) captured at shift start;
  deltas computed at event time become elicitation hints.
  Code: `com.arcshield.app.sensory.*`.
- **Layer 2 — Fusion trigger:** weighted-sum of gaze + hand + HRV +
  acoustic operator-state signals; threshold crossing fires the state
  machine. Code: `com.arcshield.app.trigger.*` — `FusionEngine`
  ticks every 200 ms, computes
  `0.35·gaze + 0.25·hand + 0.20·hrv + 0.20·acoustic`, fires
  immediately at ≥ 0.75 or after 3 near-hits (≥ 0.60) within 10 s,
  with a 10 s lockout per detection spec §4. Consumes the three
  signal-scorer interfaces (`HrvScorer`, `GazeHandScorer`,
  `AcousticScorer`) — only `DefaultHrvScorer` (driven by
  `PolarBiometrics`) is wired today; the gaze/hand and acoustic
  scorers are bound to no-op implementations until the underlying
  detectors (MediaPipe hand pose + gaze estimation, MFCC anomaly)
  land. With the no-ops in place HRV alone caps the fusion score at
  0.20 and the engine cannot fire — manual capture still works via
  `CaptureViewModel.startCycle()`. (Added 2026-05-17.)
- **Layer 3 — CIAER+ capture flow:** 5-phase state machine
  (Cause → Intuition → Action → Effect → Result + ShadowActions).
  Code: `com.arcshield.app.capture.*`.

## Reference documents

These are consulted when architectural or theoretical questions arise.
Do not improvise on questions these documents answer.

@docs/ArcShield_Whitepaper_v10.1.docx — theoretical foundation,
   arXiv-ready (v10.1)
@docs/ArcShield_Whitepaper_v10_3.docx — current whitepaper draft
   (v10.3, supersedes v10.1 for product-architecture questions)
@docs/The_Digitization_of_Tacit_Knowledge_v2-1.docx — Expert Knowledge
   Transfer framing
@docs/arcshield_marketplace_addendum-1.docx — Twin marketplace
   architecture (out of scope for the Android app; consult only on
   marketplace-related questions)

> Reference docs that the prior CLAUDE.md cited but that do not yet
> exist in this repo: `arcshield_product_architecture.docx`,
> `arcshield_schema_ref.docx`, `arcshield_ekt_spec.docx`,
> `arcshield_hardware_integration_v2.docx`,
> `arcshield_proof_of_method.docx`. If a session needs one of these,
> ask Kahn to add it to `docs/` rather than improvising on its content.

Markdown conversion: `.docx` reads work via the Read tool, but for
heavy referencing `pandoc source.docx -o source.md` gives cleaner
retrieval. Convert lazily, not pre-emptively.

## Development conventions (summary)

- Kotlin 2.0+, Compose BOM current, minSdk 35, targetSdk 36
  (Kahn's personal-use deployment is on current Pixel hardware; the
  industrial-tablet broad-support story from earlier handoffs is no
  longer in scope — see `CLAUDE_CODE_HANDOFF_POLAR.md` §Scope).
- Coroutines and Flow throughout; Polar BLE SDK 7.x exposes native Kotlin
  Flow — no RxJava bridge needed in app code (`rxjava3` and
  `kotlinx-coroutines-rx3` removed from `app/build.gradle.kts` on 2026-05-20).
- Jetpack Compose for UI; no XML layouts.
- Hilt for DI (required for the abstraction layer toggling).
- kotlinx.serialization for JSON (matches existing `@SerialName` usage).
- Room for event queue, DataStore for config.
- **Biometrics**: Polar BLE SDK 7.1.x via JitPack (coordinate
  `com.github.polarofficial:polar-ble-sdk`; settings.gradle.kts declares
  the JitPack repo). The SDK requires `BLUETOOTH_SCAN` (with
  `neverForLocation`) and `BLUETOOTH_CONNECT` runtime permissions —
  declared in `AndroidManifest.xml`.
- Test pure logic as you go; integration tests for the state machine.

## What is NOT in scope for this repo

- Pixel Watch / Health Connect biometrics (removed 2026-05-16 — Pixel
  Watch only reports HRV during sleep, fatal for on-shift trigger).
- Empatica biometrics (removed 2026-05-16; if cEDA validation work
  starts later, add a new `BiometricSource` implementation then).
- Meta glasses capture (stub only until hardware arrives).
- Python corpus server (separate repo `arcshield-corpus`, see C6).
- **In-app review / dashboard / threshold-tuning / sensor-replay UI**
  (removed 2026-05-17 — these all live in the web dashboard hosted by
  `arcshield-corpus`; the Android app is capture-only).
- LoRA training code (server-side only).
- Twin marketplace logic (separate layer, future work).
- EEG / neural pattern discovery (detection spec Phase 8 — defer
  until ≥200 captured events).
- Multi-tenancy, enterprise SOC2, cross-operator aggregation — Kahn
  is the sole operator on his own line for the foreseeable future
  (confirmed 2026-05-16).

## Current repo state (as of 2026-05-17)

**Done:**
- Gradle scaffolding: project + app `build.gradle.kts`,
  `settings.gradle.kts`, `gradle.properties`, `gradlew` wrapper.
- `AndroidManifest.xml` and `MainActivity.kt` (the latter now hosts
  the fusion-trigger lifecycle observer — see below).
- Package skeleton under `com.arcshield.app`: 55+ Kotlin files
  spanning `bio/`, `capture/`, `data/`, `home/`, `llm/`,
  `onboarding/`, `preenv/`, `security/`, `sync/`, `trigger/`,
  `twin/`, `ui/`, `vision/`, `voice/`.
- `schema/ciaer_plus_v1.json` (extended 2026-05-16 with
  `Cause.trigger_context`, `PreEnv.sensory_baseline`, and $defs for
  `TriggerContext`, `SensoryBundle`, `AcousticSnapshot`,
  `VibrationSnapshot`, `VisualSnapshot`, `OlfactorySnapshot`,
  `ThermalSnapshot`).
- Sensory module (Layer 1): 16 Kotlin files at
  `com.arcshield.app.sensory.*` covering channels, providers
  interfaces, FFT processor, bundle / snapshot / delta types, and the
  `SensoryCaptureManager` orchestrator.
- `PreEnvSnapshot.kt` carries `sensoryBaseline: SensoryBundle?`.
- `docs/` folder with reference whitepapers and the marketplace
  addendum.
- **Layer 2 fusion engine** at `com.arcshield.app.trigger.*` —
  `FusionEngine` (200 ms tick, weighted sum, lockout, counter mode),
  `TriggerEvent`, scorer interfaces and impls (see below), Hilt
  module, unit tests under `app/src/test/`. (Added 2026-05-17.)
- **Kotlin/schema sync for `Cause.trigger_context`** — Kotlin
  `Cause` now carries the `triggerContext: TriggerContext?` field
  and the supporting types serialize to the snake_case schema shape.
  Round-trip test under `app/src/test/java/com/arcshield/app/data/schema/`.
  (Added 2026-05-17.)
- **Fusion → state-machine wiring** — `MainActivity` collects
  `FusionEngine.triggers` in a `repeatOnLifecycle(STARTED)` scope and
  dispatches into `CaptureViewModel.fireCauseFromTrigger()`. Screen
  switch is driven by `state.phase`. (Added 2026-05-17.)
- **PolarBiometrics lifecycle wiring** — `PolarPairingScreen` /
  `PolarPairingViewModel` added to onboarding; 10-second BLE scan +
  device selection persists `polar_device_id` to `PreEnvPrefsStore`.
  `BiometricSource.scanForDevices()` added to interface;
  `PolarBiometrics` implements it via `api.searchForDevice(null)` (Flow-native in SDK 7.x).
  `MainActivity` collects `polarDeviceId` with `collectLatest` in a
  `repeatOnLifecycle(STARTED)` block — calls `start(deviceId)` on
  foreground and `stop()` in `finally` on background. Pairing screen
  is shown between LLM setup and HomeScreen when no deviceId persisted.
  (Added 2026-05-17.)
- **Sensory provider implementations** —
  `OpenMeteoThermalProvider` (OkHttp + 15-min cache, Helena-West Helena
  defaults), `ChipAndWakeWordAnnotationProvider` (in-memory annotation,
  vocabulary constant for UI chips), `PhoneCameraFrameProvider` (CameraX
  `ImageCapture` bound to `ProcessLifecycleOwner`, saves JPEG to
  `getExternalFilesDir("frames")`). `SensoryModule` (Hilt) wires all
  three providers plus all five channels into a singleton
  `SensoryCaptureManager`. (Added 2026-05-17.)
- **`DefaultAcousticScorer`** — FFT z-score anomaly scorer wrapping
  `AcousticChannel`; rolling ring buffer of 18 snapshots (~3 min);
  score 1.0 at ≥2σ deviation in spectral centroid or amplitude per
  detection-spec §3.4. Replaces `NoOpAcousticScorer` in
  `FusionEngineModule`. (Added 2026-05-17.)
- **Sensory baseline wiring** — `ArcShieldApp.onCreate()` calls
  `sensoryCaptureManager.initialize()`; `ManualPreEnvSource.recordShiftStart()`
  calls `captureBaseline()` and persists the JSON to `PreEnvPrefsStore`
  (`sensory_baseline_json` key); `PreEnvTracker` decodes and attaches
  it as `PreEnvSnapshot.sensoryBaseline` on every snapshot emission.
  (Added 2026-05-17.)
- **`SensoryEventRepository`** — assembles `CiaerPlusEvent` from
  `CaptureDraft`, computes `graph_weight` (+0.20 hypothesisConfirmed,
  +0.15 PREVENTED/RESOLVED, −0.10 SKILL, clamped [0.10, 1.00]),
  persists to Room via `EventDao`. `CaptureViewModel.submit()` now
  delegates here instead of calling `CorpusSink` directly.
  `CaptureDraft` gains a `graphWeight` field. (Added 2026-05-17.)
- **`OperationalModeDetector` real classifier** — injects
  `PreEnvSource` + `EventDao` + `Json`; returns SETUP when
  `shiftPhase == STARTUP`, TROUBLESHOOTING when any of the last 3
  events within 10 min has `outcomeTag` WORSE or NO_CHANGE, otherwise
  STEADY. `currentMode()` is now `suspend`. (Added 2026-05-17.)

**Not yet built:**
- `GazeHandScorer` real implementation: `PhoneCameraFrameProvider`
  captures frames but MediaPipe hand-pose + gaze estimation not yet
  wired. `NoOpGazeHandScorer` still in place — gaze (0.35) + hand
  (0.25) = 0.60 of fusion weight still dark. Auto-fire impossible
  until this lands.
- `PolarBiometrics.start()` pairing test: code is complete; first
  real test requires physical Polar H10 or Verity Sense hardware.
- `ManualPreEnvSource.captureShiftStart` UI hookup — a shift-start
  screen is needed to call `recordShiftStart()` and trigger the
  sensory baseline capture.
- Dynamic threshold adjustment loop (detection-spec §7.5) — needs
  FP/TP labels from the web dashboard.
- Backend: `arcshield-corpus` server. Two roles: (a) event corpus +
  RAG, (b) web dashboard (event review, sensor replay, threshold
  tuning, FP/TP labeling). Separate repo; not bootstrapped yet.

## Working with Kahn

Kahn directs the build from the Claude mobile app via Remote Control.
He is the author of the CIAER framework and the domain expert on PVC
extrusion. Defer to him on operational questions (what does Line 1
actually do, what does the material look like at batch changeover,
what's the expected capture latency tolerance). Flag architectural
questions for discussion before implementing anything that deviates
from this document or the handoffs.

Open questions awaiting Kahn (see integration handoff §"Open
questions" for full context):
- Olfactory chip vocabulary (initial set: `["burnt PVC", "metallic",
  "smoke", "normal", "other"]` — confirm or extend).
- Wake-word phrase for olfactory annotation (default: `"smell that"`).
- Vibration mount point — phone clipped to extruder housing vs
  handheld? Affects baseline noise floor.
- `arcshield-corpus` repo bootstrap timing and target host.
- Which Polar device(s) Kahn actually has in hand for first pairing
  test (resolved on paper as both, but pairing test requires hardware).

Resolved (do not re-open without flagging):
- Biometric path: Polar only, both H10 and Verity Sense supported.
  Pixel Watch and Empatica are removed. (2026-05-16)

## Change log

- **2026-05-17 (session 2)** — Items 1-5 from priority implementation plan.
  PolarBiometrics lifecycle wired: `PolarPairingScreen` / `PolarPairingViewModel`
  added (BLE scan, device selection, DataStore persistence); `BiometricSource`
  gains `ScannedDevice` + `scanForDevices()`; `PolarBiometrics` implements it
  via `api.searchForDevice(null)` (Flow-native, SDK 7.x); `MainActivity` uses `collectLatest` on
  `polarDeviceId` to call `start()`/`stop()` around the STARTED lifecycle.
  Sensory providers built: `OpenMeteoThermalProvider`, `ChipAndWakeWordAnnotation-
  Provider`, `PhoneCameraFrameProvider` (CameraX + ProcessLifecycleOwner);
  `SensoryModule` (Hilt) wires all into `SensoryCaptureManager` singleton.
  `DefaultAcousticScorer` replaces `NoOpAcousticScorer` in `FusionEngineModule`
  (FFT z-score ring buffer, 18-sample ~3 min baseline, ≥2σ → score 1.0).
  Sensory baseline wired end-to-end: `ArcShieldApp` calls `initialize()`,
  `ManualPreEnvSource.recordShiftStart()` calls `captureBaseline()` + persists
  JSON to `PreEnvPrefsStore`; `PreEnvTracker` decodes and attaches to snapshot.
  `SensoryEventRepository` created: assembles `CiaerPlusEvent` with `graph_weight`
  computation, persists to Room; `CaptureViewModel` delegates to it.
  `OperationalModeDetector` real classifier: SETUP/STEADY/TROUBLESHOOTING based
  on shift phase + recent event outcomes; `currentMode()` now `suspend`.
- **2026-05-17** — Layer 2 fusion engine + architecture lock-in.
  Added `com.arcshield.app.trigger.*` package: `FusionEngine`
  (weighted-sum, 200 ms tick, immediate/counter fire rules, 10 s
  lockout per detection-spec §4), `TriggerEvent`,
  `OperationalModeDetector` (steady-only stub), the three signal
  scorer interfaces (`HrvScorer`, `GazeHandScorer`, `AcousticScorer`)
  with `DefaultHrvScorer` driving off `BiometricSource` /
  `BaselineTracker` and no-op fallbacks for the other two,
  `FusionEngineModule` (Hilt), and unit tests covering no-fire /
  immediate / counter / lockout / null-signal cases. Synced Kotlin
  `Cause` data class to the schema's pre-existing
  `Cause.trigger_context` field — added `TriggerContext`,
  `TriggerType`, `SignalScores`, `OperationalMode`,
  `TriggerSensorContext`, `TriggerFeedback` data classes with
  matching snake_case `@SerialName` mappings; added a serialization
  round-trip test. Wired the engine into the capture state machine:
  `MainActivity` injects `FusionEngine` and collects its `triggers`
  flow inside `repeatOnLifecycle(STARTED)`, dispatching to
  `CaptureViewModel.fireCauseFromTrigger()`. The home↔capture screen
  switch is now driven by observing `state.phase`, so a fire while
  the operator is on HomeScreen pulls them into CaptureScreen
  automatically. Added test deps (`junit`, `kotlinx-coroutines-test`)
  to `libs.versions.toml` and `app/build.gradle.kts`. Locked the
  architecture: one Android app + one server (`arcshield-corpus`),
  no in-app dashboard — operator review and threshold tuning happen
  in the web dashboard hosted by the server. Recorded as
  Non-negotiable principle #8 and an additional "NOT in scope"
  bullet.
- **2026-05-20** — Polar BLE SDK upgraded from non-existent `5.6.0` to
  `7.1.0`. JitPack coordinate corrected from multi-module form
  (`com.github.polarofficial.polar-ble-sdk:sdk`) to single-module form
  (`com.github.polarofficial:polar-ble-sdk`). SDK 7.0.0 migrated the
  Android public API from RxJava to native Kotlin Flow: `rxjava3` and
  `kotlinx-coroutines-rx3` removed from `app/build.gradle.kts` and
  `libs.versions.toml`; `PolarBiometrics.kt` rewritten — `Disposable`
  → `Job`, `.asFlow()` bridge removed, `getAvailableOnlineStreamDataTypes`
  and `requestStreamSettings` called directly as `suspend` functions,
  `startHrStreaming`/`startSkinTemperatureStreaming`/`searchForDevice(null)`
  now return `Flow` natively.
- **2026-05-16 (PM)** — Biometric path swap. Pixel Watch and Empatica
  removed entirely; replaced with `PolarBiometrics` (Polar BLE SDK,
  supports H10 + Verity Sense). Schema `BiometricSnapshot.source_device`
  and `CaptureDevice.biometric_source` enums swapped to
  `polar_h10` / `polar_verity_sense`. Schema `body_response_fired`
  field and `body_response` TriggerChannel removed (Fitbit-specific,
  no replacement on Polar); replaced with `hand_pose` and
  `fusion_threshold` channels for the upcoming FusionEngine. Manifest
  swapped Health Connect permissions for BLE permissions. Gradle:
  JitPack repo added, `health-connect` dropped,
  `polar-ble-sdk` + `rxjava3` + `kotlinx-coroutines-rx3` added.
  New handoff doc: `CLAUDE_CODE_HANDOFF_POLAR.md`. Stronger
  "ALWAYS UPDATE" instruction added at the top of this file.
- **2026-05-16 (AM)** — Integration session. Sensory module
  extracted with package rename. Schema extended with
  `trigger_context` and `sensory_baseline`. `docs/` folder created.
  New `CLAUDE_CODE_HANDOFF_INTEGRATION.md` written reconciling the
  three trigger architectures. CLAUDE.md rewritten to reflect actual
  repo state and document the three-layer architecture.
# ArcShield — Claude Code Project Memory

> **⚠ ALWAYS UPDATE THIS FILE.** Every architectural change, module
> addition, package rename, schema extension, or resolved design conflict
> must be reflected here before the session ends. The next session's
> accuracy depends on this file matching on-disk reality. If you change
> a `.kt` file's package, add a module, extend the schema, move docs,
> or resolve an open question with Kahn — update the relevant section
> below and update the dated "Current repo state" log. Stale CLAUDE.md
> is the single largest source of wasted context in the next session.

This file is loaded automatically by Claude Code on every session in this
repository. It establishes the architectural context and points at the
handoff documents and reference materials.

**Before doing anything in this project, read the handoff documents:**
@CLAUDE_CODE_HANDOFF.md
@CLAUDE_CODE_HANDOFF_SENSORY.md
@CLAUDE_CODE_HANDOFF_INTEGRATION.md
@arcshield-detection-spec.md

The **integration handoff** is the most current — it reconciles the
three trigger architectures across the other documents into one layered
pipeline. Read it last (so the layered view lands after you've seen the
individual designs).

## Project at a glance

ArcShield is a native Android app (Kotlin, Compose, Hilt) that captures
expert operator decision-making on industrial manufacturing lines,
structures events into the CIAER+ schema, and syncs them to a Python
corpus server (separate repo `arcshield-corpus`, not yet existing).
Current deployment target is PPVC Line 1 at Hollowell Industries.

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

3. **Biometric trigger is HRV-centric, not EDA-centric.** Raw cEDA is
   not exposed by Health Connect on Pixel Watch 4. Operational trigger
   is HR + HRV-RMSSD + accelerometer-gated activity classification +
   gaze dwell, with multi-signal corroboration required.
   `BiometricSource` is dual-path: `PixelWatchBiometrics` (Health
   Connect, vendor neutrality) + `PolarBiometrics` (research-grade,
   to be added). See integration handoff §C2.

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
   RAG, and later LoRA training only.

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
  machine. *Not yet implemented.* Target package
  `com.arcshield.app.trigger`.
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

- Kotlin 2.0+, Compose BOM current, minSdk 26 (Android 8, oldest
  relevant industrial-tablet target), targetSdk 35
- Coroutines and Flow throughout; no RxJava
- Jetpack Compose for UI; no XML layouts
- Hilt for DI (required for the abstraction layer toggling)
- kotlinx.serialization for JSON (matches existing `@SerialName` usage)
- Room for event queue, DataStore for config
- Health Connect for biometrics (vendor neutrality is architectural)
- Test pure logic as you go; integration tests for the state machine

## What is NOT in scope for this repo

- Meta glasses capture (stub only until hardware arrives)
- Empatica biometrics (stub only, reserved for validation sub-study)
- Python corpus server (separate repo `arcshield-corpus`)
- Real-time fusion backend (fusion runs on-device — see principle 7)
- LoRA training code (server-side only)
- Twin marketplace logic (separate layer, future work)
- EEG / neural pattern discovery (detection spec Phase 8 — defer
  until ≥200 captured events)

## Current repo state (as of 2026-05-16)

**Done:**
- Gradle scaffolding: project + app `build.gradle.kts`,
  `settings.gradle.kts`, `gradle.properties`, `gradlew` wrapper.
- `AndroidManifest.xml` and `MainActivity.kt`.
- Package skeleton under `com.arcshield.app`: 55+ Kotlin files
  spanning `bio/`, `capture/`, `data/`, `home/`, `llm/`,
  `onboarding/`, `preenv/`, `security/`, `sync/`, `twin/`, `ui/`,
  `vision/`, `voice/`.
- `schema/ciaer_plus_v1.json` (extended 2026-05-16 with
  `Cause.trigger_context`, `PreEnv.sensory_baseline`, and $defs for
  `TriggerContext`, `SensoryBundle`, `AcousticSnapshot`,
  `VibrationSnapshot`, `VisualSnapshot`, `OlfactorySnapshot`,
  `ThermalSnapshot`).
- Sensory module (Layer 1): 16 Kotlin files at
  `com.arcshield.app.sensory.*` covering channels, providers
  interfaces, FFT processor, bundle / snapshot / delta types, and the
  `SensoryCaptureManager` orchestrator. Extracted from
  `arcshield_sensory_module.zip` on 2026-05-16 with package rewrite
  from `com.capps.arcshield`.
- `PreEnvSnapshot.kt` carries `sensoryBaseline: SensoryBundle?`
  (added 2026-05-16).
- `docs/` folder with reference whitepapers and the marketplace
  addendum (moved from repo root 2026-05-16).

**Not yet built:**
- Layer 2 fusion engine (`com.arcshield.app.trigger.*`) — see
  integration handoff §"What still needs to be built" item 1.
- Concrete sensory provider implementations:
  `PhoneCameraFrameProvider`, `ChipAndWakeWordAnnotationProvider`,
  `OpenMeteoThermalProvider`.
- `PolarBiometrics` (sibling to existing `PixelWatchBiometrics`).
- `SensoryEventRepository` wiring.
- `ManualPreEnvSource.captureShiftStart` → `SensoryCaptureManager`
  hook.
- `arcshield-corpus` server repo (separate work).

## Working with Kahn

Kahn directs the build from the Claude mobile app via Remote Control.
He is the author of the CIAER framework and the domain expert on PVC
extrusion. Defer to him on operational questions (what does Line 1
actually do, what does the material look like at batch changeover,
what's the expected capture latency tolerance). Flag architectural
questions for discussion before implementing anything that deviates
from this document or the handoffs.

Open questions awaiting Kahn (see integration handoff §"Open
questions" for full context): Pixel Watch HRV usability validation,
olfactory chip vocabulary, wake-word phrase, vibration mount point,
`arcshield-corpus` repo bootstrap timing.

## Change log

- **2026-05-16** — Integration session. Sensory module extracted with
  package rename. Schema extended with `trigger_context` and
  `sensory_baseline`. `docs/` folder created. New
  `CLAUDE_CODE_HANDOFF_INTEGRATION.md` written reconciling the three
  trigger architectures. CLAUDE.md rewritten to reflect actual repo
  state and document the three-layer architecture.
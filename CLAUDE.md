# ArcShield — Claude Code Project Memory

This file is loaded automatically by Claude Code on every session in this
repository. It establishes the architectural context and points at the
full handoff document and reference materials.

**Before doing anything in this project, read the handoff document:**
@CLAUDE_CODE_HANDOFF.md

## Project at a glance

ArcShield is a native Android app (Kotlin, Compose, Hilt) that captures
expert operator decision-making on industrial manufacturing lines,
structures events into the CIAER+ schema, and syncs them to a Python
corpus server (separate repo, not yet existing). Current deployment
target is PPVC Line 1 at Hollowell Industries.

## Non-negotiable architectural principles

1. **CIAER+ schema is the single source of truth.** It lives at
   `schema/ciaer_plus_v1.json`. Kotlin and Python types both generate
   from this file. Never edit generated classes directly.

2. **Abstraction layers for all device inputs.** Capture source,
   biometric source, corpus sink, LLM provider, PreEnv source — all
   behind interfaces. Gen 1 implementations today; Gen 2 stubs for
   later. The rest of the app does not know what physical device
   produced a given input.

3. **Biometric trigger is HRV-centric, not EDA-centric.** Raw cEDA is
   not exposed by Health Connect on Pixel Watch 4. Operational trigger
   is HR + HRV-RMSSD + accelerometer-gated activity classification +
   gaze dwell, with multi-signal corroboration required.

4. **Local server, not cloud.** Event corpus syncs to a local Python
   server on the plant network. `CorpusSink` abstraction supports
   later cloud migration without touching the app layer.

5. **PreEnv is manually captured on Line 1.** PLC is not networked.
   `PreEnvSource` interface supports future `OpcUaPreEnvSource` for
   networked facilities without touching consumers.

6. **Twin is RAG-first, LoRA-later.** Android-side Twin client is a
   thin HTTP client to the Python server. All model logic server-side.

For full rationale on each of these, see the handoff document.

## Reference documents

These are consulted when architectural or theoretical questions arise.
Do not improvise on questions these documents answer.

@docs/arcshield_whitepaper_v10.1.md - theoretical foundation, arXiv-ready
@docs/arcshield_product_architecture.docx - product architecture,
   four-layer intelligence, Personal AI Twin design, full CIAER+ spec
@docs/arcshield_schema_ref.docx - CIAER+ schema reference with worked
   examples from the April 8, 2026 segregation event
@docs/arcshield_ekt_spec.docx - Expert Knowledge Transfer spec
@docs/arcshield_hardware_integration_v2.docx - hardware architecture
@docs/arcshield_proof_of_method.docx - April 8 CIAER validation event

## Development conventions (summary)

- Kotlin 2.0+, Compose BOM current, minSdk to be confirmed with Kahn
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
- LoRA training code (server-side only)
- Twin marketplace logic (separate layer, future work)

## Current repo state

- `schema/ciaer_plus_v1.json` — complete, validates as JSON Schema
  Draft 2020-12
- `app/src/main/java/com/arcshield/app/preenv/PreEnvSnapshot.kt` — data
  class mirroring the PreEnv section of the schema
- `app/src/main/java/com/arcshield/app/preenv/source/PreEnvSource.kt` —
  interface for PreEnv sources; no implementation yet

Next phase per the handoff: project scaffolding (Gradle build files,
manifest, MainActivity). Do not start until Kahn confirms package
name, minSdk, and default gauge-reader LLM provider.

## Working with Kahn

Kahn directs the build from the Claude mobile app via Remote Control.
He is the author of the CIAER framework and the domain expert on PVC
extrusion. Defer to him on operational questions (what does Line 1
actually do, what does the material look like at batch changeover,
what's the expected capture latency tolerance). Flag architectural
questions for discussion before implementing anything that deviates
from this document.

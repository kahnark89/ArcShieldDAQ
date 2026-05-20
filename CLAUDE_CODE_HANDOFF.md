# ArcShield Android Build — Claude Code Handoff

**Project:** ArcShield — CIAER+ expert tacit knowledge digitization system
**Author/Operator:** Kahn Capps, Capps Family Enterprises, Helena-West Helena, AR
**Deployment target:** PPVC Line 1, Hollowell Industries
**Handoff date:** April 19, 2026
**Source:** This document was produced in a Claude mobile-app session to hand the Android build off to Claude Code on a laptop. The originating chat is not accessible from Claude Code; this document is the complete context needed to continue.

> **⚠ Partially superseded.** This is the original April 2026 handoff. The
> following decisions have evolved since — read **`CLAUDE.md`** for current
> state and the named handoffs for detail:
>
> - **Biometric path:** decision 3 below described Pixel Watch + Empatica
>   behind `BiometricSource`. As of 2026-05-16, **Pixel Watch and Empatica
>   are removed**; only `PolarBiometrics` (H10 + Verity Sense) remains. See
>   `CLAUDE_CODE_HANDOFF_POLAR.md`.
> - **Three trigger architectures:** the SENSORY and detection-spec docs
>   layer with this one — see `CLAUDE_CODE_HANDOFF_INTEGRATION.md`.
> - **"Current repo state"** below was the day-0 starting state; reality
>   has moved considerably. CLAUDE.md tracks current state.
>
> The architectural rationale and conventions in this document are still
> authoritative where not contradicted above.

---

## Who you are and what you're doing

You are Claude Code. You have been handed an Android native (Kotlin) build for ArcShield. Three foundation files already exist in this repo. Your job is to continue building the Android capture app while preserving the architectural decisions made upstream. **Read this entire document before writing any code.** The decisions here are load-bearing — deviating from them silently creates rework later.

The operator (Kahn) will direct you through the Claude mobile app via Remote Control, with this repo as the working directory. Treat his messages as the authoritative source for any decision not pre-committed in this document; treat this document as the authoritative source for decisions that *are* pre-committed here.

---

## What ArcShield is (one paragraph)

ArcShield captures expert operator decision-making on industrial continuous manufacturing lines and structures it into a formal data schema (CIAER+) designed to solve the causal confusion problem in imitation learning. The captured event corpus trains a Personal AI Twin that eventually advises the next generation of operators. The foundational research is documented in the ArcShield whitepaper v10.1 (arXiv-ready); the product architecture is in the companion product architecture doc. Both are in the project knowledge. **Read them if relevant context is unclear.**

---

## CIAER+ schema — the non-negotiable contract

CIAER+ is a five-phase decision event schema extended with two additions:

1. **PRE-ENV** (pre-cause environmental layer). Continuous ambient context (operator_id, shift_phase, material_batch_id, ambient_temp_f, recent_events_summary) that attaches to every event at Cause firing. Not a phase in the state machine — sampled from background trackers.

2. **Shadow Actions.** First-class structured array sibling to `action`. The actions the operator considered and consciously rejected, with rejection rationale. The negative space of expert decision-making — as informative to a training AI as the action taken. Elicitation is SRK-gated (SKILL → skip, RULE → light prompt, KNOWLEDGE → full prompt).

The five cycle phases are Cause → Intuition → Action → Effect → Result.

The full schema is the single source of truth at `schema/ciaer_plus_v1.json`. **Kotlin data classes and Python dataclasses both generate from this file.** If you need to modify the schema, edit the JSON first and propagate. Never edit a generated class and expect the change to survive.

---

## Architectural decisions already locked

These were debated and decided upstream. Do not silently re-open them.

**1. Native Android / Kotlin, not PWA.** The previous single-file PWA couldn't get Health Connect permissions. Current build is Kotlin native targeting modern Android.

**2. Abstraction layers for all device inputs.** Capture source, biometric source, corpus sink, LLM provider, and PreEnv source are all behind interfaces with Gen 1 implementations today and Gen 2 stubs for later. The single most important architectural principle: **the rest of the app does not know what physical device produced a given input.** The UI has toggles in ConfigScreen to swap implementations without touching downstream code.

**3. Biometric trigger is HRV-centric.** *(Original wording preserved; see banner — Pixel Watch + Empatica path removed 2026-05-16, replaced with Polar-only via `PolarBiometrics`.)* The Damasio somatic marker hypothesis predicts EDA would carry the richest signal, but raw cEDA is not exposed by any current consumer wearable. The operational trigger is HR + HRV-RMSSD (Polar beat-to-beat RR intervals) + accelerometer-gated activity classification + gaze dwell. Multi-signal corroboration is required (2+ channels within a calibration window). Raw EDA is reserved for a future research-grade implementation behind the same `BiometricSource` interface. The schema has an `eda_microsiemens` field that stays null until such a device is wired.

**4. Local server, not cloud.** The event corpus lives on a Python server (FastAPI, local on the plant network). Android app syncs events to it; the server runs RAG for the Twin, and later LoRA training. Cloud is a configuration change when second-facility deployments happen. Storage abstraction (`CorpusSink`) is designed for this.

**5. PreEnv is manually captured on Line 1.** Line 1's PLC is not networked so OPC-UA is unavailable. Manual sources: operator_id from login, shift_phase derived from clock + shift start, material_batch_id from barcode/short-code entry at batch change, ambient_temp_f from free weather API (Open-Meteo), recent_events_summary auto-generated from event DB. `crew_state_tag` removed from schema (solo operators on Line 1 made it noise). `PreEnvSource` interface already exists so `OpcUaPreEnvSource` slots in later at a networked facility.

**6. Twin is RAG-first, LoRA-later.** From the first captured event, the Twin retrieves relevant past events and presents them as grounding for a base model's guidance. LoRA fine-tuning activates when the corpus reaches ~200 events. The Android-side Twin client is a thin HTTP client pointing at the Python server; all model logic lives server-side.

**7. Graph weight reflexivity matters.** The schema has `graph_weight` and `withhold_sample` fields on Result. Scaled Twin deployment requires a withhold-sampling policy (documented in whitepaper §10.12) where the Twin randomly declines to offer guidance on a calibrated subset of events, producing counterfactual samples. Not implemented yet but schema-supported.

---

## Current repo state

```
arcshield/
├── schema/
│   └── ciaer_plus_v1.json          ✓ written, validates as Draft 2020-12
└── app/src/main/java/com/arcshield/app/preenv/
    ├── PreEnvSnapshot.kt           ✓ written, matches schema exactly
    └── source/
        └── PreEnvSource.kt         ✓ written, interface only
```

Nothing else exists. No Gradle config, no manifest, no app module, no MainActivity. Starting from essentially zero with a locked schema and two Kotlin files.

---

## Full target repo structure

Build toward this. Order matters — lower in the list depends on higher.

```
ArcShield/
├── build.gradle.kts (project)
├── settings.gradle.kts
├── gradle.properties
├── schema/
│   └── ciaer_plus_v1.json          [exists]
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/arcshield/app/
            ├── MainActivity.kt
            ├── data/
            │   ├── Schema.kt                (generated from ciaer_plus_v1.json)
            │   ├── EventDao.kt
            │   ├── EventDatabase.kt
            │   └── ConfigStore.kt
            ├── preenv/
            │   ├── PreEnvSnapshot.kt        [exists]
            │   ├── source/
            │   │   ├── PreEnvSource.kt      [exists]
            │   │   └── ManualPreEnvSource.kt
            │   ├── PreEnvTracker.kt         (assembles snapshot)
            │   ├── ShiftPhaseDetector.kt    (time-derived, no UI)
            │   ├── MaterialBatchTracker.kt
            │   ├── AmbientTempTracker.kt    (Open-Meteo API)
            │   └── RecentEventsRollup.kt    (queries event DB)
            ├── shift/
            │   ├── ShiftStartScreen.kt
            │   ├── BatchChangeScreen.kt
            │   └── ShiftSession.kt
            ├── capture/
            │   ├── source/
            │   │   ├── CaptureSource.kt     (interface)
            │   │   ├── PhoneCameraSource.kt
            │   │   └── GlassesSource.kt     (stub throwing NotImplementedError)
            │   ├── CaptureViewModel.kt
            │   ├── CaptureScreen.kt
            │   ├── CaptureStateMachine.kt   (5-phase enum-driven)
            │   └── ShadowActionElicitor.kt  (SRK-gated prompting)
            ├── bio/
            │   ├── source/
            │   │   ├── BiometricSource.kt   (interface)
            │   │   ├── PixelWatchBiometrics.kt  (Health Connect)
            │   │   └── EmpaticaBiometrics.kt (stub)
            │   └── BaselineTracker.kt       (personal rolling baseline math)
            ├── vision/
            │   ├── GaugeReader.kt           (multimodal LLM for gauges)
            │   └── FrameAnalyzer.kt         (frame diff for gaze dwell)
            ├── voice/
            │   ├── SpeechInput.kt           (SpeechRecognizer wrapper)
            │   └── TtsSpeaker.kt
            ├── llm/
            │   ├── LlmClient.kt             (interface)
            │   ├── ClaudeProvider.kt
            │   ├── GeminiProvider.kt
            │   └── IntuitionParser.kt
            ├── sync/
            │   ├── CorpusSink.kt            (interface)
            │   ├── LocalSqliteSink.kt
            │   ├── LocalServerSink.kt
            │   └── SyncService.kt
            ├── twin/
            │   ├── TwinClient.kt            (HTTP client)
            │   └── GuidanceRenderer.kt
            ├── events/
            │   └── EventsScreen.kt
            ├── config/
            │   └── ConfigScreen.kt          (device toggles)
            └── ui/
                └── Theme.kt
        └── res/
            ├── values/{strings,colors,themes}.xml
            ├── drawable/ic_launcher.xml
            └── xml/{data_extraction,backup}_rules.xml
```

---

## Recommended build order

The two files already written are `schema/ciaer_plus_v1.json` and the PreEnv snapshot + source interface. Continue in this order — each layer unblocks the next without premature dependencies.

**Phase 1 — Project scaffolding.** Generate the Gradle build files (`build.gradle.kts` project + app, `settings.gradle.kts`, `gradle.properties`), `AndroidManifest.xml` with Health Connect + camera + microphone + internet permissions, `MainActivity.kt` with a Jetpack Compose Scaffold host. Kotlin 2.0+, Compose BOM current, minSdk 26 (Android 8, oldest that matters for industrial tablets), targetSdk 35.

**Phase 2 — Rest of PreEnv subsystem.** `ShiftPhaseDetector` (pure time math, no dependencies), `MaterialBatchTracker` (holds current batch + timestamp, persisted in DataStore), `AmbientTempTracker` (Open-Meteo poll every 15 minutes, fallback to last-known), `RecentEventsRollup` (queries Room for last N events, template-based summary — LLM summarization later). Then `PreEnvTracker` composes them, then `ManualPreEnvSource` implements the interface using the tracker. Shift and batch screens wire the UI.

**Phase 3 — Storage.** Room database for event queue, DAO, `ConfigStore` on DataStore. `CorpusSink` interface with `LocalSqliteSink` writing to Room and `LocalServerSink` as a stub until the Python server exists.

**Phase 4 — Capture state machine.** `CaptureStateMachine` with a sealed class hierarchy or enum for the five phases, `CaptureViewModel` holding current state, `CaptureScreen` as the primary UI. At Cause firing, calls `preEnvSource.currentSnapshot()` and attaches. Shadow Action elicitor runs at Intuition-to-Action transition, gated on SRK level.

**Phase 5 — Capture source abstraction.** `CaptureSource` interface, `PhoneCameraSource` using CameraX, `GlassesSource` stub. Device toggle in `ConfigScreen`.

**Phase 6 — Biometrics.** `BiometricSource` interface, `PixelWatchBiometrics` implementing Health Connect reads for HR, HRV-RMSSD, accelerometer-derived activity. `BaselineTracker` maintains rolling personal baselines. Multi-signal corroboration logic lives in the state machine's trigger arming, not in the source — keep the source simple.

**Phase 7 — Vision, voice, LLM providers.** `GaugeReader` calls out to Claude/Gemini with the Cause frame for instrument value extraction. `SpeechInput` wraps Android SpeechRecognizer for Intuition-phase voice capture. `LlmClient` interface with provider implementations — used for both gauge reading and later for RecentEventsRollup summarization.

**Phase 8 — Twin client stub.** `TwinClient` as an HTTP client against a future Python server. `GuidanceRenderer` is a no-op UI until the server exists. Shipped early so capture flow can include a "query Twin" button that degrades gracefully.

**Phase 9 — Sync service.** Background worker that drains the Room queue into `LocalServerSink` when connectivity allows.

---

## Development conventions

**Coroutines and Flow throughout.** `StateFlow` for observable state, `suspend fun` for one-shot operations. No RxJava, no callbacks where coroutines would do.

**Jetpack Compose for UI.** No XML layouts except the strictly required manifest backup/extraction rule files.

**Hilt for dependency injection.** The abstraction layers require it — toggling from `PhoneCameraSource` to `GlassesSource` at runtime is cleanest with DI. Set up Hilt modules in Phase 1.

**kotlinx.serialization for JSON.** Do not use Gson or Moshi. The `PreEnvSnapshot.kt` already uses `@Serializable` and `@SerialName` mappings that produce snake_case JSON matching the schema.

**Room for local persistence, DataStore for config.** Room for the event queue (structured data), DataStore (Preferences or Proto) for config values like operator_id, API keys, device toggles, Open-Meteo ZIP code.

**Health Connect, not Samsung Health or vendor SDKs.** Vendor neutrality is architectural — see whitepaper §5.3.

**Testing as you go.** Unit tests for pure logic (`ShiftPhaseDetector`, `BaselineTracker`, schema serialization round-trips). Integration tests for the state machine. The capture flow is complex enough that untested refactors will silently break it.

**Commit messages.** Short imperative mood. Reference the phase from this document when relevant: `Phase 2: add MaterialBatchTracker with DataStore persistence`.

---

## What to check with Kahn before committing

These are things that could go several ways and deserve a quick confirmation before you proceed:

- **package name and app ID.** The current Kotlin files use `com.arcshield.app`. If Kahn wants a different package (e.g., `com.cappsfamilyenterprises.arcshield`), change now before the codebase grows.

- **Minimum Android version.** I wrote minSdk 26 above. If Kahn is on newer-only hardware at Hollowell, minSdk 29 or 30 simplifies a lot. Ask.

- **LLM API keys.** `ConfigStore` needs a home for Claude and Gemini API keys. Confirm with Kahn whether these go in DataStore (per-install) or are fetched from a server (per-operator). Security-wise, neither is great with a single API key scope; let him decide the posture.

- **Open-Meteo ZIP code.** Hollowell is in Helena-West Helena, AR (72390). Hardcode or make configurable?

- **Gauge reader LLM provider default.** Whitepaper §9.1 mentions both Claude and Gemini have been tested. Confirm which is the current default.

- **Anthropic API SDK for Android.** As of this handoff's date, the official SDK options may have evolved. Verify before choosing how `ClaudeProvider` calls the API. Do not invent an SDK that doesn't exist — use raw HTTP with OkHttp if no Android SDK is current.

---

## What NOT to do

- **Do not build glasses capture yet.** Kahn does not have Meta glasses. `GlassesSource` is a stub that throws `NotImplementedError`. When the glasses arrive the stub becomes real; the rest of the app does not change.

- **Do not implement Empatica biometrics yet.** Same reason. Stub only.

- **Do not implement the Python corpus server.** That's a separate repo (`arcshield-corpus`) that does not exist yet. `LocalServerSink` can be a stub until it does.

- **Do not run LoRA training code anywhere in the Android app.** Training happens on the server, not the phone.

- **Do not add crew_state_tag back to PreEnv.** It was explicitly removed. If Kahn changes his mind later it comes back via schema edit, not by you quietly adding it.

- **Do not add EDA as a primary trigger channel.** It's an aspiration tied to the Empatica validation sub-study, not an operational trigger. The whitepaper §5.2 pinned this decision.

- **Do not hardcode ambient_temp_f from a sensor.** Gen 1 is weather API only. `AmbientTempTracker` should take a source enum (`WEATHER_API` / `SENSOR` / `MANUAL` / `UNAVAILABLE`) and report which it used.

- **Do not drift the schema between Kotlin and the JSON file.** If you change one and not the other, Python and Android sides will silently diverge at the first sync.

---

## Reference documents

Claude Code reads files from the local repository directory — it does not
have access to Claude.ai project knowledge. For the reference materials
below to be available to Claude Code, Kahn needs to copy them from his
Claude.ai project into a `docs/` folder at the repo root. Once copied,
the top-level `CLAUDE.md` file automatically loads them into every session
via `@docs/filename` imports.

If a document is not present in `docs/`, Claude Code does not have access
to it and should ask Kahn rather than improvising on its content.

When in doubt about any architectural or theoretical question, consult
these first rather than improvising:

- `docs/arcshield_whitepaper_v10.1.md` (or `.pdf`) — the theoretical
  foundation and system design. The arXiv-ready paper, most recent version.
- `docs/arcshield_product_architecture.docx` — product architecture,
  including the full CIAER+ schema specification, four-layer intelligence
  architecture, and Personal AI Twin design.
- `docs/arcshield_schema_ref.docx` — CIAER+ schema reference with worked
  examples from the April 8, 2026 segregation event.
- `docs/arcshield_hardware_integration_v2.docx` — hardware architecture
  (Meta glasses + Pixel Watch 4 + earbuds + ANSI Z87.1 safety glasses).
- `docs/arcshield_ekt_spec.docx` — Expert Knowledge Transfer specification,
  covers how CIAER data flows into AI systems.
- `docs/arcshield_marketplace_addendum.docx` — Twin marketplace architecture
  (out of scope for this Android build; read only if marketplace-related
  questions arise).
- `docs/arcshield_proof_of_method.docx` — documents the April 8 CIAER
  demonstration that validated the methodology.

Note on .docx files: Claude Code can read them with the `file` or `view`
tool, but for heavy referencing, converting to Markdown with
`pandoc source.docx -o source.md` gives cleaner retrieval and better
inline searchability. Whitepaper v10.1 is already in Markdown form — prefer
that version over the PDF.

---

## First message to send Kahn

After reading this document, respond to Kahn with something like:

> I've read the handoff. I see the schema and two Kotlin files already in place. Before I start Phase 1 scaffolding, I want to confirm three things: (1) package name — keep `com.arcshield.app` or change? (2) minSdk — 26 to support older industrial tablets, or bump to 29/30? (3) Default gauge-reader LLM — Claude or Gemini? Once you answer I'll generate the Gradle files, manifest, and MainActivity, and we'll have a compiling project.

Do not start writing Gradle files without those three answers. Saves rework.

---

## End of handoff

Everything above is authoritative for decisions made in the source conversation. Anything not covered is open for you and Kahn to decide in Claude Code. Good luck, and remember: the architecture pays off over time, not in the first week. The temptation to shortcut the abstractions will be strong. Resist it.

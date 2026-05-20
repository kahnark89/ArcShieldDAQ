# Claude Code Handoff — ArcShield Sensory Module

**Project:** ArcShield (CIAER+ capture platform)
**Module:** `com.arcshield.app.sensory`
**Generated:** Monday, May 11, 2026 — updated May 16, 2026
**Target package root:** `app/src/main/java/com/arcshield/app/sensory/`

> **2026-05-16 update:** package root reconciled from `com.capps.arcshield`
> to `com.arcshield.app` to match the on-disk codebase. The 16-file
> sensory tree (channels, providers, FFT, bundle/snapshot/delta types)
> has been extracted from `arcshield_sensory_module.zip` and the package
> declarations rewritten. The ZIP's `data/` files (`CiaerEvent.kt`,
> `PreEnvSnapshot.kt`, `PreEnvSource.kt`) were **not** extracted —
> the on-disk `com.arcshield.app.preenv.*` and
> `com.arcshield.app.data.schema.CiaerPlusEvent` are more elaborated.
> `PreEnvSnapshot` now carries a nullable `sensoryBaseline: SensoryBundle`
> field; the schema gained matching `sensory_baseline` and SensoryBundle
> $defs. See `CLAUDE_CODE_HANDOFF_INTEGRATION.md` for the three-layer
> reconciliation.

---

## 1. What's in this bundle

19 Kotlin files implementing the five-channel Pre-ENV sensory layer:

```
sensory/
├── fft/
│   └── FFTProcessor.kt                  # Cooley-Tukey FFT, dom freq, centroid, 32-bin histogram
├── SensoryChannelId.kt                  # enum: ACOUSTIC, VIBRATION, VISUAL, OLFACTORY, THERMAL
├── ChannelAvailability.kt               # enum: AVAILABLE, PERMISSION_DENIED, ...
├── SensorySnapshot.kt                   # 5 @Serializable snapshot data classes + sealed interface
├── SensoryDelta.kt                      # 5 delta types + SensoryDeltaBundle.toElicitationHints()
├── SensoryBundle.kt                     # 5-channel snapshot at instant + deltaFrom() + thresholds
├── SensoryChannel.kt                    # interface — initialize/captureBaseline/captureEvent/release
├── SensoryCaptureManager.kt             # parallel orchestration, merged delta stream, annotation routing
├── channels/
│   ├── AcousticChannel.kt               # AudioRecord + FFT, 44.1 kHz, 500 ms window
│   ├── VibrationChannel.kt              # SensorManager TYPE_ACCELEROMETER + FFT, 1000 ms window
│   ├── VisualChannel.kt                 # delegates to VisualFrameProvider
│   ├── OlfactoryChannel.kt              # annotation-based + VOC sensor path via provider
│   └── ThermalChannel.kt                # Open-Meteo ambient + IR sensor path via provider
└── providers/
    ├── VisualFrameProvider.kt           # interface — Gen 1 phone camera, Gen 2 Meta glasses
    ├── OlfactoryAnnotationProvider.kt   # interface — chips/wake word + VOC sensor
    └── ThermalAmbientProvider.kt        # interface — Open-Meteo + BT IR thermometer

```

The ZIP's `data/` files are NOT extracted — the on-disk
`com.arcshield.app.preenv.PreEnvSnapshot`, `…preenv.source.PreEnvSource`,
and `…data.schema.CiaerPlusEvent` supersede them. `PreEnvSnapshot` was
updated in-place to carry a `sensoryBaseline: SensoryBundle?` field.

---

## 2. Architectural principles (do not violate)

- **Provider abstraction** — `VisualChannel`, `OlfactoryChannel`, `ThermalChannel` never touch hardware directly. Gen 1 → Gen 2 hardware swap = swap the provider implementation only. Channel API stays frozen.
- **Delta-first** — every channel exposes a baseline snapshot at shift start and produces deltas at event time. `SensoryDeltaBundle.toElicitationHints()` is the PIE prompt anchor.
- **Shadow Actions are top-level** — `CiaerEvent.shadowActions: List<ShadowAction>` sibling to `action`, not nested inside it. SRK-gated elicitation policy applies at UI layer (skip SKILL, light RULE, full KNOWLEDGE).
- **Channel failures degrade gracefully** — a null snapshot from one channel doesn't block bundle assembly. Capture continues with remaining channels.
- **No localStorage/sessionStorage / no continuous mic stream** — capture is window-based. AudioRecord opens only at capture time.

---

## 3. What still needs to be built

### 3.1 Concrete provider implementations (4 files)

Drop into `sensory/providers/impl/`:

| Provider | Implements | Gen 1 backing |
|---|---|---|
| `PhoneCameraFrameProvider` | `VisualFrameProvider` | CameraX rear camera + frame-diff motion stream (already prototyped in old PWA) |
| `ChipAndWakeWordAnnotationProvider` | `OlfactoryAnnotationProvider` | SpeechRecognizer "smell that" wake word + UI quick-select chip ingress |
| `OpenMeteoThermalProvider` | `ThermalAmbientProvider` | OkHttp call to `https://api.open-meteo.com/v1/forecast?...&temperature_unit=fahrenheit` cached at 15-min interval |
| (optional Gen 2 stubs) | `MetaGlassesFrameProvider`, `VocSensorAnnotationProvider`, `BluetoothIrProvider` | Empty TODO bodies — wire up post-Meta toolkit GA |

### 3.2 Repository layer

`data/repository/SensoryEventRepository.kt` (target package
`com.arcshield.app.data.repository`):
- Wires `SensoryCaptureManager` + `PreEnvSource` + Room DAO + LLM client.
- `assembleEvent(intuition, action, shadowActions, effect, result): CiaerPlusEvent`
- Computes `graph_weight` from tenure / outcome / srk_confidence per §10.12.
- Updates `PreEnvSource.recentEventsSummary` after each event.

### 3.3 PreEnvSource default impl

Wire `ManualPreEnvSource` (already on disk at
`com.arcshield.app.preenv.source.ManualPreEnvSource`) to call
`SensoryCaptureManager.captureBaseline()` on shift start and stitch the
result into `PreEnvSnapshot.sensoryBaseline` (field added 2026-05-16).

---

## 4. Tuning constants (must calibrate on PPVC Line 1)

`SensoryBundle.Companion` holds the significance thresholds (0..1 normalized magnitude):

```kotlin
ACOUSTIC_THRESHOLD = 0.30f    // 50 Hz freq shift OR 5 dB amplitude shift → mag 1.0
VIBRATION_THRESHOLD = 0.30f   // 2 m/s² RMS shift → mag 1.0
VISUAL_THRESHOLD = 0.25f      // motion_score 0..1 + luminance/50 → mag
OLFACTORY_THRESHOLD = 0.50f   // annotation change → mag 1.0 (high to avoid noise)
THERMAL_THRESHOLD = 0.25f     // 20°F shift → mag 1.0
```

After first 50–100 baseline runs on PPVC Line 1, surface the false-positive rate per channel and re-tune. False positives are far costlier than false negatives at this stage — the operator can always manually trigger.

---

## 5. Required dependencies (build.gradle.kts app module)

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
```

Required permissions (`AndroidManifest.xml`):

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature android:name="android.hardware.sensor.accelerometer" android:required="true" />
```

---

## 6. Wiring sketch (Application / DI layer)

```kotlin
val phoneCameraProvider = PhoneCameraFrameProvider(context, lifecycleOwner)
val chipProvider = ChipAndWakeWordAnnotationProvider(context, speechRecognizer)
val openMeteoProvider = OpenMeteoThermalProvider(lat = 34.5294, lon = -90.5912)  // Helena-West Helena

val captureManager = SensoryCaptureManager(
    acousticChannel = AcousticChannel(context),
    vibrationChannel = VibrationChannel(context),
    visualChannel = VisualChannel(phoneCameraProvider),
    olfactoryChannel = OlfactoryChannel(chipProvider),
    thermalChannel = ThermalChannel(openMeteoProvider)
)

captureManager.initialize()                          // shift app open
val baseline = captureManager.captureBaseline()      // operator hits "Start Shift"
preEnvSource.captureShiftStart(...)                  // wraps baseline into PreEnvSnapshot

// On trigger:
val (event, deltas) = captureManager.captureEventWithDeltas()
val hints = deltas?.toElicitationHints() ?: emptyList()
// → feed hints into PIE prompt builder
```

---

## 7. Open questions for Kahn

1. **PPVC Line 1 facility lat/lon** — confirmed 34.5294, -90.5912 for Helena-West Helena? Used by `OpenMeteoThermalProvider`.
2. **Olfactory quick-select chip vocabulary** — initial set: `["burnt PVC", "metallic", "smoke", "normal", "other"]`. Confirm or extend.
3. **Visual frame storage path** — currently assumes `context.getExternalFilesDir(...)/frames/`. Confirm or change to scoped storage.
4. **Wake-word phrase** — `"smell that"` for olfactory. Single phrase or multi-phrase set?
5. **Vibration mount point** — phone clipped to extruder housing vs handheld? Affects baseline noise floor and threshold tuning.

---

## 8. Reference docs

- `ArcShield_Whitepaper_v10_2.docx` §5.3 (Developer SDK Stack), §6 (Architecture)
- `arcshield_hardware_integration_v2.docx` §7 (Schema Field Mapping)
- `arcshield_schema_ref.docx` (CIAER+ JSON Schema)

---

## 9. File persistence note

These files do not persist across Claude.ai chat sessions. Save the ZIP locally before closing this conversation. The conversation transcript itself can be re-searched via past-chat tools if a regenerate is needed.

# ArcShield Real-Time Insight Detection System
## Technical Specification & Handoff Document

**Author:** Kahn Capps
**Date:** May 2026
**Version:** 1.0
**Target:** Personal cognitive capture system for industrial operations
**Build Tool:** Claude Code

---

## 1. System Overview

ArcShield is a multimodal sensor fusion system that detects moments of deliberate cognition ("insight moments") in real time during industrial operations, and automatically prompts the operator to articulate what they just noticed. This converts tacit knowledge into structured CIAER (Cause, Intuition, Action, Effect, Result) records tied to the precise biometric and environmental conditions of the moment.

**Core Innovation:** Instead of requiring the operator to manually trigger knowledge capture (high cognitive burden, low compliance), the system passively monitors gaze, biomarkers, hand interaction, and acoustic environment, and automatically prompts at the moment of recognition.

---

## 2. Hardware Stack

| Component | Purpose | Data Output |
|-----------|---------|-------------|
| Meta AI Glasses | POV video, audio capture, prompt display | 30fps video, 16kHz audio |
| Polar H10 (or Verity Sense w/ skin temp) | Biometric telemetry | Beat-to-beat HR, RMSSD, skin temp |
| Backend Server | Inference, fusion, storage | API + DB |
| Phone (optional) | Accelerometer, gyro backup | Motion data |

---

## 3. Signal Definitions

### 3.1 Gaze Fixation (POV Video)
- **Measurement:** Eye gaze remains within 5° cone of visual space for 500ms+, saccade velocity <30°/sec
- **Source:** Meta glasses video, processed via OpenGaze or MLKit gaze estimation
- **Score Range:** 0 to 1.0
  - 1.0 = sustained fixation on equipment (die, gauge, screw)
  - 0.5 = fixation on neutral areas
  - 0 = active scanning

### 3.2 Hand-Object Interaction (POV Video)
- **Measurement:** Hand velocity <0.2 m/s AND hand within 30cm of equipment for 1+ second
- **Source:** Pose estimation from glasses video (MediaPipe or similar)
- **Score Range:** 0 to 1.0
  - 1.0 = exploratory touch pattern (data gathering)
  - 0.5 = neutral hand position
  - 0 = rapid adjustment motion (decisive action)

### 3.3 Heart Rate Variability Shift (Polar)
- **Measurement:** RMSSD drops >20% from rolling 2-min baseline OR LF/HF power ratio >1.5x baseline
- **Source:** Polar API, beat-to-beat data
- **Score Range:** 0 to 1.0
  - 1.0 = clear HRV shift detected
  - 0.5 = borderline
  - 0 = stable

### 3.4 Acoustic Anomaly (Glasses Microphone)
- **Measurement:** MFCC features deviate >2 standard deviations from rolling 3-min baseline (500Hz-5kHz band)
- **Source:** Glasses microphone, processed via librosa
- **Score Range:** 0 to 1.0
  - 1.0 = clear anomaly detected
  - 0.5 = marginal
  - 0 = normal

### 3.5 Temporal Context (Modifier, Not Standalone Signal)
- **Measurement:** Rolling 30-minute activity profile
- **Application:** Multiplier (0.8x to 1.2x) applied to final confidence score
- **Purpose:** Normalize for operator state — fatigue, time-of-day, operational mode

---

## 4. Fusion Logic

### 4.1 Confidence Score Formula

```
confidence = (0.35 × gaze) + (0.25 × hand) + (0.20 × hrv) + (0.20 × acoustic)
adjusted_score = confidence × temporal_modifier
```

### 4.2 Weighting Rationale

| Signal | Weight | Rationale |
|--------|--------|-----------|
| Gaze | 0.35 | Most reliable indicator of deliberate attention |
| Hand | 0.25 | Good specificity, somewhat noisy |
| HRV | 0.20 | Sensitive, slower to register |
| Acoustic | 0.20 | Environmental context, occasional false positives |

### 4.3 Trigger Thresholds

```
IF adjusted_score >= 0.75 within a single 2-second window:
    TRIGGER PROMPT (immediate)

ELSE IF adjusted_score >= 0.60 with counter hits 3+ within 10 seconds:
    TRIGGER PROMPT (accumulated)

ELSE:
    LOG, do not fire
```

### 4.4 Lockout

After triggering, suppress further prompts for 10 seconds to prevent prompt spam during a sustained insight moment.

### 4.5 Time Window

Maintain a rolling 2-second buffer of all four signal scores. Evaluate every 200ms (5 evaluations per second).

---

## 5. Baseline Strategy

### 5.1 Initial Calibration
- **First 15 minutes of shift:** Logging-only mode, no prompts fire. System learns baseline patterns.
- **Minutes 15-45:** "Warm" mode, prompts only fire on strong signals (0.75+).
- **After 45 minutes:** Full detection enabled (0.65+ threshold).

### 5.2 Rolling Baseline Updates

| Component | Update Window | Decay Rate |
|-----------|---------------|------------|
| Gaze Baseline | 30 seconds | 0.70 |
| Hand Baseline | 30 seconds | 0.70 |
| HRV Baseline | 2 minutes | 0.80 |
| Acoustic Baseline | 3 minutes | 0.75 |

Formula: `new_baseline = (decay × old_baseline) + ((1 - decay) × recent_data)`

### 5.3 Operator-Specific Profiles
- Collect 5-10 shifts of baseline data per operator before personalization activates.
- Use percentile-based anomaly detection (20th percentile threshold) rather than absolute values.
- Store operator profiles in backend database.

### 5.4 Shift-Specific Baselines
- Maintain separate baselines for morning, afternoon, and night shifts per operator.
- Auto-switch based on clock time.

### 5.5 Context-Dependent Threshold Adjustment

| Operational Mode | Threshold | Rationale |
|------------------|-----------|-----------|
| Setup Mode (first 30min, screen change) | 0.70 | Naturally noisy, raise bar |
| Steady-State Production | 0.65 | Normal operation |
| Troubleshooting Mode (post-alarm) | 0.60 | Already problem-solving, capture more |

### 5.6 Reset Events
Reset rolling baselines on:
- Shift boundary change
- Major equipment change (die swap, screw replacement, screen change)
- Manual operator reset (post-break)

### 5.7 Fatigue Flagging
If an operator's baseline drifts >15% in any direction over a week, flag for review. Do not auto-adjust; alert for human assessment.

---

## 6. Latency Budget

### 6.1 End-to-End Pipeline

| Stage | Latency | Notes |
|-------|---------|-------|
| Sensor capture to backend | 50-150ms | Network dependent |
| Backend inference (parallel) | 50-80ms | Gaze, hand, HRV, acoustic |
| Fusion logic | <1ms | Simple weighted sum |
| Backend to glasses (prompt) | 50-150ms | Small payload |
| Glasses rendering | 50-100ms | Display update |
| **Total Round-Trip** | **200-480ms** | Target: <500ms |

### 6.2 Polling Cadence
- Evaluate confidence score every **200ms** (5x/second)
- Each evaluation analyzes the previous 2-second buffer

### 6.3 Optimization Path (if needed)
1. Run gaze and hand detection on-device on glasses (saves 30-50ms)
2. Batch process every 3rd frame instead of every frame
3. Cache HRV baselines locally on glasses, sync periodically with backend

### 6.4 Target
**300-400ms end-to-end** under normal conditions, **<250ms** with on-device optimization.

---

## 7. False Positive Tolerance

### 7.1 Tolerance Bands

| FP Rate per Hour | Acceptability | Notes |
|------------------|---------------|-------|
| 1 per hour | Tolerable | Annoying but workable |
| 2-3 per hour | Marginal | Operators may disengage |
| 5+ per hour | Unacceptable | System gets disabled |

### 7.2 Target Performance

- **Precision:** ≥70% (of every 10 prompts, at least 7 are true positives)
- **Recall:** 70-80% (catches 70-80% of genuine insight moments)
- **False Positive Rate:** ~1 per hour during steady-state operation

### 7.3 Threshold Tuning Options

| Mode | Threshold | Precision | Recall | FP/Hour |
|------|-----------|-----------|--------|---------|
| Conservative | 0.70 | ~85% | ~70% | 0.5 |
| Balanced (Default) | 0.65 | ~75% | ~80% | 1.0 |
| Aggressive | 0.60 | ~65% | ~90% | 2-3 |

### 7.4 Operator Feedback Loop

After each prompt, capture binary feedback:
- "Yes, I noticed something" → log as true positive
- "Nothing, just looking" → log as false positive

### 7.5 Dynamic Threshold Adjustment

```
IF operator's hourly FP count > 2:
    INCREASE threshold by 0.03

IF operator's hourly FP count < 0.5 AND TP count > 1:
    DECREASE threshold by 0.03

LIMIT threshold to range [0.55, 0.80]
```

### 7.6 Signal Weight Re-Tuning

Track which signals correlate with true positives per operator. If a signal is consistently firing false positives, de-weight it. Re-tune weights monthly based on accumulated data.

### 7.7 Mode-Based Thresholds

| Operator Mode | Threshold | Rationale |
|---------------|-----------|-----------|
| Mentorship/Training | 0.60 | More prompts okay, capture learning |
| Standard Operation | 0.65 | Default balanced mode |
| Expert Mode | 0.70 | Fewer interruptions, higher precision |
| Troubleshooting | 0.60 | Aggressive capture during problem-solving |

---

## 8. System Architecture

### 8.1 Component Diagram

```
+-------------------+        +------------------+
|  Meta AI Glasses  |<------>|  Backend Server  |
|                   |        |                  |
| - POV Video       |        | - Ingestion      |
| - Audio Capture   |        | - Inference      |
| - Prompt Display  |        | - Fusion Logic   |
+-------------------+        | - Database       |
                             | - API            |
+-------------------+        |                  |
|  Polar H10/Verity |------->|                  |
|                   |        +------------------+
| - HRV/HR Stream   |                |
| - Skin Temp       |                v
+-------------------+        +------------------+
                             |   Web Dashboard  |
                             |                  |
                             | - Insight Logs   |
                             | - Sensor Replay  |
                             | - Analytics      |
                             +------------------+
```

### 8.2 Data Flow

1. **Sensor capture** on glasses + Polar device, continuous streaming
2. **Backend ingestion** buffers data in rolling 2-second windows
3. **Parallel inference** runs detection models on each signal stream
4. **Fusion module** computes weighted confidence score every 200ms
5. **Threshold check** evaluates against current operator/mode threshold
6. **Trigger logic** fires prompt to glasses if threshold crossed
7. **CIAER capture** records operator response with full sensor context
8. **Storage** persists submission to database with all metadata
9. **Dashboard** provides query interface for analysis and review

### 8.3 Tech Stack Recommendations

| Layer | Recommendation | Rationale |
|-------|----------------|-----------|
| Glasses App | Meta SDK (native) | Required for hardware access |
| Backend Server | Node.js or Python (FastAPI) | Async I/O, ML library support |
| Database | PostgreSQL + TimescaleDB extension | Time-series optimization |
| Inference | TensorFlow Lite / ONNX Runtime | Fast, cross-platform |
| CV | OpenCV + MediaPipe | Standard, lightweight |
| Audio Processing | librosa | MFCC extraction, anomaly detection |
| Dashboard | React or Vue + Chart.js | Fast iteration |
| Streaming | WebSockets or gRPC | Low-latency bi-directional |

---

## 9. Data Model

### 9.1 CIAER Submission Record

```json
{
  "submission_id": "uuid",
  "operator_id": "string",
  "facility_id": "string",
  "timestamp": "ISO 8601",
  "trigger_type": "automatic | manual",
  "confidence_score": "float (0-1)",
  "signal_scores": {
    "gaze": "float",
    "hand": "float",
    "hrv": "float",
    "acoustic": "float"
  },
  "temporal_modifier": "float",
  "operational_mode": "setup | steady | troubleshooting",
  "ciaer": {
    "cause": "string (operator response)",
    "intuition": "string",
    "action": "string",
    "effect": "string",
    "result": "string"
  },
  "sensor_context": {
    "video_clip_url": "string (5 seconds pre-trigger)",
    "audio_clip_url": "string",
    "biometric_snapshot": {
      "hr": "int",
      "hrv_rmssd": "float",
      "skin_temp": "float"
    }
  },
  "feedback": {
    "operator_validated": "boolean (true positive / false positive)",
    "notes": "string"
  }
}
```

### 9.2 Operator Profile

```json
{
  "operator_id": "string",
  "name": "string",
  "shift": "morning | afternoon | night",
  "baselines": {
    "gaze": "object",
    "hand": "object",
    "hrv": "object",
    "acoustic": "object"
  },
  "thresholds": {
    "current": "float",
    "min": 0.55,
    "max": 0.80
  },
  "false_positive_rate_hourly": "float",
  "calibration_status": "initial | warm | calibrated"
}
```

---

## 10. Build Phases for Claude Code

### Phase 1: Backend Foundation (Week 1-2)
- [ ] Set up Node.js or Python server
- [ ] PostgreSQL + TimescaleDB schema
- [ ] REST API endpoints for ingestion and queries
- [ ] WebSocket support for real-time streaming
- [ ] Operator and facility data models

### Phase 2: Sensor Integration (Week 2-3)
- [ ] Polar API integration (auth, polling, parsing)
- [ ] Meta glasses SDK integration (video, audio, display)
- [ ] Data ingestion pipeline (buffering, queueing)
- [ ] Time-series storage for raw sensor data

### Phase 3: Inference Layer (Week 3-5)
- [ ] Gaze detection model (OpenGaze/MLKit integration)
- [ ] Hand pose estimation (MediaPipe)
- [ ] HRV anomaly detection (statistical, no ML needed initially)
- [ ] Acoustic anomaly detection (librosa MFCC + z-score)
- [ ] Per-signal scoring functions

### Phase 4: Fusion & Decision (Week 5-6)
- [ ] Confidence score computation
- [ ] Threshold evaluation with lockout logic
- [ ] Baseline establishment and rolling updates
- [ ] Operator profile management

### Phase 5: Prompt & Response (Week 6-7)
- [ ] Real-time prompt delivery to glasses
- [ ] CIAER five-phase capture flow on glasses
- [ ] Voice-to-text for operator responses
- [ ] Submission storage with full context

### Phase 6: Dashboard & Analytics (Week 7-8)
- [ ] Web dashboard for reviewing submissions
- [ ] Sensor replay with synchronized video/audio/biometric
- [ ] Filter and search by operator, time, signal pattern
- [ ] Operator feedback tagging interface

### Phase 7: Tuning & Validation (Week 8+)
- [ ] False positive feedback loop
- [ ] Dynamic threshold adjustment
- [ ] Signal weight re-tuning based on operator data
- [ ] Knowledge graph visualization

### Phase 8: Neural Pattern Discovery (Exploratory, Post-Foundation)

**Prerequisite:** At least 200 validated CIAER submissions with operator true/false positive labels.

**Premise:** Use the fusion model (gaze + hand + HRV + acoustic) as a *labeling system* for raw EEG data. The fusion provides ground-truth labels for "moment of deliberate cognition" that EEG alone cannot establish. By correlating EEG patterns against fusion-confirmed insight moments, the system can attempt to discover neural signatures of insight in the wild — labeled with biometric and environmental context that lab studies cannot replicate.

**Goal:** Not to validate the fusion with EEG. To use the fusion to *decode* EEG patterns that correlate with expert cognition in industrial operations.

**Hardware Candidate:**
- Emotiv Insight (5-channel, semi-processed)
- OpenBCI Cyton (8-channel, raw data, more research-grade)
- Sampling rate: 128-256Hz
- Bluetooth or USB to backend

**Data Collection:**
- [ ] Pure logging mode for EEG — no decision-making role
- [ ] Capture continuous EEG synchronized to other sensor timestamps
- [ ] Apply bandpass filter (1-30Hz) and compute power spectral density in 1-second windows
- [ ] Store alpha (8-12Hz), beta (12-30Hz), theta (4-8Hz), gamma (30-50Hz) band power per window
- [ ] Tag each EEG window with corresponding fusion confidence score and operator validation

**Analysis:**
- [ ] After 4-6 weeks of data, run correlation analysis
- [ ] Compare EEG band power distributions between:
  - True positive insight moments (fusion fired + operator confirmed)
  - False positives (fusion fired + operator denied)
  - Baseline routine work (no fusion trigger)
- [ ] Identify statistically significant differences across frequency bands
- [ ] Cluster EEG patterns to discover potential signatures of expert cognition

**Long-Term Aspiration:**
- [ ] If patterns emerge, build EEG-based predictor as supplementary signal
- [ ] Test whether EEG can predict insight moments *before* gaze/HRV/acoustic signals fire
- [ ] Build personal neural signature library for operator-specific cognitive states

**Honest Caveat:**
This phase may produce no useful findings. EEG is noisy, especially in industrial environments with electrical interference. Movement artifacts from operator activity may dominate the signal. Treat this as an exploratory research effort, not a guaranteed system feature. The value is in the attempt and the labeled dataset it produces, regardless of whether actionable patterns emerge.

---

## 11. Success Metrics

After 4 weeks of operation:
- [ ] At least 80% of triggers result in operator-validated true positives (precision)
- [ ] At least 70% of self-reported insight moments triggered automatically (recall)
- [ ] Average end-to-end latency <400ms
- [ ] At least 50 CIAER records captured
- [ ] Detectable patterns emerging in knowledge graph

After 12 weeks:
- [ ] Operator-specific thresholds converged within ±0.02 of stable value
- [ ] False positive rate <1 per hour during steady-state operation
- [ ] System runs unattended for full shifts without manual intervention
- [ ] At least 200 CIAER records captured
- [ ] Knowledge graph reveals at least 3 non-obvious decision patterns

---

## 12. Out of Scope (For Now)

These are explicitly deferred until the core system works:
- Multi-tenancy (multiple facilities sharing infrastructure)
- SOC 2 / enterprise compliance hardening
- LMS integration (Paulson, Routsis, Cornerstone)
- Cross-operator knowledge aggregation
- Mobile app (PWA or Android consumer version)
- ML model training on captured data (initial system uses statistical methods)

**Note:** EEG/neural pattern discovery is *not* out of scope — it's Phase 8, exploratory after foundation is built. The premise is to use the four-signal fusion as a labeling system for raw neural data, attempting to decode EEG patterns associated with confirmed insight moments. See Phase 8 for details.

---

## 13. Decisions & Rationale

This system is designed for **personal use first, enterprise readiness later**. Key design decisions:

1. **Why automatic detection over manual prompting?** Manual prompting creates cognitive burden that breaks the flow state where insights occur. Automatic detection lets the operator stay in the work.

2. **Why four signals instead of one?** Single signals have too many false positives. Fusion provides robustness while keeping individual signal complexity low.

3. **Why statistical baselines over deep learning?** Initial system needs to work without training data. Statistical methods (z-scores, percentiles) deliver acceptable performance immediately. ML can be added later once enough data is captured.

4. **Why Polar over Pixel Watch?** Polar provides research-grade raw HRV data via API. Consumer wearables smooth and summarize, hiding the signal you need.

5. **Why Meta glasses over a phone-cap setup?** Native POV camera, integrated audio, prompt display capability, on-device ML acceleration. Cleaner integration than DIY hardware.

6. **Why low-latency target (300-400ms)?** Insight moments unfold over 500ms-2s. Prompts must arrive within that window to be perceived as relevant to "the thing I just noticed."

---

## 14. Handoff Notes for Claude Code

When implementing:
- Start with Phase 1 (backend foundation) before touching glasses or Polar code
- Build mock data generators for each signal stream early — test fusion logic before hardware is integrated
- Implement logging-only mode first, prompts second — validate detection accuracy before enabling interruptions
- Use feature flags to enable/disable individual signals during testing
- Keep operator profile data separate from sensor data for clean privacy boundaries (even though this is personal use)
- Build the feedback loop (true/false positive labeling) into the system from day one — you'll need this data for tuning

---

**End of Specification**

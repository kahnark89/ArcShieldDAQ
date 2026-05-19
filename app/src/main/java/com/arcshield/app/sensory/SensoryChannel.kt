package com.arcshield.app.sensory

import kotlinx.coroutines.flow.Flow

/**
 * One sensory modality (acoustic, vibration, visual, olfactory, thermal).
 *
 * Every channel implementation:
 *   • exposes its identity and runtime availability;
 *   • can capture a shift-start baseline snapshot;
 *   • can capture an event-moment snapshot on demand;
 *   • optionally streams significant delta-from-baseline events for passive triggering;
 *   • releases hardware resources cleanly at shift end.
 *
 * Channels are owned by SensoryCaptureManager and never accessed directly from UI.
 */
interface SensoryChannel<S : SensorySnapshot> {

    val channelId: SensoryChannelId

    /** Current operational state — surfaced in UI status. */
    val availability: ChannelAvailability

    /**
     * Acquire hardware, request permissions if applicable, prepare for capture.
     * Idempotent — safe to call on an already-initialized channel.
     */
    suspend fun initialize(): ChannelAvailability

    /**
     * Capture the shift-start baseline. Called once per shift by SensoryCaptureManager.
     * Stored internally; subsequent captureEvent() snapshots are delta'd against it.
     */
    suspend fun captureBaseline(): S?

    /** Capture an event-moment snapshot. May return null if channel transiently unavailable. */
    suspend fun captureEvent(): S?

    /**
     * Cold flow of delta-from-baseline events that crossed the significance threshold.
     * SensoryCaptureManager merges these across all channels for passive triggering.
     * Channels without continuous streams (olfactory annotation, thermal proxy) return empty.
     */
    fun significantDeltaStream(): Flow<SensoryDelta>

    /** Release hardware, stop background threads, close streams. */
    suspend fun release()
}

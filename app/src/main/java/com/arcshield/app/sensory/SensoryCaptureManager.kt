package com.arcshield.app.sensory

import com.arcshield.app.sensory.channels.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * Orchestrates the five sensory channels.
 *
 * Responsibilities:
 *   1. Capture the shift-start SensoryBundle (baseline for all channels)
 *   2. Capture an event-moment SensoryBundle on demand
 *   3. Stream significant deltas from any channel for passive monitoring
 *   4. Expose channel availability map for UI status display
 *   5. Route operator annotations to OlfactoryChannel and ThermalChannel
 *   6. Release all hardware resources cleanly at shift end
 *
 * Threading: channel operations dispatch on Dispatchers.IO.
 * Manager scope runs on Dispatchers.Default.
 */
class SensoryCaptureManager(
    private val acousticChannel: AcousticChannel,
    private val vibrationChannel: VibrationChannel,
    private val visualChannel: VisualChannel,
    private val olfactoryChannel: OlfactoryChannel,
    private val thermalChannel: ThermalChannel,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private var baseline: SensoryBundle? = null

    private val allChannels: List<SensoryChannel<*>> = listOf(
        acousticChannel, vibrationChannel, visualChannel, olfactoryChannel, thermalChannel
    )

    /** Current availability of all five channels — drive UI status indicators from this. */
    val channelAvailability: Map<SensoryChannelId, ChannelAvailability>
        get() = allChannels.associate { it.channelId to it.availability }

    /** Last captured baseline (Pre-ENV bundle), or null if not yet captured this shift. */
    val currentBaseline: SensoryBundle? get() = baseline

    // ──────────────────────────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────────────────────────

    /**
     * Initialize all five channels in parallel.
     * Returns the per-channel availability map. Channels that fail are noted
     * but the manager continues operating with whatever channels succeeded.
     */
    suspend fun initialize(): Map<SensoryChannelId, ChannelAvailability> = coroutineScope {
        allChannels.map { ch ->
            async(Dispatchers.IO) { ch.channelId to ch.initialize() }
        }.awaitAll().toMap()
    }

    // ──────────────────────────────────────────────────────────────
    // Baseline capture (shift start)
    // ──────────────────────────────────────────────────────────────

    /**
     * Capture all five channel baselines in parallel.
     * Must be called once at shift start before event captures produce deltas.
     */
    suspend fun captureBaseline(): SensoryBundle = coroutineScope {
        val t = System.currentTimeMillis()
        val a = async(Dispatchers.IO) { acousticChannel.captureBaseline() }
        val v = async(Dispatchers.IO) { vibrationChannel.captureBaseline() }
        val vi = async(Dispatchers.IO) { visualChannel.captureBaseline() }
        val o = async(Dispatchers.IO) { olfactoryChannel.captureBaseline() }
        val th = async(Dispatchers.IO) { thermalChannel.captureBaseline() }
        val bundle = SensoryBundle(
            acoustic = a.await(),
            vibration = v.await(),
            visual = vi.await(),
            olfactory = o.await(),
            thermal = th.await(),
            timestampMs = t
        )
        baseline = bundle
        bundle
    }

    // ──────────────────────────────────────────────────────────────
    // Event capture
    // ──────────────────────────────────────────────────────────────

    /**
     * Capture an event-moment SensoryBundle across all five channels in parallel.
     * Pair with the stored baseline via [computeDeltas] to produce a SensoryDeltaBundle
     * that feeds PIE prompt generation.
     */
    suspend fun captureEvent(): SensoryBundle = coroutineScope {
        val t = System.currentTimeMillis()
        val a = async(Dispatchers.IO) { acousticChannel.captureEvent() }
        val v = async(Dispatchers.IO) { vibrationChannel.captureEvent() }
        val vi = async(Dispatchers.IO) { visualChannel.captureEvent() }
        val o = async(Dispatchers.IO) { olfactoryChannel.captureEvent() }
        val th = async(Dispatchers.IO) { thermalChannel.captureEvent() }
        SensoryBundle(
            acoustic = a.await(),
            vibration = v.await(),
            visual = vi.await(),
            olfactory = o.await(),
            thermal = th.await(),
            timestampMs = t
        )
    }

    /** Compute deltas between [event] and the stored baseline. */
    fun computeDeltas(event: SensoryBundle): SensoryDeltaBundle? {
        val b = baseline ?: return null
        return event.deltaFrom(b)
    }

    /** Convenience: capture event AND compute deltas in one call. */
    suspend fun captureEventWithDeltas(): Pair<SensoryBundle, SensoryDeltaBundle?> {
        val event = captureEvent()
        return event to computeDeltas(event)
    }

    // ──────────────────────────────────────────────────────────────
    // Passive monitoring — merged significant-delta stream
    // ──────────────────────────────────────────────────────────────

    /**
     * Stream of significant deltas from any channel.
     * Subscribe in the foreground service to drive passive trigger arbitration.
     */
    fun significantDeltaStream(): Flow<SensoryDelta> = listOf(
        acousticChannel.significantDeltaStream(),
        vibrationChannel.significantDeltaStream(),
        visualChannel.significantDeltaStream(),
        olfactoryChannel.significantDeltaStream(),
        thermalChannel.significantDeltaStream()
    ).merge()

    /**
     * Stream of elicitation hint lists, emitted whenever the merged delta
     * stream produces a significant event. Suitable for live UI display.
     */
    fun elicitationHintStream(): Flow<List<String>> = flow {
        significantDeltaStream().collect { delta ->
            // Emit a single-channel hint; full bundle hints come at event capture time
            emit(listOf(delta.elicitationHint()))
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Annotation routing — for olfactory/thermal Gen 1 inputs
    // ──────────────────────────────────────────────────────────────

    /** Operator submitted an olfactory annotation (quick-select chip or "smell that" wake word). */
    fun submitOlfactoryAnnotation(annotation: String, intensity: Int? = null, wakeWord: Boolean = false) {
        olfactoryChannel.submitAnnotation(annotation, intensity, wakeWord)
    }

    /** Operator submitted a thermal annotation (e.g., "barrel runs hot today"). */
    fun submitThermalAnnotation(annotation: String) {
        thermalChannel.submitAnnotation(annotation)
    }

    // ──────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────

    /** Release all hardware. Call from foreground service onDestroy / shift end. */
    suspend fun release() = coroutineScope {
        allChannels.map { ch -> async(Dispatchers.IO) { ch.release() } }.awaitAll()
        scope.coroutineContext[Job]?.cancelChildren()
    }
}

package com.arcshield.app.sensory

/**
 * Operational state of a sensory channel.
 * UI surfaces this to inform the operator which modalities are live.
 */
enum class ChannelAvailability {
    /** Channel is initialized and producing snapshots. */
    AVAILABLE,
    /** Required runtime permission not granted (e.g., RECORD_AUDIO, CAMERA). */
    PERMISSION_DENIED,
    /** Hardware not present or unreachable (no accelerometer, no paired sensor, no network for thermal). */
    HARDWARE_UNAVAILABLE,
    /** Channel has not yet been initialized this shift. */
    NOT_INITIALIZED,
    /** Channel encountered a non-recoverable error this shift. */
    ERROR
}

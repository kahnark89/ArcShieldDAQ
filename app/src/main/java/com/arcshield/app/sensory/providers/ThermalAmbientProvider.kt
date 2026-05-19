package com.arcshield.app.sensory.providers

import com.arcshield.app.sensory.ChannelAvailability

/**
 * Provider abstraction for thermal channel.
 *
 * Gen 1: OpenMeteoThermalProvider — periodically queries Open-Meteo with the
 *        facility lat/lon and caches the outdoor temperature in °F.
 *        Open-bay PPVC Line 1 facility → outdoor temp is a reasonable floor proxy.
 *
 * Gen 2: BluetoothIrProvider — wraps a paired Bluetooth IR thermometer for
 *        surface temperature (extruder barrel, motor housing). [sourceLabel] reflects
 *        the actual sensor model. surfaceTempF in the snapshot becomes populated.
 *
 * Substitution requires zero changes to ThermalChannel.
 */
interface ThermalAmbientProvider {

    suspend fun initialize(): ChannelAvailability

    /** Most recent ambient temperature in °F, or null if not yet available. */
    suspend fun currentAmbientTempF(): Float?

    /** Source label for traceability — "open_meteo", "bluetooth_ir_FLIR_X", etc. */
    fun sourceLabel(): String

    suspend fun release()
}

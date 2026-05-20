package com.arcshield.app.sensory.providers

import com.arcshield.app.sensory.ChannelAvailability

/**
 * Provider abstraction for olfactory channel.
 *
 * Gen 1: ChipAndWakeWordAnnotationProvider — receives quick-select chips and
 *        speech-recognized "smell that" utterances from the UI / speech layer.
 *
 * Gen 2: VocSensorAnnotationProvider — wraps a Bluetooth VOC sensor; returns
 *        live ppb readings via [currentVocPpb]. Annotations remain operator-driven.
 *
 * Substitution requires zero changes to OlfactoryChannel.
 */
interface OlfactoryAnnotationProvider {

    suspend fun initialize(): ChannelAvailability

    /** Persist an annotation submission (UI chip, wake-word transcription). */
    fun recordAnnotation(annotation: String, intensity: Int?, wakeWord: Boolean)

    /** Current live VOC sensor reading in ppb, or null on Gen 1. */
    fun currentVocPpb(): Float?

    suspend fun release()
}

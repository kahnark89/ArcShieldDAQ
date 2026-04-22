package com.arcshield.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Android [TextToSpeech]. Used by the Twin guidance
 * renderer to speak short prompts back to the operator while their hands
 * are occupied on the line.
 */
@Singleton
class TtsSpeaker @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ready = AtomicBoolean(false)
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready.set(true)
        } else {
            Log.w(TAG, "TTS init failed: $status")
        }
    }.also { it.language = Locale.US }

    fun speak(text: String) {
        if (!ready.get()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    private companion object { const val TAG = "TtsSpeaker" }
}

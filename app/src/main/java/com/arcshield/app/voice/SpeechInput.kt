package com.arcshield.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper over Android [SpeechRecognizer] for Intuition-phase voice capture.
 * Emits partial transcripts as they arrive and a final transcript at end-of-speech.
 *
 * RECORD_AUDIO permission must already be granted by the caller — this class
 * does not prompt. The caller is responsible for launching the permission
 * contract in its host Composable.
 */
@Singleton
class SpeechInput @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed class Event {
        data class Partial(val text: String) : Event()
        data class Final(val text: String)   : Event()
        data class Error(val code: Int)      : Event()
    }

    fun isSupported(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(): Flow<Event> = callbackFlow {
        if (!isSupported()) {
            trySend(Event.Error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
            close(); return@callbackFlow
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: Bundle) {
                partialResults.bestText()?.let { trySend(Event.Partial(it)) }
            }
            override fun onResults(results: Bundle) {
                results.bestText()?.let { trySend(Event.Final(it)) }
                close()
            }
            override fun onError(error: Int) {
                Log.w(TAG, "speech recognition error $error")
                trySend(Event.Error(error))
                close()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        recognizer.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        recognizer.startListening(intent)
        awaitClose {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
        }
    }

    private fun Bundle.bestText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object { const val TAG = "SpeechInput" }
}

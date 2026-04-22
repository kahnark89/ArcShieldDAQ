package com.arcshield.app.capture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcshield.app.bio.source.BiometricSource
import com.arcshield.app.capture.source.PhoneCameraSource
import com.arcshield.app.data.schema.KnowledgeSource
import com.arcshield.app.data.schema.OutcomeTag
import com.arcshield.app.data.schema.PredictionMatch
import com.arcshield.app.data.schema.ShadowAction
import com.arcshield.app.data.schema.SrkLevel
import com.arcshield.app.llm.IntuitionParser
import com.arcshield.app.preenv.source.PreEnvSource
import com.arcshield.app.sync.CorpusSink
import com.arcshield.app.vision.GaugeReader
import com.arcshield.app.voice.SpeechInput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val phoneCamera:        PhoneCameraSource,
    private val preEnv:     PreEnvSource,
    private val biometrics: BiometricSource,
    private val gaugeReader: GaugeReader,
    val speechInput:        SpeechInput,
    private val intuitionParser: IntuitionParser,
    private val sink:       CorpusSink,
) : ViewModel() {

    data class State(
        val phase: CapturePhase = CapturePhase.IDLE,
        val draft: CaptureDraft = CaptureDraft(),
        val lastSaveError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun startCycle() {
        _state.value = State(
            phase = CapturePhase.CAUSE,
            draft = CaptureDraft(causeCapturedAt = nowIso()),
        )
    }

    fun cancel() {
        _state.value = State()
    }

    fun advance() {
        _state.update { st ->
            val next = when (st.phase) {
                CapturePhase.IDLE      -> CapturePhase.CAUSE
                CapturePhase.CAUSE     -> CapturePhase.INTUITION
                CapturePhase.INTUITION -> CapturePhase.ACTION
                CapturePhase.ACTION    -> CapturePhase.EFFECT
                CapturePhase.EFFECT    -> CapturePhase.RESULT
                CapturePhase.RESULT    -> CapturePhase.RESULT
            }
            val draft = when (next) {
                CapturePhase.ACTION -> st.draft.copy(actionTimestamp = nowIso())
                CapturePhase.EFFECT -> st.draft.copy(effectCapturedAt = nowIso())
                else                -> st.draft
            }
            st.copy(phase = next, draft = draft)
        }
    }

    fun updateCause(
        description: String? = null,
    ) = _state.update { it.copy(draft = it.draft.copy(visualAnchorDescription = description)) }

    fun captureCauseFrame() {
        viewModelScope.launch {
            val bio = runCatching { biometrics.currentSnapshot() }.getOrNull()
            runCatching { phoneCamera.captureFrame() }
                .onSuccess { bytes ->
                    val path = bytes?.let { writeJpegToCache(it).absolutePath }
                    val readings = bytes?.let { gaugeReader.read(it) }.orEmpty()
                    _state.update {
                        it.copy(draft = it.draft.copy(
                            visualAnchorFrameRef = path ?: it.draft.visualAnchorFrameRef,
                            causeBiometrics      = bio ?: it.draft.causeBiometrics,
                            causeSensorReadings  = if (readings.isNotEmpty()) readings else it.draft.causeSensorReadings,
                        ))
                    }
                }
        }
    }

    fun startVoiceIntuition() {
        viewModelScope.launch {
            speechInput.listen().collect { ev ->
                when (ev) {
                    is SpeechInput.Event.Partial -> _state.update {
                        it.copy(draft = it.draft.copy(voiceTranscript = ev.text))
                    }
                    is SpeechInput.Event.Final -> {
                        _state.update { it.copy(draft = it.draft.copy(voiceTranscript = ev.text)) }
                        val parsed = intuitionParser.parse(ev.text)
                        _state.update { st ->
                            st.copy(draft = st.draft.copy(
                                srkLevel         = parsed.srkLevel         ?: st.draft.srkLevel,
                                causalHypothesis = parsed.causalHypothesis ?: st.draft.causalHypothesis,
                                projection       = parsed.projection       ?: st.draft.projection,
                                confidenceLevel  = parsed.confidenceLevel  ?: st.draft.confidenceLevel,
                                knowledgeSource  = parsed.knowledgeSource  ?: st.draft.knowledgeSource,
                            ))
                        }
                    }
                    is SpeechInput.Event.Error -> { /* swallow — UI stays on manual entry */ }
                }
            }
        }
    }

    private fun writeJpegToCache(bytes: ByteArray): File {
        val dir = File(context.cacheDir, "cause_frames").apply { mkdirs() }
        val file = File(dir, "${Instant.now().toEpochMilli()}.jpg")
        file.writeBytes(bytes)
        return file
    }

    fun updateIntuition(
        srkLevel:         SrkLevel?         = null,
        causalHypothesis: String?           = null,
        projection:       String?           = null,
        confidence:       Double?           = null,
        knowledgeSource:  KnowledgeSource?  = null,
    ) = _state.update {
        it.copy(draft = it.draft.copy(
            srkLevel         = srkLevel ?: it.draft.srkLevel,
            causalHypothesis = causalHypothesis ?: it.draft.causalHypothesis,
            projection       = projection ?: it.draft.projection,
            confidenceLevel  = confidence ?: it.draft.confidenceLevel,
            knowledgeSource  = knowledgeSource ?: it.draft.knowledgeSource,
        ))
    }

    fun updateAction(
        actionType:   String? = null,
        rationale:    String? = null,
        nonAction:    Boolean? = null,
    ) = _state.update {
        it.copy(draft = it.draft.copy(
            actionType             = actionType ?: it.draft.actionType,
            actionRationale        = rationale ?: it.draft.actionRationale,
            wasDeliberateNonAction = nonAction ?: it.draft.wasDeliberateNonAction,
        ))
    }

    fun addShadowAction(shadow: ShadowAction) = _state.update {
        it.copy(draft = it.draft.copy(shadowActions = it.draft.shadowActions + shadow))
    }

    fun removeShadowAction(index: Int) = _state.update {
        val list = it.draft.shadowActions.toMutableList()
        if (index in list.indices) list.removeAt(index)
        it.copy(draft = it.draft.copy(shadowActions = list))
    }

    fun updateEffect(
        predictionMatch: PredictionMatch? = null,
    ) = _state.update {
        it.copy(draft = it.draft.copy(
            predictionMatch = predictionMatch ?: it.draft.predictionMatch,
        ))
    }

    fun updateResult(
        outcomeTag:          OutcomeTag? = null,
        hypothesisConfirmed: Boolean?    = null,
        escalationDelta:     String?     = null,
    ) = _state.update {
        it.copy(draft = it.draft.copy(
            outcomeTag           = outcomeTag ?: it.draft.outcomeTag,
            hypothesisConfirmed  = hypothesisConfirmed ?: it.draft.hypothesisConfirmed,
            escalationDelta      = escalationDelta ?: it.draft.escalationDelta,
        ))
    }

    fun submit() {
        viewModelScope.launch {
            val snap  = preEnv.currentSnapshot()
            val draft = _state.value.draft.copy(completedAt = nowIso())
            val event = draft.finalize(preEnv = snap, nowIso = ::nowIso)
            when (val res = sink.persist(event)) {
                is CorpusSink.Result.Success -> _state.value = State()
                is CorpusSink.Result.Failure -> _state.update {
                    it.copy(lastSaveError = res.cause.message ?: "save failed")
                }
            }
        }
    }

    private fun nowIso(): String = Instant.now().toString()
}

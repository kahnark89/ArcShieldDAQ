package com.arcshield.app.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcshield.app.data.schema.KnowledgeSource
import com.arcshield.app.data.schema.OutcomeTag
import com.arcshield.app.data.schema.PredictionMatch
import com.arcshield.app.data.schema.ShadowAction
import com.arcshield.app.data.schema.SrkLevel
import com.arcshield.app.preenv.source.PreEnvSource
import com.arcshield.app.sync.CorpusSink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val preEnv: PreEnvSource,
    private val sink:   CorpusSink,
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

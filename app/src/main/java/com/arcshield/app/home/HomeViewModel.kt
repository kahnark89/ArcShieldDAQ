package com.arcshield.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcshield.app.preenv.PreEnvSnapshot
import com.arcshield.app.preenv.source.PreEnvSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preEnv: PreEnvSource,
) : ViewModel() {

    val snapshot: StateFlow<PreEnvSnapshot> = preEnv.snapshot

    fun startShift(operatorId: String) {
        viewModelScope.launch {
            preEnv.recordShiftStart(
                operatorId     = operatorId.trim(),
                shiftSessionId = UUID.randomUUID().toString(),
                shiftStartedAt = Instant.now().toString(),
            )
        }
    }

    fun endShift() {
        viewModelScope.launch { preEnv.recordShiftEnd() }
    }

    fun recordBatch(batchId: String) {
        viewModelScope.launch {
            preEnv.recordBatchChange(batchId = batchId.trim(), startedAt = null)
        }
    }
}

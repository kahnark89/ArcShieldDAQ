package com.arcshield.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcshield.app.bio.source.BiometricSource
import com.arcshield.app.preenv.PreEnvPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class PolarPairingViewModel @Inject constructor(
    private val biometrics: BiometricSource,
    private val prefs: PreEnvPrefsStore,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Scanning : UiState
        data class DevicesFound(val devices: List<BiometricSource.ScannedDevice>) : UiState
        data class Error(val msg: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _state.value = UiState.Scanning
            val found = mutableListOf<BiometricSource.ScannedDevice>()
            runCatching {
                withTimeoutOrNull(10_000) {
                    biometrics.scanForDevices().collect { device ->
                        if (found.none { it.deviceId == device.deviceId }) {
                            found.add(device)
                            _state.value = UiState.DevicesFound(found.toList())
                        }
                    }
                }
            }.onFailure { e ->
                _state.value = UiState.Error(e.message ?: "Scan failed")
                return@launch
            }
            if (found.isEmpty()) {
                _state.value = UiState.Error("No devices found. Make sure the strap is on your body and awake.")
            }
        }
    }

    fun selectDevice(device: BiometricSource.ScannedDevice) {
        viewModelScope.launch {
            prefs.savePolarDeviceId(device.deviceId)
        }
    }
}

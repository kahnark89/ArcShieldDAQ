package com.arcshield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arcshield.app.bio.source.BiometricSource
import com.arcshield.app.capture.CapturePhase
import com.arcshield.app.capture.CaptureScreen
import com.arcshield.app.capture.CaptureViewModel
import com.arcshield.app.home.HomeScreen
import com.arcshield.app.onboarding.LlmSetupScreen
import com.arcshield.app.onboarding.PolarPairingScreen
import com.arcshield.app.preenv.PreEnvPrefsStore
import com.arcshield.app.security.ApiKeyStore
import com.arcshield.app.trigger.FusionEngine
import com.arcshield.app.ui.theme.ArcShieldTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var apiKeyStore: ApiKeyStore
    @Inject lateinit var fusionEngine: FusionEngine
    @Inject lateinit var biometricSource: BiometricSource
    @Inject lateinit var preEnvPrefsStore: PreEnvPrefsStore

    private val captureViewModel: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Layer 2 — collect fusion triggers while the activity is at least STARTED.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fusionEngine.triggers.collect { event ->
                    captureViewModel.fireCauseFromTrigger(event)
                }
            }
        }

        // Polar BLE lifecycle — connect when deviceId is set, disconnect on background.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preEnvPrefsStore.polarDeviceId.collectLatest { deviceId ->
                    if (deviceId == null) {
                        biometricSource.stop()
                        return@collectLatest
                    }
                    biometricSource.start(deviceId)
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        biometricSource.stop()
                    }
                }
            }
        }

        setContent {
            ArcShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val polarDeviceId by preEnvPrefsStore.polarDeviceId
                        .collectAsStateWithLifecycle(initialValue = null)

                    when {
                        !apiKeyStore.isConfigured() -> {
                            LlmSetupScreen(
                                onSetupComplete = { recreate() },
                                modifier        = Modifier.padding(innerPadding),
                            )
                        }
                        polarDeviceId == null -> {
                            PolarPairingScreen(
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                        else -> {
                            val state by captureViewModel.state.collectAsStateWithLifecycle()
                            if (state.phase != CapturePhase.IDLE) {
                                CaptureScreen(
                                    onCycleComplete = {},
                                    modifier        = Modifier.padding(innerPadding),
                                )
                            } else {
                                HomeScreen(
                                    onBeginCapture = { captureViewModel.startCycle() },
                                    modifier       = Modifier.padding(innerPadding),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

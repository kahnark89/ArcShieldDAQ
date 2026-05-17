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
import com.arcshield.app.capture.CapturePhase
import com.arcshield.app.capture.CaptureScreen
import com.arcshield.app.capture.CaptureViewModel
import com.arcshield.app.home.HomeScreen
import com.arcshield.app.onboarding.LlmSetupScreen
import com.arcshield.app.security.ApiKeyStore
import com.arcshield.app.trigger.FusionEngine
import com.arcshield.app.ui.theme.ArcShieldTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var apiKeyStore: ApiKeyStore
    @Inject lateinit var fusionEngine: FusionEngine

    private val captureViewModel: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Layer 2 — collect fusion triggers while the activity is at least
        // STARTED and route them into the capture state machine. The engine's
        // own 10 s lockout and the ViewModel's IDLE check together prevent
        // re-fires from clobbering an in-progress draft.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fusionEngine.triggers.collect { event ->
                    captureViewModel.fireCauseFromTrigger(event)
                }
            }
        }

        setContent {
            ArcShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!apiKeyStore.isConfigured()) {
                        LlmSetupScreen(
                            onSetupComplete = { recreate() },
                            modifier        = Modifier.padding(innerPadding),
                        )
                    } else {
                        val state by captureViewModel.state.collectAsStateWithLifecycle()
                        if (state.phase != CapturePhase.IDLE) {
                            CaptureScreen(
                                // No-op: a successful submit() sets state to IDLE which auto-switches
                                // back to HomeScreen via the observation above. A failed submit keeps
                                // the draft and surfaces lastSaveError. Manual cancel calls
                                // viewModel.cancel() directly inside CaptureScreen.
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

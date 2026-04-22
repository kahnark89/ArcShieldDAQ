package com.arcshield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.arcshield.app.home.HomeScreen
import com.arcshield.app.onboarding.LlmSetupScreen
import com.arcshield.app.security.ApiKeyStore
import com.arcshield.app.ui.theme.ArcShieldTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var apiKeyStore: ApiKeyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArcShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!apiKeyStore.isConfigured()) {
                        LlmSetupScreen(
                            onSetupComplete = { recreate() },
                            modifier        = Modifier.padding(innerPadding),
                        )
                    } else {
                        HomeScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

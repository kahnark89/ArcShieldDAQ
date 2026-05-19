package com.arcshield.app.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcshield.app.bio.source.BiometricSource

@Composable
fun PolarPairingScreen(
    modifier: Modifier = Modifier,
    viewModel: PolarPairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var btPermissionsGranted by remember {
        mutableStateOf(hasBtPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        btPermissionsGranted = grants.values.all { it }
        if (btPermissionsGranted) viewModel.startScan()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Connect Polar Device", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "ArcShield uses a Polar H10 or Verity Sense for HRV-based trigger detection. " +
                "Wear the strap and tap Scan.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            if (btPermissionsGranted) {
                viewModel.startScan()
            } else {
                permissionLauncher.launch(bluetoothPermissions())
            }
        }) {
            Text(if (state is PolarPairingViewModel.UiState.Scanning) "Scanning…" else "Scan for Devices")
        }

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is PolarPairingViewModel.UiState.Scanning -> CircularProgressIndicator()
            is PolarPairingViewModel.UiState.Error    -> Text(
                s.msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            is PolarPairingViewModel.UiState.DevicesFound -> DeviceList(
                devices  = s.devices,
                onSelect = viewModel::selectDevice,
            )
            else -> Unit
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<BiometricSource.ScannedDevice>,
    onSelect: (BiometricSource.ScannedDevice) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.deviceId }) { device ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(device) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        Text(device.deviceId, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun hasBtPermissions(context: android.content.Context): Boolean =
    bluetoothPermissions().all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PermissionChecker.PERMISSION_GRANTED
    }

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.BLUETOOTH)
    }

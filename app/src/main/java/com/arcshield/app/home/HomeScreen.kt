package com.arcshield.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcshield.app.preenv.PreEnvSnapshot
import com.arcshield.app.preenv.ShiftPhase

@Composable
fun HomeScreen(
    onBeginCapture: () -> Unit,
    modifier:       Modifier = Modifier,
    viewModel:      HomeViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    var showStartShift by remember { mutableStateOf(false) }
    var showBatchChange by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ArcShield", style = MaterialTheme.typography.headlineMedium)

        ShiftCard(snapshot = snapshot)
        BatchCard(snapshot = snapshot)
        AmbientCard(snapshot = snapshot)

        Spacer(Modifier.height(8.dp))

        val shiftActive = !snapshot.shiftSessionId.isNullOrBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shiftActive) {
                OutlinedButton(onClick = viewModel::endShift) { Text("End shift") }
                OutlinedButton(onClick = { showBatchChange = true }) { Text("Change batch") }
                Button(onClick = onBeginCapture) { Text("Capture event") }
            } else {
                Button(onClick = { showStartShift = true }) { Text("Start shift") }
            }
        }
    }

    if (showStartShift) {
        StartShiftDialog(
            onConfirm = {
                viewModel.startShift(it)
                showStartShift = false
            },
            onDismiss = { showStartShift = false },
        )
    }

    if (showBatchChange) {
        BatchChangeDialog(
            onConfirm = {
                viewModel.recordBatch(it)
                showBatchChange = false
            },
            onDismiss = { showBatchChange = false },
        )
    }
}

@Composable
private fun ShiftCard(snapshot: PreEnvSnapshot) {
    InfoCard(title = "Shift") {
        val active = !snapshot.shiftSessionId.isNullOrBlank()
        Text(if (active) "Active — ${snapshot.operatorId.ifBlank { "(no operator)" }}" else "Not started")
        Text("Phase: ${snapshot.shiftPhase.displayName()}")
    }
}

@Composable
private fun BatchCard(snapshot: PreEnvSnapshot) {
    InfoCard(title = "Material batch") {
        val id = snapshot.materialBatchId
        if (id.isNullOrBlank()) {
            Text("No batch recorded this shift")
        } else {
            Text(id)
            snapshot.materialBatchStartedAt?.let { Text("Started: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun AmbientCard(snapshot: PreEnvSnapshot) {
    InfoCard(title = "Ambient temp") {
        val t = snapshot.ambientTempF
        if (t == null) {
            Text("Unavailable — fetching from weather API")
        } else {
            Text("%.1f °F".format(t))
            Text("Source: ${snapshot.ambientTempSource.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: @Composable () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            body()
        }
    }
}

@Composable
private fun StartShiftDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var operatorId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Start shift") },
        text             = {
            Column {
                Text("Enter operator ID")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = operatorId,
                    onValueChange = { operatorId = it },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(operatorId) },
                enabled = operatorId.isNotBlank(),
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BatchChangeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var batchId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Record new batch") },
        text             = {
            Column {
                Text("Enter batch ID (barcode or short-code)")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = batchId,
                    onValueChange = { batchId = it },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(batchId) },
                enabled = batchId.isNotBlank(),
            ) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun ShiftPhase.displayName(): String = when (this) {
    ShiftPhase.STARTUP      -> "Startup"
    ShiftPhase.STEADY_STATE -> "Steady state"
    ShiftPhase.SHUTDOWN     -> "Shutdown"
    ShiftPhase.UNKNOWN      -> "Unknown"
}

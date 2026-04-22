package com.arcshield.app.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.arcshield.app.data.schema.OutcomeTag
import com.arcshield.app.data.schema.PredictionMatch
import com.arcshield.app.data.schema.ShadowAction
import com.arcshield.app.data.schema.SrkLevel

@Composable
fun CaptureScreen(
    onCycleComplete: () -> Unit,
    modifier:        Modifier = Modifier,
    viewModel:       CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PhaseHeader(state.phase)
        HorizontalDivider()

        when (state.phase) {
            CapturePhase.IDLE      -> IdlePanel(onBegin = viewModel::startCycle)
            CapturePhase.CAUSE     -> CausePanel(state.draft, viewModel)
            CapturePhase.INTUITION -> IntuitionPanel(state.draft, viewModel::updateIntuition)
            CapturePhase.ACTION    -> ActionPanel(state.draft, viewModel)
            CapturePhase.EFFECT    -> EffectPanel(state.draft, viewModel::updateEffect)
            CapturePhase.RESULT    -> ResultPanel(state.draft, viewModel::updateResult)
        }

        if (state.phase != CapturePhase.IDLE) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::cancel) { Text("Cancel") }
                if (state.phase == CapturePhase.RESULT) {
                    Button(
                        onClick = { viewModel.submit(); onCycleComplete() },
                    ) { Text("Save event") }
                } else {
                    Button(onClick = viewModel::advance) { Text("Next") }
                }
            }
        }

        state.lastSaveError?.let {
            Text("Save failed: $it", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PhaseHeader(phase: CapturePhase) {
    val label = when (phase) {
        CapturePhase.IDLE      -> "Ready"
        CapturePhase.CAUSE     -> "1 — Cause"
        CapturePhase.INTUITION -> "2 — Intuition"
        CapturePhase.ACTION    -> "3 — Action"
        CapturePhase.EFFECT    -> "4 — Effect"
        CapturePhase.RESULT    -> "5 — Result"
    }
    Text(label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun IdlePanel(onBegin: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Start a CIAER+ cycle when the operator registers a deviation.")
        Button(onClick = onBegin) { Text("Begin cycle") }
    }
}

@Composable
private fun CausePanel(draft: CaptureDraft, viewModel: CaptureViewModel) {
    var note by remember { mutableStateOf(draft.visualAnchorDescription.orEmpty()) }
    Text("Captured at: ${draft.causeCapturedAt ?: "—"}", style = MaterialTheme.typography.bodySmall)

    CameraPreview(source = viewModel.phoneCamera)

    OutlinedButton(onClick = viewModel::captureCauseFrame) {
        Text(
            if (draft.visualAnchorFrameRef == null) "Snap Cause frame"
            else "Retake Cause frame",
        )
    }
    draft.visualAnchorFrameRef?.let {
        Text("Frame saved: ${it.substringAfterLast('/')}",
            style = MaterialTheme.typography.bodySmall)
    }

    OutlinedTextField(
        value         = note,
        onValueChange = {
            note = it
            viewModel.updateCause(description = it.ifBlank { null })
        },
        label         = { Text("What drew attention? (optional)") },
        modifier      = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntuitionPanel(
    draft:    CaptureDraft,
    onUpdate: (SrkLevel?, String?, String?, Double?, com.arcshield.app.data.schema.KnowledgeSource?) -> Unit,
) {
    Text("SRK classification")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SrkLevel.values().forEach { level ->
            FilterChip(
                selected = draft.srkLevel == level,
                onClick  = { onUpdate(level, null, null, null, null) },
                label    = { Text(level.name) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))

    var hypothesis by remember { mutableStateOf(draft.causalHypothesis) }
    OutlinedTextField(
        value         = hypothesis,
        onValueChange = { hypothesis = it; onUpdate(null, it, null, null, null) },
        label         = { Text("Causal hypothesis") },
        modifier      = Modifier.fillMaxWidth(),
    )

    var projection by remember { mutableStateOf(draft.projection.orEmpty()) }
    OutlinedTextField(
        value         = projection,
        onValueChange = { projection = it; onUpdate(null, null, it.ifBlank { null }, null, null) },
        label         = { Text("Projection (if no intervention)") },
        modifier      = Modifier.fillMaxWidth(),
    )

    Text("Confidence: ${"%.2f".format(draft.confidenceLevel)}")
    Slider(
        value         = draft.confidenceLevel.toFloat(),
        onValueChange = { onUpdate(null, null, null, it.toDouble(), null) },
        valueRange    = 0f..1f,
    )
}

@Composable
private fun ActionPanel(draft: CaptureDraft, viewModel: CaptureViewModel) {
    var actionType  by remember { mutableStateOf(draft.actionType) }
    var rationale   by remember { mutableStateOf(draft.actionRationale.orEmpty()) }
    var nonAction   by remember { mutableStateOf(draft.wasDeliberateNonAction) }

    OutlinedTextField(
        value         = actionType,
        onValueChange = { actionType = it; viewModel.updateAction(actionType = it) },
        label         = { Text("Action type") },
        modifier      = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value         = rationale,
        onValueChange = { rationale = it; viewModel.updateAction(rationale = it) },
        label         = { Text("Rationale (why this action)") },
        modifier      = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = nonAction, onCheckedChange = { nonAction = it; viewModel.updateAction(nonAction = it) })
        Text("Deliberate non-action")
    }

    ShadowActionSection(draft, viewModel)
}

@Composable
private fun ShadowActionSection(draft: CaptureDraft, viewModel: CaptureViewModel) {
    val intensity = ShadowActionElicitor.promptFor(draft.srkLevel)
    if (intensity == ShadowActionElicitor.PromptIntensity.NONE) return

    var type   by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    HorizontalDivider()
    Text(
        when (intensity) {
            ShadowActionElicitor.PromptIntensity.LIGHT -> "Considered but rejected? (optional)"
            ShadowActionElicitor.PromptIntensity.FULL  -> "What alternatives did you consider and reject?"
            else -> ""
        },
        style = MaterialTheme.typography.titleSmall,
    )

    draft.shadowActions.forEachIndexed { index, shadow ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.padding(end = 8.dp)) {
                Text("• ${shadow.actionType}")
                Text(shadow.rejectionRationale, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { viewModel.removeShadowAction(index) }) { Text("Remove") }
        }
    }

    OutlinedTextField(
        value         = type,
        onValueChange = { type = it },
        label         = { Text("Rejected action") },
        modifier      = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value         = reason,
        onValueChange = { reason = it },
        label         = { Text("Why rejected?") },
        modifier      = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = {
            if (type.isNotBlank() && reason.isNotBlank()) {
                viewModel.addShadowAction(
                    ShadowAction(actionType = type.trim(), rejectionRationale = reason.trim())
                )
                type = ""; reason = ""
            }
        },
        enabled = type.isNotBlank() && reason.isNotBlank(),
    ) { Text("Add shadow action") }
}

@Composable
private fun EffectPanel(
    draft:    CaptureDraft,
    onUpdate: (PredictionMatch?) -> Unit,
) {
    Text("Did the intervention match your projection?")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PredictionMatch.values().forEach { pm ->
            FilterChip(
                selected = draft.predictionMatch == pm,
                onClick  = { onUpdate(pm) },
                label    = { Text(pm.name) },
            )
        }
    }
}

@Composable
private fun ResultPanel(
    draft:    CaptureDraft,
    onUpdate: (OutcomeTag?, Boolean?, String?) -> Unit,
) {
    Text("Outcome")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutcomeTag.values().forEach { tag ->
            FilterChip(
                selected = draft.outcomeTag == tag,
                onClick  = { onUpdate(tag, null, null) },
                label    = { Text(tag.name) },
            )
        }
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(
            checked = draft.hypothesisConfirmed,
            onCheckedChange = { onUpdate(null, it, null) },
        )
        Text("Hypothesis confirmed")
    }
    var esc by remember { mutableStateOf(draft.escalationDelta.orEmpty()) }
    OutlinedTextField(
        value         = esc,
        onValueChange = { esc = it; onUpdate(null, null, it.ifBlank { null }) },
        label         = { Text("Escalation delta (narrative)") },
        modifier      = Modifier.fillMaxWidth(),
    )
}

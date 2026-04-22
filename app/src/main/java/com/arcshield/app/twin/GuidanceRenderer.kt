package com.arcshield.app.twin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Renders Twin guidance in the capture UI. No-op when [guidance] is null —
 * shipped as a placeholder so the capture screen can call it unconditionally
 * and the server build can light it up later without UI plumbing changes.
 *
 * When the Twin is offline (no server URL configured), calls to
 * [TwinClient.requestGuidance] return null, so this composable renders
 * nothing and the operator flow is untouched.
 */
@Composable
fun GuidanceCard(
    guidance: TwinClient.Guidance?,
    modifier: Modifier = Modifier,
) {
    if (guidance == null || guidance.withheld) return
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Twin suggestion", style = MaterialTheme.typography.labelMedium)
            Text(guidance.narrative, style = MaterialTheme.typography.bodyMedium)
            guidance.suggestedAction?.let {
                Text("Suggested: $it", style = MaterialTheme.typography.bodySmall)
            }
            guidance.confidence?.let {
                Text("Confidence: ${"%.2f".format(it)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PrecedentList(
    precedents: List<TwinClient.Precedent>,
    modifier: Modifier = Modifier,
) {
    if (precedents.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Related past events", style = MaterialTheme.typography.labelMedium)
        precedents.forEach { p ->
            Text(
                "• ${p.summary}  (${p.outcomeTag}, sim=${"%.2f".format(p.similarity)})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

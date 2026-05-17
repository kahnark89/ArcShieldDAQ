package com.arcshield.app.trigger

import com.arcshield.app.data.schema.OperationalMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies the operator's current operational mode per detection-spec §5.5
 * and §7.7. Drives mode-based threshold adjustment (setup 0.70 / steady 0.65
 * / troubleshooting 0.60).
 *
 * Gen 1 returns [OperationalMode.STEADY] unconditionally — the real
 * classifier needs signals not yet wired (recent alarm history, screen-change
 * events, batch-changeover timing). Replace this stub once those inputs land.
 */
@Singleton
class OperationalModeDetector @Inject constructor() {
    fun currentMode(): OperationalMode = OperationalMode.STEADY
}

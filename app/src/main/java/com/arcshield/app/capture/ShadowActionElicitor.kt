package com.arcshield.app.capture

import com.arcshield.app.data.schema.SrkLevel

/**
 * SRK-gated prompting for Shadow Actions.
 *
 * - SKILL: no prompt (automatic pattern response, no conscious alternatives)
 * - RULE: light prompt, optional single alternative
 * - KNOWLEDGE: full prompt, encourage multiple alternatives with rationale
 *
 * See whitepaper §3 and product architecture §3.2.
 */
object ShadowActionElicitor {

    enum class PromptIntensity { NONE, LIGHT, FULL }

    fun promptFor(srkLevel: SrkLevel): PromptIntensity = when (srkLevel) {
        SrkLevel.SKILL     -> PromptIntensity.NONE
        SrkLevel.RULE      -> PromptIntensity.LIGHT
        SrkLevel.KNOWLEDGE -> PromptIntensity.FULL
    }
}

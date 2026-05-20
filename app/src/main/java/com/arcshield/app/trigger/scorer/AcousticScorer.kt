package com.arcshield.app.trigger.scorer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps short-window microphone capture (AcousticChannel) to a 0..1 anomaly
 * score per detection-spec §3.4: MFCC deviation >2σ from rolling 3-min
 * baseline → 1.0.
 *
 * The sensory layer's [com.arcshield.app.sensory.channels.AcousticChannel]
 * already produces FFT-derived features (dominant freq, spectral centroid,
 * amplitude, 32-bin histogram). What's missing is a rolling baseline for
 * those features and an MFCC anomaly detector. [NoOpAcousticScorer]
 * returns null until that lands; the fusion engine treats null as 0
 * contribution.
 */
interface AcousticScorer {
    suspend fun score(): Float?
}

@Singleton
class NoOpAcousticScorer @Inject constructor() : AcousticScorer {
    override suspend fun score(): Float? = null
}

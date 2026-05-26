package com.example.pulsewatch.safety.sound

import android.util.Log
import com.example.pulsewatch.data.SoundType

/**
 * Two-stage on-watch sound detector with strict voice rejection.
 *
 * Stage 1: cheap RMS gate — skips ambient + quiet speech.
 * Stage 2: FFT spectral fingerprint + voice rejection + sustained-frame confirmation.
 *
 * Direction is always CENTER on the watch (mono microphone).
 *
 * Tuning principles:
 *   - Conversational speech rarely exceeds rms 0.20 → loud thresholds for shouts must be ≥ 0.25.
 *   - Voice fundamentals 60–280 Hz → reject when peak sits there with little high-band energy.
 *   - Real danger sounds (siren, alarm, shout) have significant energy above 2.5 kHz; speech doesn't.
 *   - Most danger sounds last > 1 second → require 2 matching frames except for impulsive GLASS_BREAK.
 */
class SoundDetector(
    private val sampleRate: Int = 16_000,
    private val rmsGate: Float = 0.10f,            // ~ -20 dBFS — above conversational speech
    private val debounceMs: Long = 5_000L,
    private val confirmationWindowMs: Long = 1_800L,
    private val fftSize: Int = 4096
) {

    data class Detection(val type: SoundType, val confidence: Float)

    private val peakHistory = ArrayDeque<Double>()
    private val peakHistoryCap = 6

    private data class Candidate(val type: SoundType, val ts: Long, val rms: Float)

    private val recentCandidates = ArrayDeque<Candidate>()
    private var lastDetectionAtMs = 0L

    fun analyze(samples: ShortArray): Detection? {
        // ─── Stage 1: amplitude gate ──────────────────────────────────────
        val rms = AudioFeatures.rms(samples)
        if (rms < rmsGate) {
            // Reset trackers when ambient — keeps state relevant
            if (peakHistory.isNotEmpty()) peakHistory.clear()
            if (recentCandidates.isNotEmpty()) recentCandidates.clear()
            return null
        }

        val now = System.currentTimeMillis()
        if (now - lastDetectionAtMs < debounceMs) return null

        // ─── Stage 2: spectral analysis ───────────────────────────────────
        val mag = AudioFeatures.magnitudeSpectrum(samples, fftSize)
        val centroid = AudioFeatures.spectralCentroid(mag, sampleRate)
        val flatness = AudioFeatures.spectralFlatness(mag)
        val peakFreq = AudioFeatures.peakFrequency(mag, sampleRate)
        val midBand = AudioFeatures.bandRatio(mag, sampleRate, 600.0, 2300.0)
        val highBand = AudioFeatures.bandRatio(mag, sampleRate, 2500.0, 6000.0)

        peakHistory.addLast(peakFreq)
        if (peakHistory.size > peakHistoryCap) peakHistory.removeFirst()
        val peakSwing = if (peakHistory.size >= 3)
            peakHistory.max() - peakHistory.min()
        else 0.0

        Log.d(
            TAG,
            "rms=${"%.3f".format(rms)} centroid=${"%.0f".format(centroid)}Hz " +
                "peak=${"%.0f".format(peakFreq)}Hz swing=${"%.0f".format(peakSwing)}Hz " +
                "flat=${"%.2f".format(flatness)} mid=${"%.2f".format(midBand)} hi=${"%.2f".format(highBand)}"
        )

        // ─── Voice rejection ──────────────────────────────────────────────
        // Conversational speech / singing — very common false positive source.
        if (isLikelyVoice(rms, peakFreq, midBand, highBand, flatness)) {
            Log.d(TAG, "  ↳ rejected as voice/speech")
            recentCandidates.clear()
            return null
        }

        // ─── Classification ───────────────────────────────────────────────
        val candidate = classifyStrict(
            rms = rms,
            centroid = centroid,
            flatness = flatness,
            peakFreq = peakFreq,
            peakSwing = peakSwing,
            midBand = midBand,
            highBand = highBand
        )
        if (candidate == null) {
            recentCandidates.clear()
            return null
        }

        // ─── Sustained confirmation (except impulsive GLASS_BREAK) ───────
        recentCandidates.addLast(Candidate(candidate, now, rms))
        while (recentCandidates.isNotEmpty() &&
            now - recentCandidates.first().ts > confirmationWindowMs
        ) {
            recentCandidates.removeFirst()
        }

        val isImpulsive = candidate == SoundType.GLASS_BREAK
        if (!isImpulsive) {
            val matchingHits = recentCandidates.count { it.type == candidate }
            if (matchingHits < 2) {
                Log.d(TAG, "  ↳ candidate $candidate awaits confirm ($matchingHits/2)")
                return null
            }
        }

        // ─── Confirmed ────────────────────────────────────────────────────
        lastDetectionAtMs = now
        recentCandidates.clear()
        peakHistory.clear()

        val confidence = (0.55f + rms * 1.5f).coerceIn(0.6f, 0.95f)
        Log.i(TAG, "✓ CONFIRMED $candidate (rms=$rms, conf=$confidence)")
        return Detection(candidate, confidence)
    }

    /**
     * True if the spectral signature looks like a human speaking or singing.
     *
     * Three signatures we care about:
     *   1. Male voice — fundamental 60–200 Hz, peak in that band, very little >2.5 kHz.
     *   2. Female voice — fundamental 160–280 Hz, low high-band energy, mostly in voice formants.
     *   3. Loud sustained vowel / singing — energy concentrated in mid band, low high band, tonal.
     */
    private fun isLikelyVoice(
        rms: Float,
        peakFreq: Double,
        midBand: Double,
        highBand: Double,
        flatness: Double
    ): Boolean {
        // (1) Peak sits in the voice fundamental band → speech almost certainly.
        if (peakFreq in 60.0..280.0 && highBand < 0.18) return true

        // (2) Female voice fundamentals can reach ~350 Hz; still rejected if no high-band energy.
        if (peakFreq in 150.0..360.0 && highBand < 0.13 && flatness < 0.28) return true

        // (3) Sustained vowel / singing: mostly mid-band, very little high-band, moderate loudness.
        if (rms < 0.30f && midBand > 0.55 && highBand < 0.12) return true

        return false
    }

    /**
     * Strict classifier — every rule requires either high RMS OR a very specific spectral marker
     * that ordinary noise can't fake.
     */
    private fun classifyStrict(
        rms: Float,
        centroid: Double,
        flatness: Double,
        peakFreq: Double,
        peakSwing: Double,
        midBand: Double,
        highBand: Double
    ): SoundType? = when {
        // FIRE_ALARM ─ very tonal narrowband ~3 kHz, hi-band dominant
        peakFreq in 2500.0..3700.0 && flatness < 0.18 && highBand > 0.50 && rms > 0.12f ->
            SoundType.FIRE_ALARM

        // SIREN ─ tonal mid-band with strong frequency wail + must be loud + some hi content
        peakFreq in 600.0..2300.0 && flatness < 0.22 && peakSwing > 220.0 &&
            rms > 0.18f && highBand > 0.10 ->
            SoundType.SIREN

        // CAR_HORN ─ tonal sustained low-mid band, must be quite loud
        peakFreq in 280.0..650.0 && flatness < 0.20 && midBand > 0.40 && rms > 0.20f ->
            SoundType.CAR_HORN

        // GLASS_BREAK ─ broadband + very high centroid + loud impulsive
        centroid > 3000.0 && flatness > 0.38 && highBand > 0.45 && rms > 0.20f ->
            SoundType.GLASS_BREAK

        // SHOUT_HELP ─ vocal effort signature: loud + above voice fundamental + significant hi content
        rms > 0.25f && peakFreq > 350.0 && highBand > 0.22 &&
            centroid > 1000.0 && flatness in 0.28..0.55 ->
            SoundType.SHOUT_HELP

        // BABY_CRY ─ tonal in 0.8–2.5 kHz, sustained (low swing), mid-band heavy
        peakFreq in 800.0..2500.0 && flatness in 0.18..0.32 &&
            peakSwing < 200.0 && midBand > 0.45 && rms > 0.15f ->
            SoundType.BABY_CRY

        else -> null
    }

    fun resetCooldown() {
        lastDetectionAtMs = 0L
        peakHistory.clear()
        recentCandidates.clear()
    }

    companion object {
        private const val TAG = "SoundDetector"
    }
}

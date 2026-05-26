package com.example.pulsewatch.safety

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.pulsewatch.data.SoundAlertPayload
import com.example.pulsewatch.data.SoundDirection
import com.example.pulsewatch.data.SoundType

class HapticController(context: Context) {

    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun vibrateForAlert(payload: SoundAlertPayload) {
        val effect = when (payload.type) {
            SoundType.SIREN -> sirenEffect(payload.direction)
            SoundType.FIRE_ALARM -> fireAlarmEffect(payload.direction)
            SoundType.SHOUT_HELP -> shoutHelpEffect(payload.direction)
            SoundType.CAR_HORN -> hornEffect(payload.direction)
            SoundType.BABY_CRY -> babyCryEffect(payload.direction)
            SoundType.GLASS_BREAK -> glassBreakEffect(payload.direction)
            SoundType.DOG_BARK -> barkEffect(payload.direction)
            // RO-ALERT (Romania civil emergency cell-broadcast) — uses the
            // strongest CRITICAL pattern so it's distinguishable from polite
            // ambient alerts and never feels casual.
            SoundType.RO_ALERT -> fireAlarmEffect(payload.direction)
        }
        vibrator.vibrate(effect)
    }

    // Wailing whoops — 3 long pulses at full strength
    private fun sirenEffect(dir: SoundDirection): VibrationEffect {
        val timings = longArrayOf(0, 550, 130, 550, 130, 550)
        val base = intArrayOf(255, 0, 255, 0, 255, 0)
        return VibrationEffect.createWaveform(timings, directional(dir, base), -1)
    }

    // Aggressive staccato bursts at full amplitude
    private fun fireAlarmEffect(dir: SoundDirection): VibrationEffect {
        val timings = longArrayOf(0, 130, 60, 130, 60, 130, 60, 130, 60, 130, 60, 130, 250)
        val base = intArrayOf(255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 0)
        return VibrationEffect.createWaveform(timings, directional(dir, base), -1)
    }

    // SOS pattern · · · — — — · · ·   (longer dots & dashes)
    private fun shoutHelpEffect(dir: SoundDirection): VibrationEffect {
        val timings = longArrayOf(
            0, 130, 80, 130, 80, 130,
            220, 350, 130, 350, 130, 350,
            220, 130, 80, 130, 80, 130
        )
        val base = intArrayOf(
            255, 0, 255, 0, 255, 0,
            0, 255, 0, 255, 0, 255,
            0, 255, 0, 255, 0, 255
        )
        return VibrationEffect.createWaveform(timings, directional(dir, base), -1)
    }

    // Short attack + sustained blare (car horn)
    private fun hornEffect(dir: SoundDirection): VibrationEffect {
        val timings = longArrayOf(0, 180, 100, 700)
        val base = intArrayOf(255, 0, 0, 255)
        return VibrationEffect.createWaveform(timings, directional(dir, base), -1)
    }

    // Rhythmic urgent pulses (baby cry)
    private fun babyCryEffect(dir: SoundDirection): VibrationEffect {
        val timings = longArrayOf(0, 280, 130, 280, 130, 280, 130, 280)
        val base = intArrayOf(230, 0, 230, 0, 230, 0, 230, 0)
        return VibrationEffect.createWaveform(timings, directional(dir, base), -1)
    }

    // Sharp crack + sustained shimmer (glass break)
    private fun glassBreakEffect(dir: SoundDirection): VibrationEffect {
        val timings = longArrayOf(0, 90, 40, 260, 50, 180, 50, 140)
        val base = intArrayOf(255, 0, 230, 0, 200, 0, 170, 0)
        return VibrationEffect.createWaveform(timings, directional(dir, base), -1)
    }

    // Triple firm bark pulse
    private fun barkEffect(dir: SoundDirection): VibrationEffect {
        val timings = longArrayOf(0, 200, 110, 200, 110, 200)
        val base = intArrayOf(240, 0, 240, 0, 240, 0)
        return VibrationEffect.createWaveform(timings, directional(dir, base), -1)
    }

    /**
     * Encodes direction into amplitude envelope:
     *   LEFT  = fade-out  (loud start → quiet end: "sound coming from the left")
     *   RIGHT = fade-in   (quiet start → loud end: "sound coming from the right")
     *   CENTER = steady 80%
     */
    private fun directional(dir: SoundDirection, base: IntArray): IntArray {
        val vibIndices = base.indices.filter { base[it] > 0 }
        val n = vibIndices.size
        if (n == 0) return base

        // Keep amplitudes high so the alert is unmistakable on the wrist —
        // direction is a *taper*, not a mute: never drop below ~70%.
        val factors = when (dir) {
            SoundDirection.LEFT -> FloatArray(n) { i -> 1.0f - i.toFloat() / n * 0.30f }
            SoundDirection.RIGHT -> FloatArray(n) { i -> 0.70f + i.toFloat() / n * 0.30f }
            SoundDirection.CENTER -> FloatArray(n) { 1.0f }
        }

        var fi = 0
        return IntArray(base.size) { i ->
            if (base[i] == 0) 0
            else (base[i] * factors[fi++]).toInt().coerceIn(1, 255)
        }
    }
}

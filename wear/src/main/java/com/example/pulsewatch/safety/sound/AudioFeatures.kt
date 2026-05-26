package com.example.pulsewatch.safety.sound

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight audio DSP utilities — RMS, windowed FFT (radix-2), and spectral features.
 * No external dependencies. All FFTs are in-place and allocate two FloatArrays of size n.
 */
object AudioFeatures {

    /** Normalized RMS in [0,1]. */
    fun rms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sum += v * v
        }
        return (sqrt(sum / samples.size) / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    /** Number of zero crossings divided by sample count, in [0,1]. */
    fun zeroCrossingRate(samples: ShortArray): Float {
        if (samples.size < 2) return 0f
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0) != (samples[i] >= 0)) crossings++
        }
        return crossings.toFloat() / samples.size
    }

    /**
     * Compute magnitude spectrum (length fftSize/2) from a mono short[] window.
     * fftSize must be a power of two; if samples is longer, takes the last fftSize.
     * Applies a Hann window before FFT.
     */
    fun magnitudeSpectrum(samples: ShortArray, fftSize: Int): FloatArray {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) { "fftSize must be power of 2" }
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val start = maxOf(0, samples.size - fftSize)
        val n = minOf(fftSize, samples.size - start)
        // Copy + Hann window
        for (i in 0 until n) {
            val w = 0.5f * (1f - cos(2.0 * PI * i / (fftSize - 1)).toFloat())
            re[i] = samples[start + i] / 32768f * w
        }
        fftRadix2(re, im)
        val half = fftSize / 2
        val mag = FloatArray(half)
        for (k in 0 until half) {
            mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
        }
        return mag
    }

    /** In-place radix-2 Cooley-Tukey FFT. n must be a power of two. */
    private fun fftRadix2(re: FloatArray, im: FloatArray) {
        val n = re.size
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        // Butterfly stages
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wlre = cos(angle).toFloat()
            val wlim = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wre = 1.0f
                var wim = 0.0f
                val half = len / 2
                for (k in 0 until half) {
                    val xRe = re[i + k]
                    val xIm = im[i + k]
                    val yRe = re[i + k + half]
                    val yIm = im[i + k + half]
                    val tr = wre * yRe - wim * yIm
                    val ti = wre * yIm + wim * yRe
                    re[i + k] = xRe + tr
                    im[i + k] = xIm + ti
                    re[i + k + half] = xRe - tr
                    im[i + k + half] = xIm - ti
                    val nwre = wre * wlre - wim * wlim
                    wim = wre * wlim + wim * wlre
                    wre = nwre
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Spectral centroid in Hz — perceptual "brightness". */
    fun spectralCentroid(magnitude: FloatArray, sampleRate: Int): Double {
        var weighted = 0.0
        var total = 0.0
        val binHz = sampleRate.toDouble() / (magnitude.size * 2)
        for (k in magnitude.indices) {
            val m = magnitude[k].toDouble()
            weighted += m * (k * binHz)
            total += m
        }
        return if (total > 0) weighted / total else 0.0
    }

    /**
     * Spectral flatness in [0,1]. ~0 = pure tone, ~1 = white noise.
     * Computed only over a useful band (skip DC + very-low-freq rumble).
     */
    fun spectralFlatness(magnitude: FloatArray): Double {
        // Skip first 4 bins to avoid DC + sub-bass dominance
        val start = 4
        if (magnitude.size <= start + 8) return 0.0
        var logSum = 0.0
        var arithSum = 0.0
        var count = 0
        for (k in start until magnitude.size) {
            val m = magnitude[k] + 1e-10f
            logSum += ln(m.toDouble())
            arithSum += m.toDouble()
            count++
        }
        val geo = kotlin.math.exp(logSum / count)
        val arith = arithSum / count
        return if (arith > 0) (geo / arith).coerceIn(0.0, 1.0) else 0.0
    }

    /** Frequency (Hz) of the loudest non-DC bin. */
    fun peakFrequency(magnitude: FloatArray, sampleRate: Int): Double {
        if (magnitude.size < 4) return 0.0
        var bestK = 1
        var bestMag = magnitude[1]
        for (k in 2 until magnitude.size) {
            if (magnitude[k] > bestMag) {
                bestMag = magnitude[k]
                bestK = k
            }
        }
        val binHz = sampleRate.toDouble() / (magnitude.size * 2)
        return bestK * binHz
    }

    /** Sum of magnitude in a frequency band, normalized by total. */
    fun bandRatio(magnitude: FloatArray, sampleRate: Int, lowHz: Double, highHz: Double): Double {
        val binHz = sampleRate.toDouble() / (magnitude.size * 2)
        val lo = (lowHz / binHz).toInt().coerceIn(0, magnitude.size - 1)
        val hi = (highHz / binHz).toInt().coerceIn(lo + 1, magnitude.size)
        var bandSum = 0.0
        var totalSum = 0.0
        for (k in magnitude.indices) {
            val m = magnitude[k].toDouble()
            totalSum += m
            if (k in lo until hi) bandSum += m
        }
        return if (totalSum > 0) bandSum / totalSum else 0.0
    }
}

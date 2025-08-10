package com.example.steplab.algorithms

import android.hardware.SensorManager
import java.math.BigDecimal
import java.math.MathContext
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Calculations(
    private val configuration: Configuration = Configuration(),
    private val FALSE_STEP_THR:Float = 1.2f
) {

    /** Resultant magnitude sqrt(x^2 + y^2 + z^2). */
    fun resultant(vector: Array<BigDecimal>): BigDecimal {
        val x = vector[0].toDouble()
        val y = vector[1].toDouble()
        val z = vector[2].toDouble()
        val result = kotlin.math.sqrt(x * x + y * y + z * z)
        return BigDecimal.valueOf(result)
    }

    /** Linear acceleration (remove gravity). */
    fun linearAcceleration(resultant: BigDecimal): BigDecimal =
        resultant.subtract(configuration.gravity)

    private val rotationMatrix = FloatArray(16)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val worldResult = Array(3) { BigDecimal.ZERO }
    private var rotationMatrixCached = false

    /** Cache rotation matrix (SensorManager). */
    fun updateRotationMatrix(
        accel: Array<BigDecimal>,
        magnet: Array<BigDecimal>
    ) {
        for (i in accel.indices) {
            gravity[i] = accel[i].toFloat()
            geomagnetic[i] = magnet[i].toFloat()
        }
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            val matrix3x3 = arrayOf(
                arrayOf(
                    BigDecimal.valueOf(rotationMatrix[0].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[1].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[2].toDouble())
                ),
                arrayOf(
                    BigDecimal.valueOf(rotationMatrix[4].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[5].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[6].toDouble())
                ),
                arrayOf(
                    BigDecimal.valueOf(rotationMatrix[8].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[9].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[10].toDouble())
                )
            )
            configuration.rotationMatrix = matrix3x3
            rotationMatrixCached = true
        }
    }

    /** Transform accelerometer to world frame using cached rotation matrix. */
    fun worldAcceleration(accel: Array<BigDecimal>): Array<BigDecimal> {
        for (i in 0..2) {
            var sum = BigDecimal.ZERO
            for (j in 0..2) {
                sum = sum.add(
                    configuration.rotationMatrix[i][j]
                        .multiply(accel[j], MathContext.DECIMAL32),
                    MathContext.DECIMAL32
                )
            }
            worldResult[i] = sum
        }
        return worldResult
    }

    /** Simple FP rejector with magnetometer stats. */
    fun checkFalseStep(
        last4Steps: MutableList<Float>,
        currentStepMean: Float
    ): Boolean {
        require(last4Steps.size == 4)
        val sum4 = last4Steps.sum()
        val m4   = sum4 / 4f
        val m5   = (sum4 + currentStepMean) / 5f
        val diff = kotlin.math.abs(m4 - m5)
        return diff > FALSE_STEP_THR
    }


    fun sumOfMagnet(magnetValues: List<Float>): Float = magnetValues.sum()

    data class AutoCorrStepResult(
        val steps: Int,
        val f0Hz: Double,
        val bandLowHz: Double,
        val bandHighHz: Double,
        val segments: List<Pair<Int, Int>>, // [start,end] in samples
        val lagsPerSegment: List<Int>,
        val autocorrPeaks: List<Double>
    )

    // ---- internal state (used to adapt windows / lag search) ----
    private var lastFs: Int = -1
    private var lastF0Hz: Double = Double.NaN

    /** Magnitude without DC component. */
    fun computeMagnitudeWithoutDC(samples: List<Array<BigDecimal>>): DoubleArray {
        val mag = DoubleArray(samples.size) { i ->
            val x = samples[i][0].toDouble()
            val y = samples[i][1].toDouble()
            val z = samples[i][2].toDouble()
            kotlin.math.sqrt(x * x + y * y + z * z)
        }
        return removeDC(mag)
    }

    /**
     * Optional decimation to a more “pedometer-friendly” rate (~50–60 Hz).
     * Call this right after computeMagnitudeWithoutDC if your fs is very high.
     */
    fun downsampleForWalking(
        x: DoubleArray,
        fs: Int,
        targetFs: Int = 50
    ): Pair<DoubleArray, Int> {
        if (fs <= targetFs * 2) return x to fs
        val factor = max(2, kotlin.math.floor(fs.toDouble() / targetFs).toInt())
        val newFs = fs / factor
        val n = x.size / factor
        val y = DoubleArray(n)
        // Simple decimation (OK because walking band << new Nyquist)
        var j = 0
        var i = 0
        while (j < n && i < x.size) {
            y[j++] = x[i]
            i += factor
        }
        return y to newFs
    }

    /**
     * Estimate fundamental frequency with optional Hann and bounded search.
     * Stores fs/f0 internally to guide later steps (MSD window, ACF lag search, merging).
     */
    fun estimateFundamentalFrequency(
        magnitudeSignal: DoubleArray,
        fs: Int,
        useHannWindow: Boolean = true,
        fMin: Double = 1.0,
        fMax: Double = 3.5
    ): Double {
        lastFs = fs
        val nyq = fs / 2.0
        val fHi = min(fMax, nyq * 0.9)
        val xForFFT = if (useHannWindow) applyHann(magnitudeSignal) else magnitudeSignal
        val result = estimateF0HzSingleSided(xForFFT, fs, fMin, fHi)
        lastF0Hz = result
        return result
    }

    /** Compute passband [1 Hz, f0+0.5], clamped to 0.9*Nyquist. */
    fun computeBandPassRange(f0Hz: Double, fs: Int): Pair<Double, Double> {
        val nyq = fs / 2.0
        val low = 1.0
        val high = min(f0Hz + 0.5, nyq * 0.9)
        require(low < high) { "Invalid band: [$low,$high] at fs=$fs" }
        return Pair(low, high)
    }

    /**
     * Moving std with window scaled by fs (~40 ms like “2 samples @ 50 Hz”).
     * If fs unknown, falls back to 2 samples.
     */
    fun computeMovingStandardDeviation(signal: DoubleArray): DoubleArray {
        val w = if (lastFs > 0) max(2, (0.040 * lastFs).roundToInt()) else 2
        return movingStdWindowN(signal, w)
    }

    /**
     * Segments where MSD > threshold; optionally skip first samples (filter transient).
     * Auto-merge short gaps, and drop segments shorter than ~1–2 cycles.
     */
    fun findSegmentsAboveThreshold(
        msd: DoubleArray,
        threshold: Double,
        skipSamples: Int = 0
    ): List<Pair<Int, Int>> {
        val base = segmentsAboveThreshold(msd, threshold, skipSamples)
        if (base.isEmpty()) return base

        // Merge and length-filter driven by fs/f0, if known
        val fs = lastFs.takeIf { it > 0 } ?: return base
        val f0 = if (!lastF0Hz.isNaN()) lastF0Hz else 2.0
        val minLen = max(
            (1.0 * fs / max(1.0, f0)).roundToInt(),        // ~1 cycle
            (0.60 * fs).roundToInt()                        // or at least 0.6 s
        )
        val maxGap = (0.20 * fs).roundToInt()               // merge gaps < 0.2 s

        val merged = mergeSegments(base, maxGap)
        return merged.filter { (s, e) -> (e - s + 1) >= minLen }
    }

    /** Normalized autocorrelation of a segment (unbiased). */
    fun computeNormalizedAutocorrelation(segment: DoubleArray): DoubleArray =
        autocorrNormalizedBiased(segment)

    /**
     * Find first local ACF peak ≥ threshold within plausible lag range from fs/f0.
     * Falls back to full search if fs/f0 unknown.
     */
    fun findFirstPeakAboveThreshold(
        autocorrelation: DoubleArray,
        threshold: Double
    ): Pair<Int, Double> {
        val n = autocorrelation.size
        if (n < 3) return 0 to (autocorrelation.getOrNull(1) ?: 0.0)

        val fs = lastFs
        val f0 = lastF0Hz
        val hasParams = fs > 0 && !f0.isNaN() && f0 > 0.5

        val (minLag, maxLag) = if (hasParams) {
            val k0 = (fs / f0).roundToInt()
            val minK = max(1, (k0 * 0.65).roundToInt())
            val maxK = min(n - 2, (k0 * 1.5).roundToInt())
            minK to maxK
        } else {
            // conservative generic range
            val minK = max(1, (fs.takeIf { it > 0 }?.let { floor(it / 3.5) } ?: 2.0).toInt())
            val maxK = min(n - 2, (fs.takeIf { it > 0 }?.let { ceil(it / 1.0) } ?: (n - 2).toDouble()).toInt())
            minK to maxK
        }

        return firstPeakAbove(autocorrelation, threshold, minLag, maxLag)
    }

    /** Stddev helper. */
    fun computeStandardDeviation(signal: DoubleArray): Double = stddev(signal)


    // ------------------- NEW: step counting guards -------------------

    /**
     * Convert an autocorrelation lag into steps for a segment.
     * - Usa steps = round(len / k).
     * - Mai moltiplicare per 2.
     * - Se i passi stimati eccedono del 35% quelli attesi dalla cadenza globale,
     *   applica una correzione "harmonic": dimezza.
     */
    fun stepsFromLagSafe(
        segLen: Int,
        lagK: Int,
        fs: Int,
        f0HzGlobal: Double? = null
    ): Int {
        if (segLen <= 0 || lagK <= 0) return 0
        var steps = ((segLen.toDouble() / lagK) + 0.5).toInt()

        val f0 = f0HzGlobal
        if (f0 != null && f0.isFinite() && f0 > 0.0 && fs > 0) {
            val expected = segLen.toDouble() / fs * f0 // passi attesi nel segmento
            val maxAllowed = kotlin.math.ceil(expected * 1.35).toInt()
            if (steps > maxAllowed && steps >= 3) {
                // tipico caso stride/step confuso -> dimezza
                steps = max(1, (steps / 2.0).roundToInt())
            }
        }
        return steps
    }

    /**
     * Clamp finale del conteggio totale ai valori compatibili con la cadenza globale.
     * Utile quando diversi segmenti sono un po’ ottimistici.
     */
    fun clampStepsToCadence(
        totalSteps: Int,
        activeSamples: Int,
        fs: Int,
        f0HzGlobal: Double
    ): Int {
        if (totalSteps <= 0 || fs <= 0 || !f0HzGlobal.isFinite() || f0HzGlobal <= 0.0) return max(0, totalSteps)
        val expected = activeSamples.toDouble() / fs * f0HzGlobal
        val maxAllowed = kotlin.math.ceil(expected * 1.35).toInt() // tolleranza +35%
        val minAllowed = kotlin.math.floor(expected * 0.65).toInt() // tolleranza -35%

        var out = totalSteps
        // correzione dura se siamo vicini al raddoppio
        if (out > expected * 1.75) {
            out = (out / 2.0).roundToInt()
        }
        // clamp soft nei limiti consentiti
        out = out.coerceIn(minAllowed, maxAllowed).coerceAtLeast(0)
        return out
    }

    // ============ Internal helpers ============

    private fun removeDC(x: DoubleArray): DoubleArray {
        val m = x.average()
        return DoubleArray(x.size) { i -> x[i] - m }
    }

    private fun applyHann(x: DoubleArray): DoubleArray {
        val n = x.size
        val y = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / (n - 1)))
            y[i] = x[i] * w
        }
        return y
    }

    /** Single-sided FFT peak search within [fMin,fMax]. */
    private fun estimateF0HzSingleSided(
        x: DoubleArray,
        fs: Int,
        fMin: Double,
        fMax: Double
    ): Double {
        val n = x.size
        val a = x.copyOf()
        DoubleFFT_1D(n.toLong()).realForward(a)

        val half = n / 2
        val kMin = max(1, kotlin.math.ceil(fMin * n / fs).toInt())
        val kMaxBound = half
        val kMax = min(kMaxBound, kotlin.math.floor(fMax * n / fs).toInt())

        var peakK = kMin
        var peakVal = Double.NEGATIVE_INFINITY

        val upper = if (n % 2 == 0) min(kMax, half) else kMax
        for (k in kMin until min(upper, half)) {
            val re = a[2 * k]
            val im = a[2 * k + 1]
            val mag = kotlin.math.hypot(re, im)
            if (mag > peakVal) {
                peakVal = mag
                peakK = k
            }
        }
        if (n % 2 == 0 && half in kMin..kMax) {
            val nyqMag = kotlin.math.abs(a[1])
            if (nyqMag > peakVal) {
                peakVal = nyqMag
                peakK = half
            }
        }
        return peakK * fs.toDouble() / n
    }

    /** MSD with generic window size w. */
    private fun movingStdWindowN(x: DoubleArray, w: Int): DoubleArray {
        if (x.isEmpty() || w <= 1) return movingStdWindow2(x) // fallback
        val n = x.size
        val out = DoubleArray(n)
        var sum = 0.0
        var sum2 = 0.0

        // init
        val w0 = min(w, n)
        for (i in 0 until w0) { sum += x[i]; sum2 += x[i]*x[i] }
        for (i in 0 until n) {
            val start = max(0, i - w + 1)
            val end = i
            if (i >= w) {
                val old = x[i - w]
                sum -= old
                sum2 -= old*old
            }
            val win = end - start + 1
            val m = sum / win
            val v = (sum2 - win * m * m) / win
            out[i] = kotlin.math.sqrt(max(0.0, v))
            if (i + 1 < n) {
                val next = x[i + 1]
                sum += next
                sum2 += next*next
            }
        }
        return out
    }

    /** MSD with window size 2 (legacy). */
    private fun movingStdWindow2(x: DoubleArray): DoubleArray {
        if (x.size < 2) return DoubleArray(x.size)
        val out = DoubleArray(x.size)
        for (i in 1 until x.size) {
            val m = 0.5 * (x[i] + x[i - 1])
            val v = 0.5 * ((x[i] - m) * (x[i] - m) + (x[i - 1] - m) * (x[i - 1] - m))
            out[i] = kotlin.math.sqrt(v)
        }
        return out
    }

    private fun stddev(x: DoubleArray): Double {
        val m = x.average()
        var s = 0.0
        for (v in x) s += (v - m) * (v - m)
        return kotlin.math.sqrt(s / x.size)
    }

    /** Base contiguous segments above threshold; optionally skip first samples. */
    private fun segmentsAboveThreshold(
        msd: DoubleArray,
        threshold: Double,
        skip: Int = 0
    ): List<Pair<Int, Int>> {
        val segs = mutableListOf<Pair<Int, Int>>()
        var i = min(skip, msd.size)
        while (i < msd.size) {
            while (i < msd.size && msd[i] <= threshold) i++
            val s = i
            while (i < msd.size && msd[i] > threshold) i++
            val e = i - 1
            if (e >= s && s < msd.size) segs.add(s to e)
        }
        return segs
    }

    /** Merge consecutive segments if gap <= maxGap. */
    private fun mergeSegments(
        segs: List<Pair<Int, Int>>,
        maxGap: Int
    ): List<Pair<Int, Int>> {
        if (segs.isEmpty()) return segs
        val out = mutableListOf<Pair<Int, Int>>()
        var (cs, ce) = segs[0]
        for (idx in 1 until segs.size) {
            val (s, e) = segs[idx]
            if (s - ce - 1 <= maxGap) {
                ce = max(ce, e)
            } else {
                out.add(cs to ce)
                cs = s; ce = e
            }
        }
        out.add(cs to ce)
        return out
    }

    /** Unbiased normalized autocorrelation: compensates for (n-k). */
    private fun autocorrNormalizedBiased(x: DoubleArray): DoubleArray {
        val n = x.size
        if (n == 0) return DoubleArray(0)
        val m = x.average()
        val y = DoubleArray(n) { i -> x[i] - m }
        var denom = 0.0; for (i in 0 until n) denom += y[i]*y[i]
        if (denom == 0.0) return DoubleArray(n)
        val rho = DoubleArray(n)
        for (k in 0 until n) {
            var s = 0.0
            for (i in 0 until n-k) s += y[i]*y[i+k]
            rho[k] = (s/denom).coerceIn(-1.0, 1.0)
        }
        rho[0] = 1.0
        return rho
    }

    /** First local peak (minLag..maxLag) ≥ threshold; else (0, rho[1] or 0). */
    private fun firstPeakAbove(
        rho: DoubleArray,
        threshold: Double,
        minLag: Int,
        maxLag: Int
    ): Pair<Int, Double> {
        val start = max(1, minLag)
        val end = min(maxLag, rho.size - 2)
        var bestK = -1
        var bestV = Double.NEGATIVE_INFINITY

        for (k in start..end) {
            val v = rho[k]
            if (v > bestV) { bestV = v; bestK = k }
            val isLocalPeak = v >= threshold && v + 1e-6 >= rho[k-1] && v + 1e-6 >= rho[k+1]
            if (isLocalPeak) return k to v
        }

        if (bestK >= 0 && bestV >= threshold * 0.95) {
            return bestK to bestV
        }
        return 0 to (rho.getOrNull(1) ?: 0.0)
    }
}

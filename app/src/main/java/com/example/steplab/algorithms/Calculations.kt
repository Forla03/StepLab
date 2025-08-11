package com.example.steplab.algorithms

import android.hardware.SensorManager
import java.math.BigDecimal
import java.math.MathContext
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.ceil
import kotlin.math.floor
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
     * Used for fs very high.
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

    /** Stddev helper. */
    fun computeStandardDeviation(signal: DoubleArray): Double = stddev(signal)


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

}

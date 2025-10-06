package com.example.steplab.algorithms

import android.hardware.SensorManager
import uk.me.berndporr.iirj.Butterworth
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.min
import kotlin.math.max

/**
 * Filtering utilities for the step-detection pipeline (IPIN 2019, Fig. 8).
 * Minimal comments in English as requested.
 * 
 * CRITICAL FIX: Separate filter state per sensor to prevent cross-contamination.
 */
class Filters(
    private val configuration: Configuration
) {
    // ----- Legacy LP utilities (3D, BigDecimal) -----
    // CRITICAL FIX: Use separate state for different sensors
    private val accelerometerFilterState = Array(3) { BigDecimal.ZERO }
    private val magnetometerFilterState = Array(3) { BigDecimal.ZERO }
    private val rotationFilterState = Array(3) { BigDecimal.ZERO }

    private val butterworthFilters = Array(3) { Butterworth() }
    private var butterworthInitialized = false
    private var lastCutoff: Double = -1.0
    private var lastSamplingRate: Int = -1

    fun lowPassFilter(
        input: Array<BigDecimal>,
        alpha: BigDecimal
    ): Array<BigDecimal> {
        for (i in input.indices) {
            accelerometerFilterState[i] = accelerometerFilterState[i].add(
                alpha.multiply(
                    input[i].subtract(accelerometerFilterState[i]),
                    MathContext.DECIMAL32
                ),
                MathContext.DECIMAL32
            )
        }
        // Return a copy to avoid shared references
        return arrayOf(
            accelerometerFilterState[0], 
            accelerometerFilterState[1], 
            accelerometerFilterState[2]
        )
    }

    fun bagileviFilter(input: Array<BigDecimal>): BigDecimal {
        var sum = BigDecimal.ZERO
        for (i in input.indices) {
            sum = sum.add(
                BigDecimal.valueOf(4).multiply(
                    BigDecimal.valueOf(SensorManager.MAGNETIC_FIELD_EARTH_MAX.toDouble())
                        .subtract(input[i]),
                    MathContext.DECIMAL32
                ),
                MathContext.DECIMAL32
            )
        }
        return sum.divide(BigDecimal.valueOf(3), MathContext.DECIMAL32)
    }

    /**
     * Per-axis low-pass with persistent state.
     */
    fun butterworthFilter(
        threeAxisVector: Array<BigDecimal>,
        cutoff: Double,
        samplingRate: Int
    ): Array<BigDecimal> {
        val output = Array(3) { BigDecimal.ZERO }

        if (!butterworthInitialized || cutoff != lastCutoff || samplingRate != lastSamplingRate) {
            initializeButterworthFilters(cutoff, samplingRate)
            butterworthInitialized = true
            lastCutoff = cutoff
            lastSamplingRate = samplingRate
            
            //Warm-up the filter with the first input to avoid transient
            // Feed the first value multiple times to stabilize the filter state
            for (warmUpIteration in 0 until 10) {
                for (axis in 0 until 3) {
                    butterworthFilters[axis].filter(threeAxisVector[axis].toDouble())
                }
            }
        }

        for (i in 0 until 3) {
            val filteredValue = butterworthFilters[i].filter(threeAxisVector[i].toDouble())
            output[i] = BigDecimal.valueOf(filteredValue)
            accelerometerFilterState[i] = output[i]
        }
        return output
    }

    private fun initializeButterworthFilters(cutoffInput: Double, samplingFreqValue: Int) {
        val sampleRate = samplingFreqValue.toDouble()
        val nyq = sampleRate / 2.0
        val cutoff = when {
            cutoffInput <= 0 -> 1.0
            cutoffInput >= nyq -> nyq * 0.9
            else -> cutoffInput
        }
        for (i in 0..2) {
            butterworthFilters[i].lowPass(
                2,            // order
                sampleRate,
                cutoff
            )
        }
    }

    fun resetFilterState() {
        for (i in 0..2) {
            accelerometerFilterState[i] = BigDecimal.ZERO
            magnetometerFilterState[i] = BigDecimal.ZERO
            rotationFilterState[i] = BigDecimal.ZERO
        }
        // Reset Butterworth filters completely to avoid transient spikes
        butterworthInitialized = false
        lastCutoff = -1.0
        lastSamplingRate = -1
        // Reinitialize the filter objects to clear internal state
        for (i in 0..2) {
            butterworthFilters[i] = Butterworth()
        }
        // Reset band-pass filter state
        bp = Butterworth()
        bpConfigured = false
        bpLow = -1.0
        bpHigh = -1.0
        bpFs = -1
        bpOrder = -1
    }

    // ----- Magnitude band-pass -----

    private var bp = Butterworth()
    private var bpConfigured = false
    private var bpLow = -1.0
    private var bpHigh = -1.0
    private var bpFs = -1
    private var bpOrder = -1

    /**
     * Configure single IIR for magnitude band-pass (center/bandwidth form).
     */
    private fun configureMagnitudeBandPass(
        fs: Int,
        low: Double,
        high: Double,
        order: Int = 6
    ) {
        require(order >= 2 && order % 2 == 0) { "order must be even and >= 2" }
        val (lo, hi) = sanitizeBand(fs, low, high)
        val center = (lo + hi) / 2.0
        val bwHz = hi - lo
        bp = Butterworth().apply { bandPass(order, fs.toDouble(), center, bwHz) }
        bpConfigured = true
        bpLow = lo; bpHigh = hi; bpFs = fs; bpOrder = order
    }

    /**
     * Filter a full magnitude series (batch). Keeps state for later streaming if desired.
     */
    fun filterMagnitudeBandPassSeries(
        magNoDc: DoubleArray,
        fs: Int,
        low: Double,
        high: Double,
        order: Int = 6
    ): DoubleArray {
        require(magNoDc.isNotEmpty()) { "magNoDc is empty" }
        if (!bpConfigured || fs != bpFs || low != bpLow || high != bpHigh || order != bpOrder) {
            configureMagnitudeBandPass(fs, low, high, order)
        }
        val y = DoubleArray(magNoDc.size)
        for (i in magNoDc.indices) y[i] = bp.filter(magNoDc[i])
        return y
    }


    // ----- Helpers -----

    /** Clamp band to sensible values: 0 < low < high < 0.9*Nyquist. */
    private fun sanitizeBand(fs: Int, low: Double, high: Double): Pair<Double, Double> {
        require(fs > 1) { "fs must be > 1 Hz" }
        val nyq = fs / 2.0
        val lo = max(0.01, low)
        val hi = min(high, nyq * 0.9)
        require(lo < hi) { "Invalid band: [$lo,$hi] at fs=$fs" }
        return lo to hi
    }
}

package com.example.steplab.algorithms

import android.hardware.SensorManager
import java.math.BigDecimal
import java.math.MathContext
import uk.me.berndporr.iirj.Butterworth

/**
 * Provides filtering utilities for step recognition algorithm.
 */
class Filters(
    private val configuration: Configuration
) {
    private val filteredComponents = Array(3) { BigDecimal.ZERO }

    private var butterworthFilters = arrayOfNulls<Butterworth>(3)
    private var currentCutoff: Double = -1.0

    /**
     * Butterworth filter: filtered[i] = butterworth[i].filter(input[i])
     */
    fun butterworthFilter(input: Array<BigDecimal>): Array<BigDecimal> {
        val cutoff = calculateDynamicCutoff()

        if (cutoff <= 0.0) return input

        if (cutoff != currentCutoff || butterworthFilters[0] == null) {
            for (i in 0..2) {
                butterworthFilters[i] = Butterworth().apply {
                    lowPass(2, configuration.samplingRate.toDouble(), cutoff)
                }
            }
            currentCutoff = cutoff
        }

        val filtered = Array(3) { i ->
            val value = butterworthFilters[i]!!.filter(input[i].toDouble())
            BigDecimal.valueOf(value)
        }

        return filtered
    }

    /**
     * Low-pass filter: filtered[i] = filtered[i] + alpha * (input[i] - filtered[i])
     */
    fun lowPassFilter(
        input: Array<BigDecimal>,
        alpha: BigDecimal
    ): Array<BigDecimal> {
        for (i in input.indices) {
            filteredComponents[i] = filteredComponents[i].add(
                alpha.multiply(
                    input[i].subtract(filteredComponents[i]),
                    MathContext.DECIMAL32
                ),
                MathContext.DECIMAL32
            )
        }
        return filteredComponents
    }

    /**
     * Bagilevi filter: returns average of 4*(MAGNETIC_FIELD_EARTH_MAX - input[i])
     */
    fun bagileviFilter(
        input: Array<BigDecimal>
    ): BigDecimal {
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
    private fun calculateDynamicCutoff(): Double {
        val diffMagn = configuration.lastDiffMagnitude.toDouble() ?: return 0.0
        val timeDiff = configuration.lastStepTimeDiff.toDouble() ?: return 0.0
        val samplingRate = configuration.samplingRate.toDouble()

        if (diffMagn == 0.0) return 0.0

        val fi2 = samplingRate / (diffMagn * 2.0)
        var cutoff = 0.0

        if (fi2 < 81) {
            cutoff = samplingRate / 6.0
            if (timeDiff > 20.0) cutoff = samplingRate / 15.0
        } else if (fi2 < 183) {
            cutoff = samplingRate / 7.0
            if (timeDiff > 40.0) cutoff = samplingRate / 20.0
        } else if (timeDiff < 2.1) {
            configuration.isFalseStep = true
        }

        return cutoff
    }

    /**
     * Applies a Butterworth band-pass filter centered around the fundamental frequency.
     * This filter is designed **only for** the ACF algorithm (non real-time).
     */
    fun butterworthBandPassForACF(
        signal: List<Double>,
        samplingRate: Int,
        fundamentalFreq: Double
    ): List<Double> {
        val lowCutoff = 1.0
        val highCutoff = fundamentalFreq + 0.5

        val centerFreq = (lowCutoff + highCutoff) / 2.0
        val bandwidth = (highCutoff - lowCutoff)

        val filter = Butterworth()
        filter.bandPass(6, samplingRate.toDouble(), centerFreq, bandwidth / 2.0)

        return signal.map { filter.filter(it) }
    }
}

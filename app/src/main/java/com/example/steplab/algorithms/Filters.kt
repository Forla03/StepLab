package com.example.steplab.algorithms

import android.hardware.SensorManager
import uk.me.berndporr.iirj.Butterworth
import java.math.BigDecimal
import java.math.MathContext

/**
 * Provides filtering utilities for step recognition algorithm.
 */
class Filters(
    private val configuration: Configuration
) {
    // Stores last filtered accelerometer components
    private val filteredComponents = Array(3) { BigDecimal.ZERO }

    // Butterworth filters for each axis
    private val butterworthFilters = Array(3) { Butterworth() }

    private var initialized = false

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
     * Butterworth filter applied independently on each component.
     * @param input The input accelerometer vector.
     * @param cutoffFrequency The cutoff frequency.
     * @param samplingRate The sampling frequency (Hz).
     */
    fun butterworthFilter(
        input: Array<BigDecimal>,
        cutoffFrequency: Double,
        samplingRate: Int
    ): Array<BigDecimal> {
        if (!initialized) {
            for (i in 0..2) {
                if (samplingRate > 200) {
                    val adjustedCutoff = ((samplingRate / 2.0) - 1) - cutoffFrequency
                    butterworthFilters[i].lowPass(1, samplingRate.toDouble(), adjustedCutoff)
                } else {
                    butterworthFilters[i].lowPass(1, 200.0, 50.0)
                }
            }
            initialized = true
        }

        val output = Array(3) { BigDecimal.ZERO }
        for (i in 0..2) {
            val value = input[i].toDouble()
            val filtered = butterworthFilters[i].filter(value)
            output[i] = BigDecimal.valueOf(filtered)
            filteredComponents[i] = output[i]
        }

        return output
    }
}

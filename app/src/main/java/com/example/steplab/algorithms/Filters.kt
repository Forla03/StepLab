package com.example.steplab.algorithms

import android.hardware.SensorManager
import java.math.BigDecimal
import java.math.MathContext

/**
 * Provides filtering utilities for step recognition algorithm.
 */
class Filters(
    private val configuration: Configuration
) {
    // stores last filtered accelerometer components
    private val filteredComponents = Array(3) { BigDecimal.ZERO }

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
}

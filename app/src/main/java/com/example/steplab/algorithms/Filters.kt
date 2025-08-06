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

    // Butterworth filters for each axis - maintain state across calls
    private val butterworthFilters = Array(3) { Butterworth() }
    private var butterworthInitialized = false
    private var lastCutoff: Double = -1.0
    private var lastSamplingRate: Int = -1

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
     * Maintains filter state across calls for proper IIR filtering.
     */
    fun butterworthFilter(
        vettore3Componenti: Array<BigDecimal>,
        taglio: Double,
        frequenzaCampionamentoValue: Int
    ): Array<BigDecimal> {

        val output = Array(3) { BigDecimal.ZERO }

        // Initialize or reinitialize the filters if the sampling frequency has changed
        if (!butterworthInitialized || taglio != lastCutoff || frequenzaCampionamentoValue != lastSamplingRate) {
            initializeButterworthFilters(taglio, frequenzaCampionamentoValue)
            butterworthInitialized = true
            lastCutoff = taglio
            lastSamplingRate = frequenzaCampionamentoValue
        }

        for (i in 0..2) {
            val valoreDouble = vettore3Componenti[i].toDouble()
            val filteredValue = butterworthFilters[i].filter(valoreDouble)
            output[i] = BigDecimal.valueOf(filteredValue)
            filteredComponents[i] = output[i]
        }

        return output
    }

    /**
     * Initialize Butterworth filters with proper parameter validation.
     */
    private fun initializeButterworthFilters(taglio: Double, frequenzaCampionamentoValue: Int) {
        // Validate parameters
        val sampleRate = frequenzaCampionamentoValue.toDouble()
        val nyquistFrequency = sampleRate / 2.0

        // Ensure cutoff frequency is within valid range (0 < cutoff < Nyquist frequency)
        val cutoffFrequency = when {
            taglio <= 0 -> 1.0 // Minimum valid cutoff
            taglio >= nyquistFrequency -> nyquistFrequency * 0.9 // Maximum 90% of Nyquist
            else -> taglio
        }

        // Initialize each filter with the same parameters
        for (i in 0..2) {
            butterworthFilters[i].lowPass(
                1, // Filter order
                sampleRate,
                cutoffFrequency
            )
        }
    }

    /**
     * Reset Butterworth filters state. Call this when starting a new filtering session.
     */
    fun resetButterworthFilters() {
        butterworthInitialized = false
        // Reset the filter state for all axes
        for (i in 0..2) {
            butterworthFilters[i] = Butterworth()
        }
    }

}

package com.example.steplab.algorithms

import java.math.BigDecimal

/**
 * Holds raw, filtered, and transformed sensor values for step detection.
 */
class SensorData {
    /** Raw 3-component sensor readings */
    var rawValues: Array<BigDecimal> = Array(3) { BigDecimal.ZERO }
        set(values) {
            field = values
            instantiated = true
        }

    /** Filtered sensor readings */
    var filteredValues: Array<BigDecimal> = Array(3) { BigDecimal.ZERO }

    /** Sensor readings transformed to world coordinate system */
    var worldValues: Array<BigDecimal> = Array(3) { BigDecimal.ZERO }

    /** Resultant magnitude of raw values */
    var resultant: BigDecimal = BigDecimal.ZERO

    /** Resultant magnitude of filtered values */
    var filteredResultant: BigDecimal = BigDecimal.ZERO

    /** Linear acceleration (resultant minus gravity) */
    var linearResultant: BigDecimal = BigDecimal.ZERO

    /** Flag indicating whether rawValues has been set at least once */
    var instantiated: Boolean = false
}

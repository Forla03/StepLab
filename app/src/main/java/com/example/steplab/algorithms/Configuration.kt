package com.example.steplab.algorithms

import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext

/**
 * Holds configuration and state for the step recognition algorithm.
 */
data class Configuration(
    /** 3x3 rotation matrix */
    var rotationMatrix: Array<Array<BigDecimal>> = Array(3) { Array(3) { BigDecimal.ZERO } },
    /** Last local maximum value from accelerometer */
    var lastLocalMaxAccel: BigDecimal = BigDecimal.ZERO,
    /** Last local minimum value from accelerometer */
    var lastLocalMinAccel: BigDecimal = BigDecimal.ZERO,
    /** Timestamp (ms) of second half of last step */
    var lastStepSecondPhaseTime: Long = 0L,
    /** Timestamp (ms) of first half of last step */
    var lastStepFirstPhaseTime: Long = 0L,
    /** Timestamp (ms) of last x-axis intersection */
    var lastXAxisIntersectionTime: Long = 0L,
    /** Sampling frequency index (0=20Hz,1=40Hz,2=50Hz,3=100Hz,4=MAX) */
    var samplingFrequencyIndex: Int = -1,
    /** Real-time mode flag (0=REAL_TIME,1=NON_REAL_TIME) */
    var realTimeMode: Int = -1,
    /** Recognition algorithm (0=PEAK_ONLY,1=PEAK_AND_CROSSING) */
    var recognitionAlgorithm: Int = -1,
    /** Filter type (0=BAGILEVI,1=LOW_PASS,2=NONE,3=ROTATION_MATRIX) */
    var filterType: Int = -1,
    /** Cutoff frequency index (0=2Hz,1=3Hz,2=10Hz,3=alpha=0.1) */
    var cutoffFrequencyIndex: Int = -1,
    /** Gravity acceleration constant */
    var gravity: BigDecimal = BigDecimal("9.80665"),
    /** Timestamp (ms) of last detected step */
    var lastDetectedStepTime: Long = 0L,
    /** Threshold for continuous Bagilevi detection */
    var continuousBagileviDetection: Boolean = false,
    /** Threshold value for step detection */
    var detectionThreshold: BigDecimal = BigDecimal.ZERO
) : Serializable, Cloneable {

    /**
     * Absolute difference between last local max and min accelerometer values.
     */
    val localExtremaDifference: BigDecimal
        get() = lastLocalMaxAccel.subtract(lastLocalMinAccel, MathContext.DECIMAL32).abs()

    public override fun clone(): Any = super.clone()
}
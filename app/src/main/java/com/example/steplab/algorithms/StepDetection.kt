package com.example.steplab.algorithms

import java.math.BigDecimal
import java.math.MathContext

/**
 * Implements real-time step detection using various strategies.
 */
class StepDetection(
    private val configuration: Configuration
) {
    private var previousDifference: BigDecimal = BigDecimal.ZERO
    private var previousMatch: Int = -1
    private var thresholdSum: BigDecimal = configuration.detectionThreshold
    private var eventCount: Int = 1

    /**
     * Peak-and-difference detection: adaptive threshold based on local extrema difference.
     * Returns true if a step is detected.
     */
    fun detectStepByPeakDifference(): Boolean {
        val localMax = configuration.lastLocalMaxAccel
        if (localMax > BigDecimal("10.5")) {
            val dynamicThreshold = configuration.detectionThreshold
                .multiply(BigDecimal("3"), MathContext.DECIMAL32)
                .divide(BigDecimal("5"), MathContext.DECIMAL32)

            val extremaDiff = configuration.localExtremaDifference
            if (extremaDiff > dynamicThreshold) {
                thresholdSum = thresholdSum.add(extremaDiff)
                eventCount++
                configuration.detectionThreshold = thresholdSum
                    .divide(BigDecimal.valueOf(eventCount.toLong()), MathContext.DECIMAL32)
                configuration.lastDetectedStepTime = configuration.lastStepSecondPhaseTime
                return true
            } else {
                thresholdSum = thresholdSum.add(configuration.detectionThreshold)
                eventCount++
                configuration.detectionThreshold = thresholdSum
                    .divide(BigDecimal.valueOf(eventCount.toLong()), MathContext.DECIMAL32)
            }
        }
        return false
    }

    /**
     * Crossing-time detection: detects if step interval spans the last x-axis intersection.
     */
    fun detectStepByCrossing(): Boolean {
        val firstTime = configuration.lastStepFirstPhaseTime
        val secondTime = configuration.lastStepSecondPhaseTime
        val intersectionTime = configuration.lastXAxisIntersectionTime

        return if (firstTime < secondTime) {
            firstTime < intersectionTime && secondTime > intersectionTime
        } else {
            // fix for Bagilevi filter mode
            secondTime < intersectionTime && firstTime > intersectionTime
        }
    }

    private var stepDetected: Boolean = false
    private var currentDifference: BigDecimal = BigDecimal.ZERO

    /**
     * Bagilevi-based detection: matches peaks roughly consistent with previous differences.
     * @param match indicator of current peak (e.g., 0 or 1)
     */
    fun detectStepByBagilevi(match: Int): Boolean {
        stepDetected = false
        if (configuration.continuousBagileviDetection) {
            currentDifference = configuration.localExtremaDifference

            if (currentDifference > BigDecimal("10")) {
                val condition1 = currentDifference > previousDifference
                    .multiply(BigDecimal("2").divide(BigDecimal("3"), MathContext.DECIMAL32), MathContext.DECIMAL32)
                val condition2 = previousDifference > currentDifference
                    .divide(BigDecimal("3"), MathContext.DECIMAL32)
                val condition3 = previousMatch != (1 - match)

                if (condition1 && condition2 && condition3) {
                    previousMatch = match
                    stepDetected = true
                } else {
                    previousMatch = -1
                }
            }
            previousDifference = currentDifference
        }
        return stepDetected
    }
}

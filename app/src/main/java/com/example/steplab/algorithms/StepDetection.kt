package com.example.steplab.algorithms

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow
import kotlin.math.sqrt

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

    fun movingStd(signal: List<Double>, windowSize: Int = 2): List<Double> {
        val result = mutableListOf<Double>()
        for (i in 0..signal.size - windowSize) {
            val window = signal.subList(i, i + windowSize)
            val mean = window.average()
            val std = sqrt(window.sumOf { (it - mean) * (it - mean) } / window.size)
            result.add(std)
        }
        return result
    }

    fun dynamicStdThreshold(movingStd: List<Double>): Double {
        val mean = movingStd.average()
        return sqrt(movingStd.sumOf { (it - mean) * (it - mean) } / movingStd.size)
    }

    fun extractMovementSegments(
        signal: List<Double>,
        movingStd: List<Double>,
        threshold: Double,
        windowSize: Int = 2
    ): List<List<Double>> {
        val segments = mutableListOf<MutableList<Double>>()
        var currentSegment: MutableList<Double>? = null

        for (i in movingStd.indices) {
            if (movingStd[i] > threshold) {
                if (currentSegment == null) currentSegment = mutableListOf()
                currentSegment.add(signal[i])
            } else if (currentSegment != null) {
                segments.add(currentSegment)
                currentSegment = null
            }
        }

        if (currentSegment != null && currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
        }

        return segments
    }

    fun autocorrelation(signal: List<Double>): List<Double> {
        val n = signal.size
        val mean = signal.average()
        val variance = signal.sumOf { (it - mean).pow(2) } / n

        return List(n) { lag ->
            var sum = 0.0
            for (i in 0 until n - lag) {
                sum += (signal[i] - mean) * (signal[i + lag] - mean)
            }
            sum / ((n - lag) * variance)
        }
    }

    fun findFirstPeak(acf: List<Double>, threshold: Double = 0.7): Pair<Int, Double>? {
        for (i in 1 until acf.size - 1) {
            if (acf[i] > acf[i - 1] && acf[i] > acf[i + 1] && acf[i] >= threshold) {
                return i to acf[i]
            }
        }
        return null
    }

    fun detectStepsUsingACF(segments: List<List<Double>>, samplingRate: Int): Int {
        var totalSteps = 0

        for (segment in segments) {
            if (segment.size < 30) continue // too short

            val acf = autocorrelation(segment)
            val peak = findFirstPeak(acf, threshold = 0.6)

            if (peak != null) {
                val lag = peak.first
                totalSteps += segment.size / lag
            }
        }

        return totalSteps
    }

}

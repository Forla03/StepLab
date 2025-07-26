package com.example.steplab.algorithms

import java.math.BigDecimal
import kotlin.math.ceil
import kotlin.math.floor

class KeyValueRecognition(private val configuration: Configuration) {

    private val threshold = BigDecimal.valueOf(1000)

    private var previousAccelerometerMagnitude: BigDecimal = BigDecimal.ZERO
    private var direction: Int? = null
    private var previousDirection: Int = 0

    private var peakDetected = false
    private var valleyDetected = false
    private var previousValue: BigDecimal = BigDecimal.ZERO
    private var peakValue: BigDecimal = BigDecimal.ZERO
    private var previousTimestamp: Long = 0

    private var firstValue = true
    private var wasPreviouslyPositive: Boolean? = null

    /**
     * Detect local minima/maxima using Bagilevi method in real-time.
     */
    fun recognizeLocalExtremaRealtimeBagilevi(
        magnitude: BigDecimal,
        timestamp: Long?
    ): Int {
        direction = magnitude.compareTo(previousAccelerometerMagnitude)

        if (direction == -previousDirection) { // Direction change
            configuration.continuousBagileviDetection = true
            if (direction!! > 0) {
                configuration.lastLocalMinAccel = magnitude
                configuration.lastStepFirstPhaseTime = timestamp ?: 0
            } else {
                configuration.lastLocalMaxAccel = magnitude
                configuration.lastStepSecondPhaseTime = timestamp ?: 0
            }
        } else {
            configuration.continuousBagileviDetection = false
        }

        previousDirection = direction!!
        previousAccelerometerMagnitude = magnitude

        return if (direction!! > 0) 0 else 1
    }

    /**
     * Detect local peaks and valleys for step recognition.
     */
    fun recognizeLocalExtremaRealtime(
        magnitude: BigDecimal,
        timestamp: Long
    ): Boolean {
        if (magnitude <= previousValue) {
            valleyDetected = false
            if (!peakDetected || previousValue > peakValue) {
                peakValue = previousValue
                configuration.lastLocalMaxAccel = previousValue
                configuration.lastStepFirstPhaseTime = previousTimestamp
                peakDetected = true
            }
        } else if (peakDetected && magnitude >= previousValue) {
            if (!valleyDetected) {
                peakValue = BigDecimal.ZERO
                configuration.lastLocalMinAccel = previousValue
                configuration.lastStepSecondPhaseTime = previousTimestamp
                valleyDetected = true
            }
        }

        previousValue = magnitude
        previousTimestamp = timestamp

        return if (valleyDetected) {
            peakDetected = false
            valleyDetected = false
            true
        } else {
            false
        }
    }

    /**
     * Detect zero-crossing of the accelerometer magnitude in real-time.
     */
    fun recognizeXAxisIntersectionRealtime(
        magnitude: BigDecimal,
        timestamp: Long?
    ) {
        if (firstValue) {
            wasPreviouslyPositive = magnitude >= BigDecimal.ZERO
            firstValue = false
        } else {
            if (wasPreviouslyPositive == true && magnitude < BigDecimal.ZERO) {
                wasPreviouslyPositive = false
                configuration.lastXAxisIntersectionTime = timestamp ?: 0
            } else if (wasPreviouslyPositive == false && magnitude >= BigDecimal.ZERO) {
                wasPreviouslyPositive = true
                configuration.lastXAxisIntersectionTime = timestamp ?: 0
            }
        }
    }

    /**
     * Detect fundamental frequency for autocorrelation matrix.
     */
    fun findFundamentalFrequency(
        magnitude: Array<BigDecimal>,
        samplingRate: Int): Double {

        val n = magnitude.size*2
        val startIndex = ceil(1.0 * n / samplingRate).toInt()
        val endIndex = floor(3.0 * n / samplingRate).toInt()

        var maxIndex = startIndex
        var maxValue = magnitude[startIndex]

        for(i in startIndex..endIndex){
            if (magnitude[i] > maxValue) {
                maxIndex = i
                maxValue = magnitude[i]
            }
        }

        return maxIndex.toDouble() * samplingRate / n
    }
}

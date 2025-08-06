package com.example.steplab.algorithms

import java.math.BigDecimal

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
    private var peakDetectedValue: BigDecimal = BigDecimal.ZERO

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
     * Time filtering step recognition: identifies significant peaks and valleys using temporal thresholds.
     */
    fun recognizeLocalExtremaTimeFiltering(
        magnitude: BigDecimal,
        timestamp: Long
    ): Boolean {

        if (magnitude <= previousValue) {
            valleyDetected = false

            if (!peakDetected || previousValue > peakDetectedValue) {
                configuration.exExMax?.let { exEx ->
                    val exMax = configuration.exMax ?: 0L
                    val thS = 0.5 * (configuration.exMax?.toDouble() ?: 0.0 - exEx.toDouble())
                    val delta = configuration.lastStepFirstPhaseTime - exMax

                    if (delta > thS) {
                        val thE = 0.3 * delta
                        val deltaPeak = previousTimestamp - configuration.lastStepFirstPhaseTime

                        if (deltaPeak >= thE) {
                            configuration.previousLocalMax = configuration.lastLocalMaxAccel
                            configuration.exExMax = configuration.exMax
                            configuration.exMax = configuration.lastStepFirstPhaseTime
                            configuration.lastLocalMaxAccel = previousValue
                            configuration.lastStepFirstPhaseTime = previousTimestamp
                            peakDetected = true

                            previousValue = magnitude
                            previousTimestamp = timestamp
                            return false
                        }
                    }
                }

                peakDetectedValue = previousValue
                configuration.exExMax = configuration.exMax
                configuration.exMax = configuration.lastStepFirstPhaseTime
                configuration.lastLocalMaxAccel = previousValue
                configuration.lastStepFirstPhaseTime = previousTimestamp
                peakDetected = true
            }

        } else if (peakDetected && magnitude >= previousValue) {

            if (!valleyDetected) {
                peakDetectedValue = BigDecimal.ZERO

                configuration.exExMin?.let { exEx ->
                    val exMin = configuration.exMin ?: 0L
                    val thS = 0.5 * (configuration.exMin?.toDouble() ?: 0.0 - exEx.toDouble())
                    val delta = configuration.lastStepSecondPhaseTime - exMin

                    if (delta > thS) {
                        val thE = 0.3 * delta
                        val deltaValley = previousTimestamp - configuration.lastStepSecondPhaseTime

                        if (deltaValley >= thE) {
                            configuration.previousLocalMin = configuration.lastLocalMinAccel
                            configuration.exExMin = configuration.exMin
                            configuration.exMin = configuration.lastStepSecondPhaseTime
                            configuration.lastLocalMinAccel = previousValue
                            configuration.lastStepSecondPhaseTime = previousTimestamp

                            previousValue = magnitude
                            previousTimestamp = timestamp
                            return false
                        }
                    }
                }

                configuration.exExMin = configuration.exMin
                configuration.exMin = configuration.lastStepSecondPhaseTime
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

}

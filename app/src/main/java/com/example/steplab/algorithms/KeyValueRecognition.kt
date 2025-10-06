package com.example.steplab.algorithms

import java.math.BigDecimal

class KeyValueRecognition(private val configuration: Configuration) {

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
     * 
     * This implementation uses adaptive temporal thresholds to filter out false peaks and valleys:
     * - thS (Start threshold): 35% of the interval between the two previous peaks/valleys
     * - thE (End threshold): 20% of the current interval being evaluated
     * 
     * The algorithm ensures that:
     * 1. The current peak/valley interval is consistent with previous intervals (thS check)
     * 2. New candidates are sufficiently separated in time from the current extremum (thE check)
     */
    fun recognizeLocalExtremaTimeFiltering(
        magnitude: BigDecimal,
        timestamp: Long
    ): Boolean {

        // PEAK DETECTION: when signal stops increasing (magnitude <= previousValue)
        if (magnitude <= previousValue) {
            valleyDetected = false

            // Check if we found a new peak (not yet detected, or higher than previous peak candidate)
            if (!peakDetected || previousValue > peakDetectedValue) {
                
                // PEAK TUNING: Check if we should update the current peak or keep it
                // Only applies if we have history of at least 2 previous peaks
                val exExMax = configuration.exExMax
                val exMax = configuration.exMax
                
                if (exExMax != null && exMax != null && exExMax > 0L && exMax > 0L) {
                    // thS: 35% of the time interval between the two previous peaks
                    val thS = 0.35 * kotlin.math.abs((exMax - exExMax).toDouble())
                    
                    // delta: time elapsed from the previous peak to the current peak
                    val delta = configuration.lastStepFirstPhaseTime - exMax
                    
                    // Only proceed with tuning if the current interval is significant enough
                    if (delta > thS) {
                        // thE: 20% of the current peak interval
                        val thE = 0.20 * delta
                        
                        // deltaPeak: time from current peak to new peak candidate
                        val deltaPeak = previousTimestamp - configuration.lastStepFirstPhaseTime
                        
                        // If the new candidate is separated enough, update to the new peak
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

                // Standard peak detection (no tuning or first peaks)
                peakDetectedValue = previousValue
                configuration.exExMax = configuration.exMax
                configuration.exMax = configuration.lastStepFirstPhaseTime
                configuration.lastLocalMaxAccel = previousValue
                configuration.lastStepFirstPhaseTime = previousTimestamp
                peakDetected = true
            }

        // VALLEY DETECTION: when signal stops decreasing (magnitude >= previousValue) after a peak
        } else if (peakDetected && magnitude >= previousValue) {

            // Check if we found a new valley
            if (!valleyDetected) {
                peakDetectedValue = BigDecimal.ZERO

                // VALLEY TUNING: Check if we should update the current valley or keep it
                // Only applies if we have history of at least 2 previous valleys
                val exExMin = configuration.exExMin
                val exMin = configuration.exMin
                
                if (exExMin != null && exMin != null && exExMin > 0L && exMin > 0L) {
                    // thS: 35% of the time interval between the two previous valleys
                    val thS = 0.35 * kotlin.math.abs((exMin - exExMin).toDouble())
                    
                    // delta: time elapsed from the previous valley to the current valley
                    val delta = configuration.lastStepSecondPhaseTime - exMin
                    
                    // Only proceed with tuning if the current interval is significant enough
                    if (delta > thS) {
                        // thE: 20% of the current valley interval
                        val thE = 0.20 * delta
                        
                        // deltaValley: time from current valley to new valley candidate
                        val deltaValley = previousTimestamp - configuration.lastStepSecondPhaseTime
                        
                        // If the new candidate is separated enough, update to the new valley
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

                // Standard valley detection (no tuning or first valleys)
                configuration.exExMin = configuration.exMin
                configuration.exMin = configuration.lastStepSecondPhaseTime
                configuration.lastLocalMinAccel = previousValue
                configuration.lastStepSecondPhaseTime = previousTimestamp
                valleyDetected = true
            }
        }

        // Update tracking variables for next iteration
        previousValue = magnitude
        previousTimestamp = timestamp

        // Return true only when a complete peak-valley cycle is detected
        return if (valleyDetected) {
            peakDetected = false
            valleyDetected = false
            true
        } else {
            false
        }
    }

}

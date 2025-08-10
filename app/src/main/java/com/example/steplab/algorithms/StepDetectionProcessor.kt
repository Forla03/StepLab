package com.example.steplab.algorithms

import com.github.mikephil.charting.data.Entry
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.util.*
import kotlin.math.pow

/**
 * Helper class that encapsulates step detection and sensor processing l              // Chart points: place a marker at each estimated step
            // at the height of the filtered value (y[pos])           // at the height of the filtered value (y[pos])         // Chart points: place a marker at each estimated step
            // at the height of the filtered value (y[pos])           // at the height of the filtered value (y[pos])         // Chart points: place a marker at each estimated step
            // at the level of the filtered value (y[pos])ic.
 * This clas             // Points for chart: place a marker at each estimated step
            // at the level of the filtered value (y[pos])         // Chart points: put a marker at each estimated step
            // at the height of the filtered value (filteredSignal[position])can be used by both real-time processing (PedometerRunningFragment) 
 * and batch processing (ConfigurationsComparison).
 */
class StepDetectionProcessor(
    private val configuration: Configuration
) {
    // Core algorithm components
    private val accelerometer = SensorData()
    private val accelerometerXAxis = SensorData()
    private val magnetometer = SensorData()
    private val rotation = SensorData()
    private val filters = Filters(configuration)
    private val calculations = Calculations(configuration)
    private val keyPointDetection = KeyValueRecognition(configuration)
    private val stepDetection = StepDetection(configuration)

    // State variables
    var stepDetected = false
        private set
    var accelerometerEvent = false
        // Made public for PedometerRunningFragment chart updates
    private var firstSignal = true
    var lastAccelerationMagnitude: BigDecimal? = null
        private set

    // Sampling and filtering variables
    var cutoffFrequencyValue: Int? = null
        private set
    var samplingRateValue: Int = 0
        private set
    private var signalCount: Int = 0
    private var firstSecond: Int? = null
    private var currentSecond: Int? = null
    var alpha: BigDecimal? = null
        private set
    private var date: Calendar? = null
    
    // Counters
    var stepsCount = 0
        private set
    var counter = 0
        private set
    
    // Chart entries for batch processing
    val chartEntries = mutableListOf<Entry>()
    
    // False step detection variables
    private val vectorMagn = FloatArray(3)
    private var resultMagn = 0f
    private var resultMagnPrev = 0f
    private val sumResMagn = mutableListOf<Float>()
    private var countFour = 0
    private val resLast4Steps = mutableListOf<Float>()
    private var falseStep = false
    
    // For time filtering algorithm
    private val last3Acc = mutableListOf<BigDecimal>()
    private var s = 0
    private var sOld = 0
    var cutoffSelected = 3.0 // Default cutoff frequency for Butterworth filter
        private set
    private var oldMagn = 0.0
    
    // For autocorrelation algorithm
    private val recordList = mutableListOf<Sample>()
    private var startingPoint = 0L

    // Reuse BigDecimal arrays to reduce object creation (for real-time processing)
    private val reusableValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableMagnetometerValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableGravityValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)

    init {
        initializeConfiguration()
    }

    private fun initializeConfiguration() {
        configuration.lastDetectedStepTime = 0L
        configuration.lastStepSecondPhaseTime = 0L
        val cutoffFrequencyIndex = configuration.cutoffFrequencyIndex
        
        if (cutoffFrequencyIndex == 3) {
            alpha = BigDecimal("0.1")
        } else {
            cutoffFrequencyValue = when (cutoffFrequencyIndex) {
                0 -> 2
                1 -> 3
                2 -> 10
                else -> 3
            }
            samplingRateValue = 0
            signalCount = 0
            firstSignal = true
        }
    }

    /**
     * Process sensor data from real-time sensor events.
     * Used by PedometerRunningFragment.
     */
    fun processRealTimeSensorData(
        sensorType: Int,
        values: FloatArray,
        timestamp: Long
    ): ProcessingResult {
        stepDetected = false
        
        when (sensorType) {
            android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                accelerometerEvent = true
                // Reuse array to reduce object creation
                reusableValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableValues[2] = BigDecimal.valueOf(values[2].toDouble())
                
                accelerometer.rawValues = reusableValues
                accelerometerXAxis.rawValues = reusableValues
                lastAccelerationMagnitude = calculations.resultant(reusableValues)
                
                // Update sampling rate dynamically
                updateSamplingRate(timestamp)
            }
            
            android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> {
                accelerometerEvent = false
                reusableMagnetometerValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableMagnetometerValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableMagnetometerValues[2] = BigDecimal.valueOf(values[2].toDouble())
                magnetometer.rawValues = reusableMagnetometerValues
            }
            
            android.hardware.Sensor.TYPE_GRAVITY -> {
                accelerometerEvent = false
                reusableGravityValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableGravityValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableGravityValues[2] = BigDecimal.valueOf(values[2].toDouble())
                configuration.gravity = calculations.resultant(reusableGravityValues)
            }
        }

        // Process filters and step detection for accelerometer events
        // Works for both real-time (0) and non-real-time (1) modes
        if (accelerometerEvent) {
            processFiltersAndDetection(timestamp)
        }

        return ProcessingResult(
            stepDetected = stepDetected,
            filteredValue = getFilteredValueForChart(),
            chartLabel = getChartLabel()
        )
    }

    /**
     * Process sensor data from JSON events.
     * Used by ConfigurationsComparison.
     */
    fun processBatchSensorData(instant: Long, eventJson: JSONObject): Boolean {
        stepDetected = false

        try {
            if (eventJson.has("acceleration_x")) {
                counter++
                lastAccelerationMagnitude = BigDecimal(eventJson.getString("acceleration_magnitude"))
                accelerometerEvent = true

                val vector = arrayOf(
                    BigDecimal(eventJson.getString("acceleration_x")),
                    BigDecimal(eventJson.getString("acceleration_y")),
                    BigDecimal(eventJson.getString("acceleration_z"))
                )
                accelerometer.rawValues = vector
                accelerometerXAxis.rawValues = vector

            } else if (eventJson.has("magnetometer_x")) {
                accelerometerEvent = false
                magnetometer.rawValues = arrayOf(
                    BigDecimal(eventJson.getString("magnetometer_x")),
                    BigDecimal(eventJson.getString("magnetometer_y")),
                    BigDecimal(eventJson.getString("magnetometer_z"))
                )

            } else if (eventJson.has("gravity_x")) {
                accelerometerEvent = false
                configuration.gravity = calculations.resultant(arrayOf(
                    BigDecimal(eventJson.getString("gravity_x")),
                    BigDecimal(eventJson.getString("gravity_y")),
                    BigDecimal(eventJson.getString("gravity_z"))
                ))
            } else if (eventJson.has("rotation_x")) {
                accelerometerEvent = false
                rotation.rawValues = arrayOf(
                    BigDecimal(eventJson.getString("rotation_x")),
                    BigDecimal(eventJson.getString("rotation_y")),
                    BigDecimal(eventJson.getString("rotation_z"))
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Apply filters and step detection
        processFiltersAndDetection(instant)

        if (configuration.recognitionAlgorithm == 1 && configuration.filterType != 4) {
            applyIntersectionCorrection(instant)
        }

        if (stepDetected) {
            handleStepDetection()
        } else {
            // Add magnetometer values for false step detection
            if (configuration.falseStepDetectionEnabled) {
                sumResMagn.add(resultMagn)
            }
        }
        
        return stepDetected
    }

    /**
     * Process autocorrelation algorithm for batch processing.
     */
    fun processAutocorrelationAlgorithm(jsonObject: JSONObject) {
        // 1) Extract and sort timestamps (String -> Long)
        val keysSorted = jsonObject.keys().asSequence().toList().sortedBy { it.toLong() }
        recordList.clear()
        chartEntries.clear()

        // 2) Build samples in temporal order
        for (key in keysSorted) {
            val eventJson = jsonObject.getJSONObject(key)
            if (eventJson.has("acceleration_x")) {
                val x = eventJson.getString("acceleration_x").toDouble()
                val y = eventJson.getString("acceleration_y").toDouble()
                val z = eventJson.getString("acceleration_z").toDouble()
                val magnitude = kotlin.math.sqrt(x*x + y*y + z*z)
                val timestampMillis = key.toLong()
                recordList.add(Sample(magnitude, timestampMillis / 1000.0)) // timestamp in seconds (double)
            }
        }

        if (recordList.size < 4) {
            stepsCount = 0
            return
        }

        // 3) Estimate sampling frequency from timestamps (in seconds)
        val firstSecond = recordList.first().timestamp
        val lastSecond  = recordList.last().timestamp
        val totalSeconds = (lastSecond - firstSecond).coerceAtLeast(1e-6) // avoid division by 0
        val estimatedFs = (((recordList.size - 1) / totalSeconds).toInt()).coerceAtLeast(10) // min 10 Hz

        // 4) Build vectors [0,0,magnitude] (the pipeline uses magnitude)
        val samples = recordList.map { sample ->
            arrayOf(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(sample.magnitude)
            )
        }

        try {
            // 5) Count steps with autocorrelation pipeline
            val result = stepDetection.countStepsAutocorrelation(
                samples = samples,
                fs = estimatedFs,
                filters = filters,
                calculations = calculations,
                useHannWindowForFFT = true,
                dropHeadSecondsForMSD = 0.3,
                bpOrder = 6
            )
            stepsCount = result.steps

            // 6) (Optional but useful) Draw filtered series + step markers
            //    Filter magnitude with the band used by the algorithm
            val magnitudeWithoutDC = calculations.computeMagnitudeWithoutDC(samples)
            val filteredSignal = filters.filterMagnitudeBandPassSeries(
                magNoDc = magnitudeWithoutDC,
                fs = estimatedFs,
                low = result.bandLowHz,
                high = result.bandHighHz,
                order = 6
            )

            // Chart points: place a marker at each estimated step
            // all’altezza del valore filtrato (y[pos])
            for (segmentIndex in result.segments.indices) {
                val (start, end) = result.segments[segmentIndex]
                val lag = result.lagsPerSegment[segmentIndex]
                if (lag <= 0) continue
                var position = start + lag
                while (position <= end) {
                    if (position in filteredSignal.indices) chartEntries.add(Entry(position.toFloat(), filteredSignal[position].toFloat()))
                    position += lag
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Gentle fallback
            stepsCount = (recordList.size / 25).coerceAtLeast(0)
            chartEntries.clear()
        }
    }

    private fun updateSamplingRate(timestamp: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val second = calendar.get(Calendar.SECOND)
        
        if (firstSecond == null) {
            firstSecond = second
            currentSecond = second
        }
        
        if (second == firstSecond) {
            signalCount++
        } else if (second == currentSecond) {
            signalCount++
        } else {
            currentSecond = second
            samplingRateValue = signalCount
            signalCount = 0
            
            // Recalculate alpha for low-pass filter
            if (configuration.filterType == 1 && configuration.cutoffFrequencyIndex != 3) {
                alpha = calculateAlpha(samplingRateValue, cutoffFrequencyValue!!)
            }
        }
    }

    private fun processFiltersAndDetection(instant: Long) {
        when (configuration.filterType) {
            0 -> applyBagileviFilter(instant)
            1 -> applyLowPassFilter(instant)
            2 -> applyNoFilter(instant)
            3 -> applyRotationMatrixFilter(instant)
            4 -> applyButterworthFilter(instant)
        }
    }

    private fun applyBagileviFilter(instant: Long) {
        if (accelerometerEvent) {
            val filtered = filters.bagileviFilter(accelerometer.rawValues)
            accelerometer.filteredResultant = filtered
            val peak = keyPointDetection.recognizeLocalExtremaRealtimeBagilevi(filtered, instant)
            stepDetected = stepDetection.detectStepByBagilevi(peak)
        }
    }

    private fun applyLowPassFilter(instant: Long) {
        if (accelerometerEvent) {
            // Update sampling frequency
            if (configuration.cutoffFrequencyIndex != 3) {
                date = Calendar.getInstance().apply { timeInMillis = instant }
                val second = date!!.get(Calendar.SECOND)

                if (firstSignal) {
                    firstSecond = second
                    currentSecond = second
                    firstSignal = false
                }

                if (second == firstSecond) {
                    signalCount++
                    samplingRateValue++
                } else if (second == currentSecond) {
                    signalCount++
                } else {
                    currentSecond = second
                    samplingRateValue = signalCount
                    signalCount = 0
                }

                alpha = calculateAlpha(samplingRateValue, cutoffFrequencyValue!!)
            }

            // Calculate magnetometer magnitude for false step detection
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }

            val filtered = filters.lowPassFilter(accelerometer.rawValues, alpha!!)
            accelerometer.filteredValues = filtered
            accelerometer.filteredResultant = calculations.resultant(filtered)

            val detectionResult = when (configuration.recognitionAlgorithm) {
                1 -> keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)
                2 -> keyPointDetection.recognizeLocalExtremaTimeFiltering(accelerometer.filteredResultant, instant)
                else -> keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)
            }

            if (detectionResult) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }
    }

    private fun applyNoFilter(instant: Long) {
        if (accelerometerEvent) {
            val magnitude = calculations.resultant(accelerometer.rawValues)
            accelerometer.resultant = magnitude

            if (keyPointDetection.recognizeLocalExtremaRealtime(magnitude, instant)) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }
    }

    private fun applyRotationMatrixFilter(instant: Long) {
        if (accelerometer.instantiated && magnetometer.instantiated) {
            calculations.updateRotationMatrix(accelerometer.rawValues, magnetometer.rawValues)
            val fixedSystem = calculations.worldAcceleration(accelerometer.rawValues)
            accelerometer.worldValues = fixedSystem

            val zAxis = fixedSystem[2]
            if (keyPointDetection.recognizeLocalExtremaRealtime(zAxis, instant)) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }
    }

    private fun applyButterworthFilter(instant: Long) {
        if (accelerometerEvent) {
            // Update sampling frequency
            date = Calendar.getInstance().apply { timeInMillis = instant }
            val second = date!!.get(Calendar.SECOND)

            if (firstSignal) {
                firstSecond = second
                currentSecond = second
                firstSignal = false
            }

            if (second == firstSecond) {
                signalCount++
                samplingRateValue++
            } else if (second == currentSecond) {
                signalCount++
            } else {
                currentSecond = second
                samplingRateValue = signalCount
                signalCount = 0
            }
            
            // Calculate magnetometer magnitude for false step detection
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }

            val filtered = filters.butterworthFilter(accelerometer.rawValues, cutoffSelected, samplingRateValue)
            accelerometer.filteredValues = filtered
            accelerometer.filteredResultant = calculations.resultant(filtered)

            val detectionResult = when (configuration.recognitionAlgorithm) {
                0 -> keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)
                1 -> keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)
                2 -> keyPointDetection.recognizeLocalExtremaTimeFiltering(accelerometer.filteredResultant, instant)
                else -> false
            }

            if (detectionResult) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }
    }

    private fun applyIntersectionCorrection(instant: Long) {
        if (accelerometerEvent) {
            val magnitude = calculations.resultant(accelerometerXAxis.rawValues)
            accelerometerXAxis.resultant = magnitude
            val linear = calculations.linearAcceleration(magnitude)
            accelerometerXAxis.linearResultant = linear
            keyPointDetection.recognizeXAxisIntersectionRealtime(linear, instant)
            stepDetected = stepDetected && stepDetection.detectStepByCrossing()
        } else {
            stepDetected = false
        }
    }

    private fun calculateMagnetometerMagnitude() {
        val magn = magnetometer.rawValues
        vectorMagn[0] = magn[0].toFloat()
        vectorMagn[1] = magn[1].toFloat()
        vectorMagn[2] = magn[2].toFloat()
        resultMagn = kotlin.math.sqrt(
            kotlin.math.sqrt(vectorMagn[0] * vectorMagn[0] + vectorMagn[1] * vectorMagn[1]).pow(2) + vectorMagn[2] * vectorMagn[2]
        )
    }

    private fun handleStepDetection() {
        // Check for false steps based on time filtering (algorithm 2)
        if (configuration.recognitionAlgorithm == 2) {
            checkTimeFilteringFalseStep()
        }
        
        // Check for false steps based on Butterworth filter
        if (configuration.filterType == 4 && configuration.falseStepDetectionEnabled) {
            checkButterworthFalseStep()
        }
        
        // Check for false steps based on magnetometer data
        if (configuration.falseStepDetectionEnabled) {
            checkFalseStep()
        }
        
        if (!falseStep) {
            stepsCount++
            // Store chart entry for batch processing
            lastAccelerationMagnitude?.let { magnitude ->
                chartEntries.add(Entry(counter.toFloat(), magnitude.toFloat()))
            }
        } else {
            falseStep = false
        }
    }

    private fun checkFalseStep() {
        if (!configuration.falseStepDetectionEnabled) return
        if (sumResMagn.isEmpty()) return // avoid division by zero

        val averageRes = calculations.sumOfMagnet(sumResMagn) / sumResMagn.size
        sumResMagn.clear()

        if (resLast4Steps.size < 4) {
            resLast4Steps.add(averageRes)
            return
        }

        falseStep = calculations.checkFalseStep(resLast4Steps, averageRes)

        resLast4Steps.removeAt(0)
        resLast4Steps.add(averageRes)
    }


    private fun checkTimeFilteringFalseStep() {
        if (configuration.recognitionAlgorithm == 2) {
            val aNPeakValley = configuration.lastStepFirstPhaseTime - configuration.lastStepSecondPhaseTime
            val aN1PeakValley = configuration.exMax!! - configuration.exMin!!
            val diff = kotlin.math.abs(aNPeakValley.toDouble() - aN1PeakValley.toDouble())

            falseStep = (diff == 0.0)
        }
    }

    private fun checkButterworthFalseStep() {
        if (configuration.filterType != 4 || !configuration.falseStepDetectionEnabled) return

        val magnN = configuration.lastLocalMaxAccel.toDouble() - configuration.lastLocalMinAccel.toDouble()
        if (oldMagn != 0.0) {
            val diffMagn = kotlin.math.abs(magnN - oldMagn)
            val eps = 1e-6
            val fi2 = if (diffMagn > eps) kotlin.math.abs(kotlin.math.ceil(samplingRateValue / diffMagn * 2.0)) else Double.POSITIVE_INFINITY
            val timeDiff = kotlin.math.abs(
                (configuration.lastStepFirstPhaseTime - configuration.lastStepSecondPhaseTime).toDouble() -
                        (configuration.exMax!! - configuration.exMin!!).toDouble()
            )

            if (!falseStep && fi2 > 180.0 && timeDiff < 2.1) {
                falseStep = true
            }

            cutoffSelected = when {
                fi2 < 81.0  -> (samplingRateValue / 6.0).let { if (timeDiff > 20.0) it / 2.5 else it }
                fi2 < 183.0 -> (samplingRateValue / 7.0).let { if (timeDiff > 40.0) it / 2.857 else it } // ~/20
                else        -> 0.0
            }
        }
        oldMagn = magnN
    }

    private fun calculateAlpha(samplingRate: Int, cutoffFrequency: Int): BigDecimal {
        val samplingFreq = BigDecimal.ONE.divide(BigDecimal.valueOf(samplingRate.toLong()), MathContext.DECIMAL32)
        val cutoffFreq = BigDecimal.ONE.divide(
            BigDecimal.valueOf(2).multiply(BigDecimal.valueOf(Math.PI), MathContext.DECIMAL32)
                .multiply(BigDecimal.valueOf(cutoffFrequency.toLong()), MathContext.DECIMAL32),
            MathContext.DECIMAL32
        )
        return samplingFreq.divide(samplingFreq.add(cutoffFreq), MathContext.DECIMAL32)
    }

    private fun getFilteredValueForChart(): BigDecimal {
        return when (configuration.filterType) {
            0 -> accelerometer.filteredResultant ?: BigDecimal.ZERO
            1 -> accelerometer.filteredResultant ?: BigDecimal.ZERO
            2 -> accelerometer.resultant ?: BigDecimal.ZERO
            3 -> accelerometer.worldValues?.get(2) ?: BigDecimal.ZERO
            4 -> accelerometer.filteredResultant ?: BigDecimal.ZERO
            else -> BigDecimal.ZERO
        }
    }

    private fun getChartLabel(): String {
        return when (configuration.filterType) {
            0 -> "Acceleration - Bagilevi Filter"
            1 -> "Acceleration - Low Pass Filter"
            2 -> "Acceleration - No Filter"
            3 -> "Acceleration - Rotation Matrix Filter (Z axis)"
            4 -> "Acceleration - Butterworth Filter"
            else -> "Acceleration"
        }
    }

    /**
     * Reset the processor state for a new session.
     */
    fun reset() {
        stepsCount = 0
        counter = 0
        chartEntries.clear()
        stepDetected = false
        accelerometerEvent = false
        firstSignal = true
        lastAccelerationMagnitude = null
        signalCount = 0
        firstSecond = null
        currentSecond = null
        falseStep = false
        sumResMagn.clear()
        resLast4Steps.clear()
        countFour = 0
        oldMagn = 0.0
        recordList.clear()
        startingPoint = 0L
        initializeConfiguration()
    }

    /**
     * Data class for real-time processing results.
     */
    data class ProcessingResult(
        val stepDetected: Boolean,
        val filteredValue: BigDecimal,
        val chartLabel: String
    )

    /**
     * Data class for autocorrelation algorithm samples.
     */
    private data class Sample(
        val magnitude: Double,
        val timestamp: Double
    )
}

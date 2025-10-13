package com.example.steplab.algorithms

import com.github.mikephil.charting.data.Entry
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

class StepDetectionProcessor(
    private val configuration: Configuration
) {
    private val accelerometer = SensorData()
    private val accelerometerXAxis = SensorData()
    private val magnetometer = SensorData()
    private val rotation = SensorData()
    private val filters = Filters(configuration)
    private val calculations = Calculations(configuration)
    private val keyPointDetection = KeyValueRecognition(configuration)
    private val stepDetection = StepDetection(configuration)

    // Thread-safe atomic variables to prevent race conditions
    private val _stepDetected = AtomicBoolean(false)
    var stepDetected: Boolean
        get() = _stepDetected.get()
        private set(value) = _stepDetected.set(value)
    
    var accelerometerEvent = false
    private var firstSignal = true
    var lastAccelerationMagnitude: BigDecimal? = null
        private set

    var cutoffFrequencyValue: Int? = null
        private set
    var samplingRateValue: Int = 0
        private set
    private var alpha: BigDecimal? = null

    // Thread-safe atomic counter for step count
    private val _stepsCount = AtomicInteger(0)
    var stepsCount: Int
        get() = _stepsCount.get()
        private set(value) = _stepsCount.set(value)
    
    var counter = 0
        private set

    val chartEntries = mutableListOf<Entry>()

    private val vectorMagn = FloatArray(3)
    private var resultMagn = 0f
    private var resultMagnPrev = 0f
    private val sumResMagn = mutableListOf<Float>()
    
    // Memory management constants
    private val MAX_CHART_ENTRIES = 2000
    private val MAX_SUM_RES_MAGN = 1000
    private val resLast4Steps = mutableListOf<Float>()
    // Thread-safe atomic boolean for false step detection
    private val _falseStep = AtomicBoolean(false)
    private var falseStep: Boolean
        get() = _falseStep.get()
        set(value) = _falseStep.set(value)

    private val last3Acc = mutableListOf<BigDecimal>()
    private var s = 0
    private var sOld = 0
    var cutoffSelected = 3.0
        private set
    private var oldMagn = 0.0

    private val recordList = mutableListOf<Sample>()
    
    private var lastSensorEventNs: Long = -1L
    private var lastBatchInstant: Long = -1L  
    private val IDLE_THRESHOLD_MS = 4000L

    private val reusableValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableXAxisValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableMagnetometerValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableGravityValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableRotationValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)

    private var lastAccelTsNanos: Long = -1L
    private var emaFs: Double = 0.0
    private var collectForCharts = false
    private var forceFs: Int? = null
    
    private var currentSecond: Int = -1
    private var firstCurrentSecond: Int = -1
    private var numberOfSensorEvents: Int = 0

    init {
        initializeConfiguration()
    }

    /*
     * Reset all filter states to prevent contamination between tests
     */
    fun resetFilterStates() {
        filters.resetFilterState()
        
        // Reset step counter and other state
        _stepsCount.set(0)
        counter = 0
        chartEntries.clear()
        lastAccelTsNanos = -1L
        lastBatchInstant = -1L  
        emaFs = 0.0
        firstSignal = true
        lastAccelerationMagnitude = null
        
        // Reset sensor event tracking
        lastSensorEventNs = -1L
        
        // Reset Java-aligned frequency sampling variables
        currentSecond = -1
        firstCurrentSecond = -1
        numberOfSensorEvents = 0
        
        // Clear sensor data
        sumResMagn.clear()
        resLast4Steps.clear()
        last3Acc.clear()
        recordList.clear()
        
        oldMagn = 0.0
        s = 0
        sOld = 0
    }
    
    /**
     * Reset counter for configuration comparison mode.
     * This ensures chart entries start from 0 for each configuration comparison.
     */
    fun resetCounterForComparison() {
        counter = 0
        chartEntries.clear()
        _stepsCount.set(0)
        
        // Reset Java-aligned frequency sampling variables
        currentSecond = -1
        firstCurrentSecond = -1
        numberOfSensorEvents = 0
    }

    private fun initializeConfiguration() {
        configuration.gravity = BigDecimal("9.80665")
        configuration.lastDetectedStepTime = 0L
        configuration.lastStepSecondPhaseTime = 0L

        // Set threshold based on filter type
        when (configuration.filterType) {
            1 -> configuration.detectionThreshold = BigDecimal.valueOf(5) // Low Pass Filter
            2 -> configuration.detectionThreshold = BigDecimal.valueOf(5) // No Filter  
            3 -> configuration.detectionThreshold = BigDecimal.valueOf(8) // Rotation Matrix
            0 -> {
                // Keep current value or set to a default if needed
                if (configuration.detectionThreshold == BigDecimal.ZERO) {
                    configuration.detectionThreshold = BigDecimal.valueOf(5) // Safe default
                }
            }
            else -> {
                // Default fallback
                if (configuration.detectionThreshold == BigDecimal.ZERO) {
                    configuration.detectionThreshold = BigDecimal.valueOf(5)
                }
            }
        }
        
        val idx = configuration.cutoffFrequencyIndex
        if (configuration.filterType == 1) {
            if (idx == 3) {
                // Cutoff = 2% sampling rate
                // Calculate dynamically when fs is available
                val initialFs = if (samplingRateValue > 0) samplingRateValue else 50
                cutoffFrequencyValue = (initialFs * 0.02).toInt().coerceAtLeast(1)
                alpha = calculateAlpha(initialFs, cutoffFrequencyValue!!)
            } else {
                cutoffFrequencyValue = when (idx) { 0 -> 2; 1 -> 3; 2 -> 10; else -> 3 }
                // starting alpha, will be correct when calculate
                alpha = calculateAlpha(
                    if (samplingRateValue > 0) samplingRateValue else 50,
                    cutoffFrequencyValue ?: 3
                )
            }
        } else {
            cutoffFrequencyValue = when (idx) { 0 -> 2; 1 -> 3; 2 -> 10; else -> 3 }
        }
    }

    fun processRealTimeSensorData(
        sensorType: Int,
        values: FloatArray,
        timestamp: Long
    ): ProcessingResult {
        stepDetected = false
        val nowNs = System.nanoTime()
        
        when (sensorType) {
            android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                accelerometerEvent = true
                lastSensorEventNs = nowNs
                
                reusableValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableValues[2] = BigDecimal.valueOf(values[2].toDouble())
                
                accelerometer.rawValues = arrayOf(reusableValues[0], reusableValues[1], reusableValues[2])
                accelerometerXAxis.rawValues = arrayOf(reusableValues[0], reusableValues[1], reusableValues[2])
                lastAccelerationMagnitude = calculations.resultant(accelerometer.rawValues)
                
            }
            android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> {
                accelerometerEvent = false
                reusableMagnetometerValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableMagnetometerValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableMagnetometerValues[2] = BigDecimal.valueOf(values[2].toDouble())
                magnetometer.rawValues = arrayOf(reusableMagnetometerValues[0], reusableMagnetometerValues[1], reusableMagnetometerValues[2])
            }
            android.hardware.Sensor.TYPE_GRAVITY -> {
                accelerometerEvent = false
                reusableGravityValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableGravityValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableGravityValues[2] = BigDecimal.valueOf(values[2].toDouble())
                configuration.gravity = calculations.resultant(reusableGravityValues)
            }
            android.hardware.Sensor.TYPE_ROTATION_VECTOR -> {
                accelerometerEvent = false
                reusableRotationValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableRotationValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableRotationValues[2] = BigDecimal.valueOf(values[2].toDouble())
                rotation.rawValues = arrayOf(reusableRotationValues[0], reusableRotationValues[1], reusableRotationValues[2])
            }
        }
        
        if (accelerometerEvent) {
            processFiltersAndDetection(timestamp)
            if (configuration.recognitionAlgorithm == 1) {
                applyIntersectionCorrection(timestamp)
            }
        }
        
        if (stepDetected) {
            handleStepDetection()
        } else {
            if (configuration.falseStepDetectionEnabled) {
                sumResMagn.add(resultMagn)
                if (sumResMagn.size > MAX_SUM_RES_MAGN) {
                    sumResMagn.removeAt(0)
                }
            }
        }
        return ProcessingResult(
            stepDetected = stepDetected,
            filteredValue = getFilteredValueForChart(),
            chartLabel = getChartLabel()
        )
    }

    fun processBatchSensorData(instant: Long, eventJson: JSONObject): Boolean {
        stepDetected = false
        collectForCharts = true
        
        // Reset accelerometerEvent - will be set to true only if accelerometer data is present
        accelerometerEvent = false
        
        try {
            // Process accelerometer data if present
            if (eventJson.has("acceleration_x")) {
                //Update fs whit ms of the key
                updateFsFromMillis(instant)
                
                counter++
                lastAccelerationMagnitude = BigDecimal(eventJson.getString("acceleration_magnitude"))
                accelerometerEvent = true
                val vector = arrayOf(
                    BigDecimal(eventJson.getString("acceleration_x")),
                    BigDecimal(eventJson.getString("acceleration_y")),
                    BigDecimal(eventJson.getString("acceleration_z"))
                )
                //Create separate copies for each sensor
                accelerometer.rawValues = arrayOf(vector[0], vector[1], vector[2])
                accelerometerXAxis.rawValues = arrayOf(vector[0], vector[1], vector[2])
            }
            
            // Process magnetometer data if present (can coexist with accelerometer in same JSON)
            if (eventJson.has("magnetometer_x")) {
                magnetometer.rawValues = arrayOf(
                    BigDecimal(eventJson.getString("magnetometer_x")),
                    BigDecimal(eventJson.getString("magnetometer_y")),
                    BigDecimal(eventJson.getString("magnetometer_z"))
                )
            }
            
            // Process gravity data if present
            if (eventJson.has("gravity_x")) {
                configuration.gravity = calculations.resultant(
                    arrayOf(
                        BigDecimal(eventJson.getString("gravity_x")),
                        BigDecimal(eventJson.getString("gravity_y")),
                        BigDecimal(eventJson.getString("gravity_z"))
                    )
                )
            }
            
            // Process rotation data if present
            if (eventJson.has("rotation_x")) {
                rotation.rawValues = arrayOf(
                    BigDecimal(eventJson.getString("rotation_x")),
                    BigDecimal(eventJson.getString("rotation_y")),
                    BigDecimal(eventJson.getString("rotation_z"))
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (accelerometerEvent) {
            processFiltersAndDetection(instant)
            // Apply intersection correction only for intersection algorithm (1), not for time filtering (2)
            if (configuration.recognitionAlgorithm == 1) {
                applyIntersectionCorrection(instant)
            }
        }
        
        if (stepDetected) {
            handleStepDetection()
        } else {
            if (configuration.falseStepDetectionEnabled) {
                sumResMagn.add(resultMagn)
                if (sumResMagn.size > MAX_SUM_RES_MAGN) {
                    sumResMagn.removeAt(0)
                }
            }
        }
        collectForCharts = false
        return stepDetected
    }

    fun processAutocorrelationAlgorithm(jsonObject: JSONObject) {
        val keysSorted = jsonObject.keys().asSequence().toList().sortedBy { it.toLong() }
        recordList.clear()
        chartEntries.clear()
        for (key in keysSorted) {
            val eventJson = jsonObject.getJSONObject(key)
            if (eventJson.has("acceleration_x")) {
                val x = eventJson.getString("acceleration_x").toDouble()
                val y = eventJson.getString("acceleration_y").toDouble()
                val z = eventJson.getString("acceleration_z").toDouble()
                val magnitude = sqrt(x * x + y * y + z * z)
                val timestampMillis = key.toLong()
                recordList.add(Sample(magnitude, timestampMillis / 1000.0))
            }
        }
        if (recordList.size < 4) {
            _stepsCount.set(0)
            return
        }
        val firstSecond = recordList.first().timestamp
        val lastSecond = recordList.last().timestamp
        val totalSeconds = (lastSecond - firstSecond).coerceAtLeast(1e-6)
        val estimatedFs = (((recordList.size - 1) / totalSeconds).toInt()).coerceAtLeast(10)
        val samples = recordList.map { sample ->
            arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(sample.magnitude))
        }
        try {
            val result = stepDetection.countStepsAutocorrelation(
                samples = samples,
                fs = estimatedFs,
                filters = filters,
                calculations = calculations,
                useHannWindowForFFT = true,
                dropHeadSecondsForMSD = 0.3,
                bpOrder = 6
            )
            _stepsCount.set(result.steps)
            val magnitudeWithoutDC = calculations.computeMagnitudeWithoutDC(samples)
            val filteredSignal = filters.filterMagnitudeBandPassSeries(
                magNoDc = magnitudeWithoutDC,
                fs = estimatedFs,
                low = result.bandLowHz,
                high = result.bandHighHz,
                order = 6
            )
            val decimationFactor = if (estimatedFs > 120)
                max(2, floor(estimatedFs.toDouble() / 50.0).toInt())
            else 1
            val fsUsed = estimatedFs.toDouble() / decimationFactor.toDouble()
            val headDropSeconds = 0.3
            val headDropUsed = (headDropSeconds * fsUsed).toInt()
            val offsetOriginal = headDropUsed * decimationFactor
            for (segmentIndex in result.segments.indices) {
                val (start, end) = result.segments[segmentIndex]
                val lag = result.lagsPerSegment[segmentIndex]
                if (lag <= 0) continue
                var position = start + lag
                while (position <= end) {
                    val originalIndex = offsetOriginal + position * decimationFactor
                    if (originalIndex in filteredSignal.indices) {
                        chartEntries.add(
                            Entry(originalIndex.toFloat(), filteredSignal[originalIndex].toFloat())
                        )
                    }
                    position += lag
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _stepsCount.set((recordList.size / 25).coerceAtLeast(0))
            chartEntries.clear()
        }
    }

    /**
     * @deprecated Use updateFsFromMillis() instead for Java-aligned behavior.
     * Public wrapper to update frequency sampling using nanosecond timestamp from sensor events.
     * This method is deprecated in favor of updateFsFromMillis() which uses the same 
     * Java-like per-second counting logic for both batch and live modes.
     */
    @Deprecated("Use updateFsFromMillis() for consistent Java-aligned Fs calculation")
    fun updateFsFromNs(ns: Long) {
        updateFsFromEventTimestamp(ns)
    }

    fun updateFsFromMillis(ms: Long) {
        if (forceFs != null) {
            samplingRateValue = forceFs!!
            return
        }

        val currentSec = ((ms / 1000L).toInt()) % 60

        if (firstCurrentSecond == -1) {
            firstCurrentSecond = currentSec
            currentSecond = currentSec
            numberOfSensorEvents = 0
            return
        }

        // During the first second, count events for initial Fs estimation
        if (currentSec == firstCurrentSecond) {
            numberOfSensorEvents++
            samplingRateValue = numberOfSensorEvents.coerceAtLeast(1)
        } else if (currentSec == currentSecond) {
            // Same second as before, just increment counter
            numberOfSensorEvents++
        } else {
            // New second detected - update Fs and reset counter
            samplingRateValue = numberOfSensorEvents.coerceAtLeast(1)

            // Recalculate alpha dynamically if using low-pass filter
            if (configuration.filterType == 1) {
                if (configuration.cutoffFrequencyIndex == 3) {
                    // Cutoff = 2% of sampling rate
                    cutoffFrequencyValue = (samplingRateValue * 0.02).toInt().coerceAtLeast(1)
                    alpha = calculateAlpha(samplingRateValue, cutoffFrequencyValue!!)
                } else {
                    val cutoff = cutoffFrequencyValue ?: 3
                    alpha = calculateAlpha(samplingRateValue, cutoff)
                }
            }

            // Move to the new second and reset counter
            currentSecond = currentSec
            numberOfSensorEvents = 0
        }
    }

    /**
     * Set a fixed frequency sampling for batch processing mode.
     * This prevents real-time FS calculation and uses the provided value consistently.
     */
    fun setFixedFsForBatch(fs: Int) {
        forceFs = fs
        samplingRateValue = fs
    }

    /**
     * Set initial sampling frequency from configured value.
     * This ensures alpha is calculated with the correct Fs from the start.
     */
    fun setInitialSamplingFrequency(fs: Int) {
        samplingRateValue = fs
        // Recalculate alpha with the correct initial sampling rate
        if (configuration.filterType == 1) {
            if (configuration.cutoffFrequencyIndex == 3) {
                // For 2% sampling rate: recalculate both cutoff and alpha
                cutoffFrequencyValue = (samplingRateValue * 0.02).toInt().coerceAtLeast(1)
                alpha = calculateAlpha(samplingRateValue, cutoffFrequencyValue!!)
            } else {
                // CRITICAL FIX: Use fixed cutoff frequency like Java implementation
                val cutoff = cutoffFrequencyValue!!
                alpha = calculateAlpha(samplingRateValue, cutoff)
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
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }
            val filtered = filters.bagileviFilter(accelerometer.rawValues)
            accelerometer.filteredResultant = filtered
            val peak = keyPointDetection.recognizeLocalExtremaRealtimeBagilevi(filtered, instant)
            stepDetected = stepDetection.detectStepByBagilevi(peak)
        }
    }

    private fun applyLowPassFilter(instant: Long) {
        if (accelerometerEvent) {
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }
            // Use the pre-calculated alpha value that's updated only when Fs changes
            // This ensures cutoff frequency remains fixed (except for 2% mode)
            val aLocal = alpha ?: calculateAlpha(
                samplingRateValue.takeIf { it > 0 } ?: 50,
                cutoffFrequencyValue ?: 3
            )

            val filtered = filters.lowPassFilter(accelerometer.rawValues, aLocal)
            accelerometer.filteredValues = filtered
            accelerometer.filteredResultant = calculations.resultant(filtered)
            val detectionResult = when (configuration.recognitionAlgorithm) {
                0 -> keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)
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
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }
            val magnitude = calculations.resultant(accelerometer.rawValues)
            accelerometer.resultant = magnitude
            val detectionResult = when (configuration.recognitionAlgorithm) {
                0 -> keyPointDetection.recognizeLocalExtremaRealtime(magnitude, instant)
                1 -> keyPointDetection.recognizeLocalExtremaRealtime(magnitude, instant)
                2 -> keyPointDetection.recognizeLocalExtremaTimeFiltering(magnitude, instant)
                else -> keyPointDetection.recognizeLocalExtremaRealtime(magnitude, instant)
            }
            if (detectionResult) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }
    }

    private fun applyRotationMatrixFilter(instant: Long) {
        if (accelerometer.instantiated && magnetometer.instantiated) {
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }
            calculations.updateRotationMatrix(accelerometer.rawValues, magnetometer.rawValues)
            val fixedSystem = calculations.worldAcceleration(accelerometer.rawValues)
            accelerometer.worldValues = fixedSystem
            val zAxis = fixedSystem[2]
            val detectionResult = when (configuration.recognitionAlgorithm) {
                0 -> keyPointDetection.recognizeLocalExtremaRealtime(zAxis, instant)
                1 -> keyPointDetection.recognizeLocalExtremaRealtime(zAxis, instant)
                2 -> keyPointDetection.recognizeLocalExtremaTimeFiltering(zAxis, instant)
                else -> keyPointDetection.recognizeLocalExtremaRealtime(zAxis, instant)
            }
            if (detectionResult) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }
    }

    private fun applyButterworthFilter(instant: Long) {
        if (accelerometerEvent) {
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }
            //Ensure sampling rate is valid before using it
            // Wait for at least 2 seconds of data to get a stable Fs estimate
            val fsLocal = if (samplingRateValue > 10) samplingRateValue else {
                // Skip processing until we have a reliable sampling rate
                return
            }
            val filtered = filters.butterworthFilter(accelerometer.rawValues, cutoffSelected, fsLocal)
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
            // Use separate calculation to avoid shared state issues
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
        if (!magnetometer.instantiated) return
        val magn = magnetometer.rawValues
        vectorMagn[0] = magn[0].toFloat()
        vectorMagn[1] = magn[1].toFloat()
        vectorMagn[2] = magn[2].toFloat()
        resultMagn = sqrt(vectorMagn[0] * vectorMagn[0] + vectorMagn[1] * vectorMagn[1] + vectorMagn[2] * vectorMagn[2])
    }

    private fun handleStepDetection() {
        // Reset false step flag at the beginning
        falseStep = false
        
        // Only run false step detection if enabled
        if (configuration.falseStepDetectionEnabled) {
            // Check algorithm-specific false step conditions first
            when (configuration.recognitionAlgorithm) {
                2 -> falseStep = checkTimeFilteringFalseStep()
            }
            
            // Check filter-specific false step conditions for Butterworth
            if (!falseStep && configuration.filterType == 4) {
                falseStep = checkButterworthFalseStep()
            }
            
            if (!falseStep) {
                falseStep = checkGeneralFalseStep()
            }
        }
        
        // Only increment step count if not a false step
        if (!falseStep) {
            _stepsCount.incrementAndGet()
            if (collectForCharts) {
                lastAccelerationMagnitude?.let { magnitude ->
                    chartEntries.add(Entry(counter.toFloat(), magnitude.toFloat()))
                    if (chartEntries.size > MAX_CHART_ENTRIES) {
                        chartEntries.removeAt(0)
                    }
                }
            }
        }
    }

    private fun checkGeneralFalseStep(): Boolean {
        if (sumResMagn.isEmpty()) return false
        val averageRes = calculations.sumOfMagnet(sumResMagn) / sumResMagn.size
        sumResMagn.clear()
        
        // Don't always allow the first 4 steps
        // Gather data but check also the first 4 steps
        if (resLast4Steps.size < 4) {
            resLast4Steps.add(averageRes)
            if (resLast4Steps.size >= 2) {
                val current = averageRes
                val previous = resLast4Steps[resLast4Steps.size - 2]
                val diff = kotlin.math.abs(current - previous)
                if (diff > 3.6f) {
                    return true
                }
            }
            return false
        }
        
        val isFalseStep = calculations.checkFalseStep(resLast4Steps, averageRes)
        resLast4Steps.removeAt(0)
        resLast4Steps.add(averageRes)
        return isFalseStep
    }

    private fun checkTimeFilteringFalseStep(): Boolean {
        val exMaxSafe = configuration.exMax ?: return false
        val exMinSafe = configuration.exMin ?: return false
        
        // Skip validation for initial values (not yet properly initialized)
        if (exMaxSafe <= 0L || exMinSafe <= 0L) return false
        
        // Calculate time intervals for current and previous steps
        val cur = kotlin.math.abs(configuration.lastStepFirstPhaseTime - configuration.lastStepSecondPhaseTime)
        val old = kotlin.math.abs(exMaxSafe - exMinSafe)

        if (old == 0L || cur < 10L) return false
        if (cur > 5000L || old > 5000L) return true
        
        // Check relative variation between current and previous step intervals
        val rel = kotlin.math.abs(cur - old).toDouble() / old.toDouble()
        // If greater than 40% is false
        return rel > 0.40
    }


    private fun checkButterworthFalseStep(): Boolean {
        val magnN = configuration.lastLocalMaxAccel.toDouble() - configuration.lastLocalMinAccel.toDouble()
        var isFalseStep = false
        
        if (oldMagn != 0.0 && configuration.exMax != null && configuration.exMin != null) {
            val diffMagn = kotlin.math.abs(magnN - oldMagn)
            
            if (diffMagn < 0.3) {
                isFalseStep = true
                oldMagn = magnN
                return isFalseStep
            }
            
            val eps = 1e-6
            val safeSamplingRate = samplingRateValue.coerceAtLeast(1)
            val fi2 = if (diffMagn > eps) kotlin.math.abs(kotlin.math.ceil(safeSamplingRate / diffMagn * 2.0)) else Double.POSITIVE_INFINITY
            val timeDiff = kotlin.math.abs(
                (configuration.lastStepFirstPhaseTime - configuration.lastStepSecondPhaseTime).toDouble() -
                        (configuration.exMax!! - configuration.exMin!!).toDouble()
            )
            if (fi2 > 180.0 && timeDiff < 2.1) {
                isFalseStep = true
            }
            
            val newCutoff = when {
                fi2 < 81.0 -> kotlin.math.max(2.0, (safeSamplingRate / 6.0).let { if (timeDiff > 20.0) it / 2.5 else it })
                fi2 < 183.0 -> kotlin.math.max(2.0, (safeSamplingRate / 7.0).let { if (timeDiff > 40.0) it / 2.857 else it })
                else -> 2.5
            }.coerceIn(2.0, safeSamplingRate / 3.0)
            
            if (kotlin.math.abs(newCutoff - cutoffSelected) > 1.0) {
                cutoffSelected = newCutoff
            }
        }
        oldMagn = magnN
        return isFalseStep
    }

    private fun calculateAlpha(samplingRate: Int, cutoffFrequency: Int): BigDecimal {
        if (samplingRate <= 0) {
            return calculateAlpha(50, cutoffFrequency)
        }
        if (cutoffFrequency <= 0) { 
            return calculateAlpha(samplingRate, 3)
        }
        
        // For EMA: alpha = 2π * fc * dt / (1 + 2π * fc * dt)
        // where dt = 1/fs is sampling rate
        
        val dt = 1.0 / samplingRate.toDouble()  // samppling period
        val rc = 1.0 / (2.0 * Math.PI * cutoffFrequency.toDouble())  // Time constant RC

        // alpha = dt / (RC + dt)
        val alphaValue = dt / (rc + dt)
        
        // Clamp alpha between 0 and 1 for stability
        val clampedAlpha = alphaValue.coerceIn(0.0, 1.0)
        
        return BigDecimal.valueOf(clampedAlpha)
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

    data class ProcessingResult(
        val stepDetected: Boolean,
        val filteredValue: BigDecimal,
        val chartLabel: String
    )

    private data class Sample(
        val magnitude: Double,
        val timestamp: Double
    )
}

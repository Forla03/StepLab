package com.example.steplab.algorithms

import com.github.mikephil.charting.data.Entry
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.util.*
import kotlin.math.pow
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

    var stepDetected = false
        private set
    var accelerometerEvent = false
    private var firstSignal = true
    var lastAccelerationMagnitude: BigDecimal? = null
        private set

    var cutoffFrequencyValue: Int? = null
        private set
    var samplingRateValue: Int = 0
        private set
    private var alpha: BigDecimal? = null

    var stepsCount = 0
        private set
    var counter = 0
        private set

    val chartEntries = mutableListOf<Entry>()

    private val vectorMagn = FloatArray(3)
    private var resultMagn = 0f
    private var resultMagnPrev = 0f
    private val sumResMagn = mutableListOf<Float>()
    private var countFour = 0
    private val resLast4Steps = mutableListOf<Float>()
    private var falseStep = false

    private val last3Acc = mutableListOf<BigDecimal>()
    private var s = 0
    private var sOld = 0
    var cutoffSelected = 3.0
        private set
    private var oldMagn = 0.0

    private val recordList = mutableListOf<Sample>()
    private var startingPoint = 0L

    private val reusableValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableXAxisValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableMagnetometerValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableGravityValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableRotationValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)

    private var lastAccelTsNanos: Long = -1L
    private var emaFs: Double = 0.0
    private val FS_EMA_ALPHA = 0.2
    private var collectForCharts = false

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
            firstSignal = true
            if (configuration.filterType == 1 && cutoffFrequencyValue != null) {
                alpha = calculateAlpha(50, cutoffFrequencyValue!!)
            }
        }
    }

    fun processRealTimeSensorData(
        sensorType: Int,
        values: FloatArray,
        timestamp: Long
    ): ProcessingResult {
        stepDetected = false
        when (sensorType) {
            android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                accelerometerEvent = true
                reusableValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableValues[2] = BigDecimal.valueOf(values[2].toDouble())
                reusableXAxisValues[0] = reusableValues[0]
                reusableXAxisValues[1] = reusableValues[1]
                reusableXAxisValues[2] = reusableValues[2]
                accelerometer.rawValues = reusableValues
                accelerometerXAxis.rawValues = reusableXAxisValues
                lastAccelerationMagnitude = calculations.resultant(reusableValues)
                updateFsFromEventTimestamp(timestamp)
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
            android.hardware.Sensor.TYPE_ROTATION_VECTOR -> {
                accelerometerEvent = false
                reusableRotationValues[0] = BigDecimal.valueOf(values[0].toDouble())
                reusableRotationValues[1] = BigDecimal.valueOf(values[1].toDouble())
                reusableRotationValues[2] = BigDecimal.valueOf(values[2].toDouble())
                rotation.rawValues = reusableRotationValues
            }
        }
        if (accelerometerEvent) {
            processFiltersAndDetection(timestamp)
            if (configuration.recognitionAlgorithm == 1 && configuration.filterType != 4) {
                applyIntersectionCorrection(timestamp)
            }
        }
        if (stepDetected) {
            handleStepDetection()
        } else {
            if (configuration.falseStepDetectionEnabled) {
                sumResMagn.add(resultMagn)
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
                configuration.gravity = calculations.resultant(
                    arrayOf(
                        BigDecimal(eventJson.getString("gravity_x")),
                        BigDecimal(eventJson.getString("gravity_y")),
                        BigDecimal(eventJson.getString("gravity_z"))
                    )
                )
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
        processFiltersAndDetection(instant)
        if (configuration.recognitionAlgorithm == 1 && configuration.filterType != 4) {
            applyIntersectionCorrection(instant)
        }
        if (stepDetected) {
            handleStepDetection()
        } else {
            if (configuration.falseStepDetectionEnabled) {
                sumResMagn.add(resultMagn)
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
            stepsCount = 0
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
            stepsCount = result.steps
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
            stepsCount = (recordList.size / 25).coerceAtLeast(0)
            chartEntries.clear()
        }
    }

    private fun updateFsFromEventTimestamp(tsNanos: Long) {
        if (lastAccelTsNanos > 0L) {
            val dtSec = ((tsNanos - lastAccelTsNanos).coerceAtLeast(1_000_000L)) / 1e9
            val instFs = 1.0 / dtSec
            emaFs = if (emaFs == 0.0) instFs else FS_EMA_ALPHA * instFs + (1 - FS_EMA_ALPHA) * emaFs
            samplingRateValue = emaFs.toInt().coerceIn(10, 400)
            if (configuration.filterType == 1 && configuration.cutoffFrequencyIndex != 3 && cutoffFrequencyValue != null) {
                alpha = calculateAlpha(samplingRateValue, cutoffFrequencyValue!!)
            }
        }
        lastAccelTsNanos = tsNanos
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
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }
            val aLocal = if (configuration.cutoffFrequencyIndex == 3) alpha!! else (alpha ?: calculateAlpha(max(samplingRateValue, 50), cutoffFrequencyValue!!))
            val filtered = filters.lowPassFilter(accelerometer.rawValues, aLocal)
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
            if (configuration.falseStepDetectionEnabled) {
                calculateMagnetometerMagnitude()
            }
            val fsLocal = if (samplingRateValue > 0) samplingRateValue else 50
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
        if (configuration.recognitionAlgorithm == 2) {
            checkTimeFilteringFalseStep()
        }
        if (configuration.filterType == 4 && configuration.falseStepDetectionEnabled) {
            checkButterworthFalseStep()
        }
        if (configuration.falseStepDetectionEnabled) {
            checkFalseStep()
        }
        if (!falseStep) {
            stepsCount++
            if (collectForCharts) {
                lastAccelerationMagnitude?.let { magnitude ->
                    chartEntries.add(Entry(counter.toFloat(), magnitude.toFloat()))
                }
            }
        } else {
            falseStep = false
        }
    }

    private fun checkFalseStep() {
        if (!configuration.falseStepDetectionEnabled) return
        if (sumResMagn.isEmpty()) return
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
                fi2 < 81.0 -> (samplingRateValue / 6.0).let { if (timeDiff > 20.0) it / 2.5 else it }
                fi2 < 183.0 -> (samplingRateValue / 7.0).let { if (timeDiff > 40.0) it / 2.857 else it }
                else -> 0.0
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

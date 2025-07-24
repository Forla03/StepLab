package com.example.steplab.ui.test

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.steplab.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class PedometerRunningFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravitySensor: Sensor? = null

    private var isAccelerometerEvent = false
    private val accelerometerData = SensorData()
    private val accelerometerXAxis = SensorData()
    private val magnetometerData = SensorData()
    private lateinit var filters: Filters
    private lateinit var calculations: Calculations
    private lateinit var keyValueRecognition: KeyValueRecognition
    private lateinit var stepDetection: StepDetection
    private lateinit var configuration: Configuration

    private var stepDetected = false
    private var samplingPeriodMicros: Int = 0
    private var samplingRate: Int = 0
    private var selectedCutoffIndex: Int = 0
    private var cutoffFrequencyValue: Int = 0
    private var alpha: BigDecimal = BigDecimal("0.1")

    private var currentSecond: Int = 0
    private var firstSecond: Int = 0
    private var sensorEventCount: Int = 0
    private var stepCount: Int = 0
    private var counter: Int = 0
    private var timestamp: Long = 0
    
    // Reuse Calendar instance to avoid frequent object creation
    private val calendar = Calendar.getInstance()
    
    // Reuse BigDecimal arrays to reduce object creation
    private val reusableValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableMagnetometerValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val reusableGravityValues = arrayOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    
    // Throttling for rotation matrix calculations (very expensive)
    private var rotationMatrixUpdateCounter = 0
    
    // Cache last world acceleration values to avoid recalculating every time
    private var lastWorldValues: Array<BigDecimal>? = null

    private lateinit var chartLine: LineDataSet
    private lateinit var chartData: LineData
    private lateinit var lineChart: LineChart
    private lateinit var stepCountTextView: TextView

    companion object {
        private const val CONFIG_KEY = "config"

        fun newInstance(configuration: Configuration): PedometerRunningFragment {
            val fragment = PedometerRunningFragment()
            val args = Bundle()
            args.putSerializable(CONFIG_KEY, configuration)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration = arguments?.getSerializable(CONFIG_KEY) as Configuration
        initializeWithConfiguration()
    }

    private fun initializeWithConfiguration() {
        filters = Filters(configuration)
        calculations = Calculations(configuration)
        keyValueRecognition = KeyValueRecognition(configuration)
        stepDetection = StepDetection(configuration)
        configuration.lastDetectedStepTime = 0L

        calendar.timeInMillis = System.currentTimeMillis()
        currentSecond = calendar.get(Calendar.SECOND)
        firstSecond = currentSecond
        stepCount = 0
        counter = 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.pedometer_running_fragment, container, false)

        stepCountTextView = root.findViewById(R.id.step_count)
        lineChart = root.findViewById(R.id.line_chart)
        lineChart.description.isEnabled = false
        lineChart.xAxis.setDrawLabels(false)

        chartLine = LineDataSet(null, "Magnitude of Acceleration").apply {
            color = Color.RED
            setDrawCircles(false)
            setDrawValues(false)
        }

        chartData = LineData()
        chartData.addDataSet(chartLine)
        lineChart.data = chartData

        when (configuration.samplingFrequencyIndex) {
            0 -> {
                samplingRate = 20
                samplingPeriodMicros = 50000
            }
            1 -> {
                samplingRate = 40
                samplingPeriodMicros = 25000
            }
            2 -> {
                samplingRate = 50
                samplingPeriodMicros = 20000
            }
            3 -> {
                samplingRate = 100
                samplingPeriodMicros = 10000
            }
            4 -> {
                samplingRate = 250
                samplingPeriodMicros = SensorManager.SENSOR_DELAY_FASTEST
            }
        }
        sensorEventCount = samplingRate

        selectedCutoffIndex = configuration.cutoffFrequencyIndex
        if (selectedCutoffIndex == 3) {
            alpha = BigDecimal("0.1")
        } else {
            cutoffFrequencyValue = when (selectedCutoffIndex) {
                0 -> 2
                1 -> 3
                2 -> 10
                else -> 2
            }
            alpha = calculateAlpha(samplingRate, cutoffFrequencyValue)
        }

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

        return root
    }

    override fun onResume() {
        super.onResume()
        // Use a slightly throttled sampling rate to balance performance and responsiveness
        val actualSamplingRate = when {
            samplingPeriodMicros == SensorManager.SENSOR_DELAY_FASTEST -> SensorManager.SENSOR_DELAY_GAME
            samplingPeriodMicros < SensorManager.SENSOR_DELAY_UI -> SensorManager.SENSOR_DELAY_UI
            else -> samplingPeriodMicros
        }
        sensorManager.registerListener(this, accelerometer, actualSamplingRate)
        sensorManager.registerListener(this, magnetometer, actualSamplingRate)
        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        timestamp = System.currentTimeMillis()
        stepDetected = false

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                isAccelerometerEvent = true
                // Reuse array to reduce object creation
                reusableValues[0] = BigDecimal.valueOf(event.values[0].toDouble())
                reusableValues[1] = BigDecimal.valueOf(event.values[1].toDouble())
                reusableValues[2] = BigDecimal.valueOf(event.values[2].toDouble())
                
                accelerometerData.rawValues = reusableValues
                accelerometerXAxis.rawValues = reusableValues

                calendar.timeInMillis = timestamp
                val second = calendar.get(Calendar.SECOND)
                if (second != firstSecond) {
                    if (second == currentSecond) {
                        sensorEventCount++
                    } else {
                        samplingRate = sensorEventCount
                        if (selectedCutoffIndex != 3) {
                            cutoffFrequencyValue = when (selectedCutoffIndex) {
                                0 -> 2
                                1 -> 3
                                2 -> 10
                                else -> 2
                            }
                            alpha = calculateAlpha(samplingRate, cutoffFrequencyValue)
                        }
                        currentSecond = second
                        sensorEventCount = 0
                    }
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                isAccelerometerEvent = false
                reusableMagnetometerValues[0] = BigDecimal.valueOf(event.values[0].toDouble())
                reusableMagnetometerValues[1] = BigDecimal.valueOf(event.values[1].toDouble())
                reusableMagnetometerValues[2] = BigDecimal.valueOf(event.values[2].toDouble())
                magnetometerData.rawValues = reusableMagnetometerValues
            }

            Sensor.TYPE_GRAVITY -> {
                isAccelerometerEvent = false
                reusableGravityValues[0] = BigDecimal.valueOf(event.values[0].toDouble())
                reusableGravityValues[1] = BigDecimal.valueOf(event.values[1].toDouble())
                reusableGravityValues[2] = BigDecimal.valueOf(event.values[2].toDouble())
                configuration.gravity = calculations.resultant(reusableGravityValues)
            }
        }

        if (configuration.realTimeMode == 0 && isAccelerometerEvent) {
            // In live testing, prevent using Butterworth filter - default to no filter
            val actualFilterType = if (configuration.filterType == 4) 2 else configuration.filterType
            
            when (actualFilterType) {
                0 -> { // Bagilevi Filter
                    val filtered = filters.bagileviFilter(accelerometerData.rawValues)
                    accelerometerData.filteredResultant = filtered
                    stepDetected = stepDetection.detectStepByBagilevi(
                        keyValueRecognition.recognizeLocalExtremaRealtimeBagilevi(filtered, timestamp)
                    )

                    chartLine.label = "Acceleration - Bagilevi Filter"
                    chartData = lineChart.data
                    chartData.addEntry(Entry(counter.toFloat(), filtered.toFloat()), 0)
                    chartData.notifyDataChanged()
                    lineChart.notifyDataSetChanged()
                    lineChart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
                    lineChart.moveViewToX(chartData.entryCount.toFloat())
                    counter++
                }

                1 -> { // Low-pass Filter
                    val filtered = filters.lowPassFilter(accelerometerData.rawValues, alpha)
                    accelerometerData.filteredValues = filtered
                    val magnitude = calculations.resultant(filtered)
                    accelerometerData.filteredResultant = magnitude

                    if (keyValueRecognition.recognizeLocalExtremaRealtime(magnitude, timestamp)) {
                        stepDetected = stepDetection.detectStepByPeakDifference()
                    }

                    chartLine.label = "Acceleration - Low Pass Filter"
                    chartData = lineChart.data
                    chartData.addEntry(Entry(counter.toFloat(), magnitude.toFloat()), 0)
                    chartData.notifyDataChanged()
                    lineChart.notifyDataSetChanged()
                    lineChart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
                    lineChart.moveViewToX(chartData.entryCount.toFloat())
                    counter++
                }

                2 -> { // No Filter
                    val magnitude = calculations.resultant(accelerometerData.rawValues)
                    accelerometerData.resultant = magnitude

                    if (keyValueRecognition.recognizeLocalExtremaRealtime(magnitude, timestamp)) {
                        stepDetected = stepDetection.detectStepByPeakDifference()
                    }

                    chartLine.label = "Acceleration - No Filter"
                    chartData = lineChart.data
                    chartData.addEntry(Entry(counter.toFloat(), magnitude.toFloat()), 0)
                    chartData.notifyDataChanged()
                    lineChart.notifyDataSetChanged()
                    lineChart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
                    lineChart.moveViewToX(chartData.entryCount.toFloat())
                    counter++
                }

                3 -> { // Rotation Matrix Filter
                    if (accelerometerData.instantiated && magnetometerData.instantiated) {
                        // Throttle rotation matrix calculations - very expensive operation
                        rotationMatrixUpdateCounter++
                        if (rotationMatrixUpdateCounter % 5 == 0) { // Update rotation matrix every 5th accelerometer event
                            calculations.updateRotationMatrix(accelerometerData.rawValues, magnetometerData.rawValues)
                            // Recalculate world values when rotation matrix is updated
                            lastWorldValues = calculations.worldAcceleration(accelerometerData.rawValues)
                            accelerometerData.worldValues = lastWorldValues!!
                        } else if (lastWorldValues != null) {
                            // Use cached rotation matrix for world acceleration calculation (much faster)
                            lastWorldValues = calculations.worldAcceleration(accelerometerData.rawValues)
                            accelerometerData.worldValues = lastWorldValues!!
                        }

                        // Always update chart and step detection for fluidity
                        if (lastWorldValues != null) {
                            val zAxis = lastWorldValues!![2]
                            if (keyValueRecognition.recognizeLocalExtremaRealtime(zAxis, timestamp)) {
                                stepDetected = stepDetection.detectStepByPeakDifference()
                            }

                            chartLine.label = "Acceleration - Rotation Matrix Filter (Z axis)"
                            chartData = lineChart.data
                            chartData.addEntry(Entry(counter.toFloat(), zAxis.toFloat()), 0)
                            chartData.notifyDataChanged()
                            lineChart.notifyDataSetChanged()
                            lineChart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
                            lineChart.moveViewToX(chartData.entryCount.toFloat())
                            counter++
                        }
                    }
                }
            }

            if (configuration.recognitionAlgorithm == 1) {
                if (isAccelerometerEvent) {
                    val magnitude = calculations.resultant(accelerometerXAxis.rawValues)
                    accelerometerXAxis.resultant = magnitude
                    val linear = calculations.linearAcceleration(magnitude)
                    accelerometerXAxis.linearResultant = linear
                    keyValueRecognition.recognizeXAxisIntersectionRealtime(linear, timestamp)
                    stepDetected = stepDetected && stepDetection.detectStepByCrossing()
                } else {
                    stepDetected = false
                }
            }
        }

        if (stepDetected) {
            onStepDetected()
        }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignored for now
    }

    private fun onStepDetected() {
        stepCount++
        stepCountTextView.text = stepCount.toString()
    }

    private fun calculateAlpha(samplingRate: Int, cutoffFrequency: Int): BigDecimal {
        val sampling = BigDecimal.ONE.divide(BigDecimal.valueOf(samplingRate.toLong()), MathContext.DECIMAL32)
        val cutoff = BigDecimal.ONE.divide(
            BigDecimal(2).multiply(BigDecimal.valueOf(Math.PI), MathContext.DECIMAL32)
                .multiply(BigDecimal.valueOf(cutoffFrequency.toLong()), MathContext.DECIMAL32),
            MathContext.DECIMAL32
        )
        return sampling.divide(sampling.add(cutoff), MathContext.DECIMAL32)
    }
}

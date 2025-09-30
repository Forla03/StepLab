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
import com.example.steplab.ui.main.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class PedometerRunningFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravitySensor: Sensor? = null

    private lateinit var configuration: Configuration
    private lateinit var stepDetectionProcessor: StepDetectionProcessor

    private var samplingPeriodMicros: Int = 0
    private var stepCount: Int = 0
    private var counter: Int = 0
    private var timestamp: Long = 0

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
        stepDetectionProcessor = StepDetectionProcessor(configuration)
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
            0 -> samplingPeriodMicros = 50000
            1 -> samplingPeriodMicros = 25000
            2 -> samplingPeriodMicros = 20000
            3 -> samplingPeriodMicros = 10000
            4 -> samplingPeriodMicros = SensorManager.SENSOR_DELAY_FASTEST
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
        val nowMs = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            stepDetectionProcessor.updateFsFromNs(event.timestamp)
        }

        val result = stepDetectionProcessor.processRealTimeSensorData(
            event.sensor.type,
            event.values,
            nowMs // ms per logiche di soglie/tempi già in ms
        )

        if (stepDetectionProcessor.accelerometerEvent) updateChart(result)
        if (result.stepDetected) onStepDetected()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignored for now
    }

    private fun updateChart(result: StepDetectionProcessor.ProcessingResult) {
        chartLine.label = result.chartLabel
        chartData = lineChart.data
        chartData.addEntry(Entry(counter.toFloat(), result.filteredValue.toFloat()), 0)
        chartData.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
        lineChart.moveViewToX(chartData.entryCount.toFloat())
        counter++
    }

    private fun onStepDetected() {
        stepCount++
        stepCountTextView.text = stepCount.toString()
    }
}

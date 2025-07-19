package com.example.steplab.ui.configuration

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.*
import com.example.steplab.data.local.EntityTest
import com.example.steplab.ui.test.SelectTest
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class ConfigurationsComparison : AppCompatActivity() {

    private lateinit var configurations: ArrayList<Configuration>
    private lateinit var testApp: EntityTest
    private lateinit var jsonObject: JSONObject

    private val colors = arrayOf(Color.BLUE, Color.YELLOW, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.BLACK)

    private lateinit var chart: LineChart
    private lateinit var startButton: Button
    private lateinit var selectTestButton: Button
    private lateinit var recyclerView: RecyclerView

    private val dataset = arrayListOf<Configuration>()
    private val colorDataset = arrayListOf<Int>()
    private val stepDataset = arrayListOf<Int>()
    private lateinit var chartData: LineData
    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.configurations_comparison)

        configurations = intent.getSerializableExtra("configurations") as ArrayList<Configuration>
        val testId = intent.getSerializableExtra("test") as String

        lifecycleScope.launch(Dispatchers.IO) {
            testApp = MainActivity.getDatabase()?.databaseDao()?.getTestFromId(testId.toInt()) ?: return@launch
            jsonObject = JSONObject(testApp.testValues)

            lifecycleScope.launch(Dispatchers.Main) {
                setupViews()
                setupChart()
                drawBaseLine()
                setupRecyclerView()
                processConfigurations()
            }
        }
    }

    private fun setupViews() {
        chart = findViewById(R.id.line_chart)
        recyclerView = findViewById(R.id.recycler_view)
        startButton = findViewById(R.id.start_new_comparison)
        selectTestButton = findViewById(R.id.select_another_test)

        startButton.setOnClickListener {
            startActivity(Intent(applicationContext, SelectConfigurationsToCompare::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        selectTestButton.setOnClickListener {
            startActivity(Intent(applicationContext, SelectTest::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("configurations", configurations))
        }
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            xAxis.setDrawLabels(false)
            chartData = LineData()
            data = chartData
        }

        val baseLine = LineDataSet(null, "Magnitude of Acceleration").apply {
            color = Color.RED
            setDrawCircles(false)
            setDrawValues(false)
        }

        chartData.addDataSet(baseLine)
    }

    private fun drawBaseLine() {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = jsonObject.getJSONObject(key)
            if (obj.has("acceleration_magnitude")) {
                chartData.addEntry(Entry(counter.toFloat(), obj.getString("acceleration_magnitude").toFloat()), 0)
                counter++
            }
        }
        chartData.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
    }

    private fun setupRecyclerView() {
        recyclerView.apply {
            val chartDataSets = ArrayList<LineDataSet>().apply {
                for (set in chartData.dataSets) {
                    if (set is LineDataSet) add(set)
                }
            }

            adapter = AdapterForConfigurationsCard(
                configurations = dataset,
                colors = colorDataset,
                stepCounts = stepDataset,
                chartDataSets = chartDataSets,
                context = this@ConfigurationsComparison,
                fullChartData = chartData,
                chart = chart
            )

            layoutManager = LinearLayoutManager(this@ConfigurationsComparison)
            setHasFixedSize(true)
        }
    }

    private fun processConfigurations() {
        for ((index, config) in configurations.withIndex()) {
            val clonedConfig = config.clone() as Configuration
            val context = ConfigurationContext(clonedConfig, colors[index], index, chart, chartData)

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val event = jsonObject.getJSONObject(key)
                context.runDetection(key.toLong(), event)
            }

            dataset.add(clonedConfig)
            colorDataset.add(colors[index])
            stepDataset.add(context.stepsCount)
        }
        recyclerView.adapter?.notifyDataSetChanged()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private inner class ConfigurationContext(
        val configuration: Configuration,
        val color: Int,
        val index: Int,
        val chart: LineChart,
        val chartData: LineData
    ) {
        val accelerometer = SensorData()
        val accelerometerXAxis = SensorData()
        val magnetometer = SensorData()
        val filters = Filters(configuration)
        val calculations = Calculations(configuration)
        val keyPointDetection = KeyValueRecognition(configuration)
        val stepDetection = StepDetection(configuration)
        var stepDetected = false
        var lastAccelerationMagnitude: BigDecimal? = null

        var cutoffFrequencyValue: Int? = null
        var samplingRateValue: Int? = null
        var signalCount: Int? = null
        var firstSecond: Int? = null
        var currentSecond: Int? = null
        var alpha: BigDecimal? = null
        var date: Calendar? = null
        var firstSignal = true
        var stepsCount = 0

        fun runDetection(instant: Long, event: JSONObject) {
            stepDetected = false

            when {
                event.has("acceleration_x") -> processAccelerometerEvent(event)
                event.has("magnetometer_x") -> processMagnetometerEvent(event)
                event.has("gravity_x") -> processGravityEvent(event)
            }

            if (configuration.realTimeMode == 0) {
                applyFilterAndDetection(instant)

                if (configuration.recognitionAlgorithm == 1) {
                    applyIntersectionCorrection(instant)
                }

                if (stepDetected) {
                    stepsCount++
                    chartData.addEntry(Entry(counter.toFloat(), lastAccelerationMagnitude!!.toFloat()), index + 1)
                    chartData.notifyDataChanged()
                    chart.notifyDataSetChanged()
                    chart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
                }
            }
        }

        private fun processAccelerometerEvent(event: JSONObject) {
            counter++
            lastAccelerationMagnitude = BigDecimal(event.getString("acceleration_magnitude"))
            val vector = arrayOf(
                BigDecimal(event.getString("acceleration_x")),
                BigDecimal(event.getString("acceleration_y")),
                BigDecimal(event.getString("acceleration_z"))
            )
            accelerometer.rawValues = vector
            accelerometerXAxis.rawValues = vector
        }

        private fun processMagnetometerEvent(event: JSONObject) {
            magnetometer.rawValues = arrayOf(
                BigDecimal(event.getString("magnetometer_x")),
                BigDecimal(event.getString("magnetometer_y")),
                BigDecimal(event.getString("magnetometer_z"))
            )
        }

        private fun processGravityEvent(event: JSONObject) {
            configuration.gravity = calculations.resultant(arrayOf(
                BigDecimal(event.getString("gravity_x")),
                BigDecimal(event.getString("gravity_y")),
                BigDecimal(event.getString("gravity_z"))
            ))
        }

        private fun applyFilterAndDetection(instant: Long) {
            when (configuration.filterType) {
                0 -> applyBagileviFilter(instant)
                1 -> applyLowPassFilter(instant)
                2 -> applyNoFilter(instant)
                3 -> applyRotationMatrixFilter(instant)
            }
        }

        private fun applyBagileviFilter(instant: Long) {
            val filtered = filters.bagileviFilter(accelerometer.rawValues)
            accelerometer.filteredResultant = filtered
            val peak = keyPointDetection.recognizeLocalExtremaRealtimeBagilevi(filtered, instant)
            stepDetected = stepDetection.detectStepByBagilevi(peak)
        }

        private fun applyLowPassFilter(instant: Long) {
            alpha = if (configuration.cutoffFrequencyIndex != 3) {
                updateSamplingFrequency(instant)
                calculateAlpha(samplingRateValue, cutoffFrequencyValue)
            } else BigDecimal("0.1")

            val filtered = filters.lowPassFilter(accelerometer.rawValues, alpha!!)
            accelerometer.filteredValues = filtered
            accelerometer.filteredResultant = calculations.resultant(filtered)

            if (keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }

        private fun applyNoFilter(instant: Long) {
            val magnitude = calculations.resultant(accelerometer.rawValues)
            accelerometer.resultant = magnitude
            if (keyPointDetection.recognizeLocalExtremaRealtime(magnitude, instant)) {
                stepDetected = stepDetection.detectStepByPeakDifference()
            }
        }

        private fun applyRotationMatrixFilter(instant: Long) {
            if (accelerometer.instantiated && magnetometer.instantiated) {
                calculations.updateRotationMatrix(accelerometer.rawValues, magnetometer.rawValues)
                val fixedSystem = calculations.worldAcceleration(accelerometer.rawValues)
                accelerometer.worldValues = fixedSystem

                if (keyPointDetection.recognizeLocalExtremaRealtime(fixedSystem[2], instant)) {
                    stepDetected = stepDetection.detectStepByPeakDifference()
                }
            }
        }

        private fun applyIntersectionCorrection(instant: Long) {
            val magnitude = calculations.resultant(accelerometerXAxis.rawValues)
            accelerometerXAxis.resultant = magnitude
            val linear = calculations.linearAcceleration(magnitude)
            accelerometerXAxis.linearResultant = linear
            keyPointDetection.recognizeXAxisIntersectionRealtime(linear, instant)
            stepDetected = stepDetected && stepDetection.detectStepByCrossing()
        }

        private fun updateSamplingFrequency(instant: Long) {
            date = Calendar.getInstance().apply { timeInMillis = instant }
            val second = date!!.get(Calendar.SECOND)
            if (firstSignal) {
                firstSecond = second
                currentSecond = second
                firstSignal = false
            }
            if (second == firstSecond) {
                signalCount = (signalCount ?: 0) + 1
                samplingRateValue = (samplingRateValue ?: 0) + 1
            } else if (second == currentSecond) {
                signalCount = (signalCount ?: 0) + 1
            } else {
                currentSecond = second
                samplingRateValue = signalCount
                signalCount = 0
            }
        }

        private fun calculateAlpha(samplingRate: Int?, cutoffFrequency: Int?): BigDecimal {
            val sampling = BigDecimal.ONE.divide(BigDecimal.valueOf(samplingRate?.toLong() ?: 1), MathContext.DECIMAL32)
            val cutoff = BigDecimal.ONE.divide(
                BigDecimal.valueOf(2).multiply(BigDecimal.valueOf(Math.PI), MathContext.DECIMAL32)
                    .multiply(BigDecimal.valueOf(cutoffFrequency?.toLong() ?: 1), MathContext.DECIMAL32),
                MathContext.DECIMAL32
            )
            return sampling.divide(sampling.add(cutoff), MathContext.DECIMAL32)
        }
    }
}

package com.example.steplab.ui.configuration
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
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
import kotlinx.coroutines.withContext
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
    private lateinit var progressBar: ProgressBar

    private val dataset = arrayListOf<Configuration>()
    private val colorDataset = arrayListOf<Int>()
    private val stepDataset = arrayListOf<Int>()
    private val myLines = arrayListOf<LineDataSet>()
    private lateinit var chartData: LineData
    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.configurations_comparison)

        configurations = intent.getSerializableExtra("configurations") as ArrayList<Configuration>
        val testId = intent.getSerializableExtra("test") as String

        lifecycleScope.launch(Dispatchers.IO) {
            testApp = MainActivity.getDatabase()?.databaseDao()?.getTestFromId(testId.toInt())
                ?: return@launch
            jsonObject = JSONObject(testApp.testValues)

            withContext(Dispatchers.Main) {
                setupViews()
                setupChart()
                drawBaseLine()
                setupRecyclerView()
                processConfigurationsSync()
            }
        }
    }

    private fun setupViews() {
        chart = findViewById(R.id.line_chart)
        recyclerView = findViewById(R.id.recycler_view)
        startButton = findViewById(R.id.start_new_comparison)
        selectTestButton = findViewById(R.id.select_another_test)
        progressBar = findViewById(R.id.progress_bar)

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
        chart = findViewById(R.id.line_chart)
        chart.description.isEnabled = false
        chart.xAxis.setDrawLabels(false)
        chartData = LineData()
        chart.data = chartData
    }

    private fun drawBaseLine() {
        val baseEntries = mutableListOf<Entry>()
        val keys = jsonObject.keys()
        var index = 0

        // Create baseline dataset first
        val baseLine = LineDataSet(null, "Magnitude of Acceleration").apply {
            color = Color.RED
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
        }
        chartData.addDataSet(baseLine)
        chart.data = chartData

        // Add baseline data points
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = jsonObject.getJSONObject(key)
            if (obj.has("acceleration_magnitude")) {
                chartData.addEntry(Entry(index.toFloat(), obj.getString("acceleration_magnitude").toFloat()), 0)
                index++
            }
        }

        chartData.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
        chart.invalidate()
    }

    private fun setupRecyclerView() {
        val adapter = AdapterForConfigurationsCard(
            configurations = dataset,
            colors = colorDataset,
            stepCounts = stepDataset,
            chartDataSets = myLines,
            context = this@ConfigurationsComparison,
            fullChartData = chartData,
            chart = chart
        )
        recyclerView.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(this@ConfigurationsComparison)
            setHasFixedSize(true)
        }
    }

    private fun processConfigurationsSync() {
        // Show progress bar
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.Default) {
            val processedConfigurations = mutableListOf<ProcessedConfiguration>()
            
            for (i in configurations.indices) {
                val config = configurations[i]

                try {
                    val clonedConfig = config.clone() as Configuration
                    val configColor = colors[i % colors.size]
                    val context = ConfigurationContext(clonedConfig, i)

                    // Process all events for this configuration in background
                    val keys = jsonObject.keys()
                    var processedEvents = 0
                    val totalEvents = jsonObject.length()
                    
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val eventJson = jsonObject.getJSONObject(key)
                        context.myOnSensorChanged(key.toLong(), eventJson)
                        
                        processedEvents++
                        
                        // Yield control every 100 events to prevent ANR
                        if (processedEvents % 100 == 0) {
                            kotlinx.coroutines.yield()
                        }
                    }

                    // Collect processed data
                    processedConfigurations.add(
                        ProcessedConfiguration(
                            clonedConfig,
                            configColor,
                            context.stepsCount,
                            context.chartEntries
                        )
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                updateUIWithProcessedConfigurations(processedConfigurations)
                // Hide progress bar and show results
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun updateUIWithProcessedConfigurations(processedConfigurations: List<ProcessedConfiguration>) {
        for ((index, processed) in processedConfigurations.withIndex()) {
            // Add LineDataSet for this configuration
            val configDataSet = LineDataSet(processed.chartEntries, "${index + 1}").apply {
                color = processed.color
                setDrawValues(false)
                setCircleColor(processed.color)
                setDrawCircles(true)
                circleRadius = 4f
                lineWidth = 2f
            }

            myLines.add(configDataSet)
            chartData.addDataSet(configDataSet)
            chart.data = chartData

            // Add results to datasets
            dataset.add(processed.configuration)
            colorDataset.add(processed.color)
            stepDataset.add(processed.stepCount)
        }

        // Final chart update
        chartData.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private data class ProcessedConfiguration(
        val configuration: Configuration,
        val color: Int,
        val stepCount: Int,
        val chartEntries: MutableList<Entry>
    )

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private inner class ConfigurationContext(
        val configuration: Configuration,
        val index: Int
    ) {
        val accelerometer = SensorData()
        val accelerometerXAxis = SensorData()
        val magnetometer = SensorData()
        val filters = Filters(configuration)
        val calculations = Calculations(configuration)
        val keyPointDetection = KeyValueRecognition(configuration)
        val stepDetection = StepDetection(configuration)

        var stepDetected = false
        var accelerometerEvent = false
        var firstSignal = true
        var lastAccelerationMagnitude: BigDecimal? = null

        var cutoffFrequencyValue: Int? = null
        var samplingRateValue: Int = 0
        var signalCount: Int = 0
        var firstSecond: Int? = null
        var currentSecond: Int? = null
        var alpha: BigDecimal? = null
        var date: Calendar? = null
        var stepsCount = 0
        var counter = 0
        
        // Store chart entries for this configuration
        val chartEntries = mutableListOf<Entry>()

        init {
            configuration.lastDetectedStepTime = 0L
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

        fun myOnSensorChanged(instant: Long, eventJson: JSONObject) {
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
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (configuration.realTimeMode == 0) { // real-time
                when (configuration.filterType) {
                    0 -> applyBagileviFilter(instant)
                    1 -> applyLowPassFilter(instant)
                    2 -> applyNoFilter(instant)
                    3 -> applyRotationMatrixFilter(instant)
                    4 -> applyButterworthFilter(instant)
                }

                if (configuration.recognitionAlgorithm == 1) {
                    applyIntersectionCorrection(instant)
                }

                if (stepDetected) {
                    stepsCount++
                    // Update parameters for Butterworth dynamic cutoff
                    updateButterworthParameters(instant)
                    // Store chart entry instead of adding directly to chart
                    chartEntries.add(Entry(counter.toFloat(), lastAccelerationMagnitude!!.toFloat()))
                }
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

                val filtered = filters.lowPassFilter(accelerometer.rawValues, alpha!!)
                accelerometer.filteredValues = filtered
                accelerometer.filteredResultant = calculations.resultant(filtered)

                if (keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)) {
                    stepDetected = stepDetection.detectStepByPeakDifference()
                } else {
                    stepDetected = false
                }
            }
        }

        private fun applyNoFilter(instant: Long) {
            if (accelerometerEvent) {
                val magnitude = calculations.resultant(accelerometer.rawValues)
                accelerometer.resultant = magnitude

                if (keyPointDetection.recognizeLocalExtremaRealtime(magnitude, instant)) {
                    stepDetected = stepDetection.detectStepByPeakDifference()
                } else {
                    stepDetected = false
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
                } else {
                    stepDetected = false
                }
            } else {
                stepDetected = false
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

        private fun applyButterworthFilter(instant: Long) {
            if (accelerometerEvent) {
                // Update sampling frequency dynamically (similar to low-pass filter)
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
                    // Update the sampling rate in configuration for Butterworth filter
                    configuration.samplingRate = samplingRateValue
                }

                val filtered = filters.butterworthFilter(accelerometer.rawValues)
                accelerometer.filteredValues = filtered
                accelerometer.filteredResultant = calculations.resultant(filtered)

                if (keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)) {
                    stepDetected = stepDetection.detectStepByPeakDifference()
                } else {
                    stepDetected = false
                }
            }
        }

        private fun updateButterworthParameters(currentTime: Long) {
            // Update lastDiffMagnitude with the current local extrema difference
            configuration.lastDiffMagnitude = configuration.localExtremaDifference
            
            // Update lastStepTimeDiff with time difference between consecutive steps
            if (configuration.lastDetectedStepTime > 0) {
                val timeDiff = currentTime - configuration.lastDetectedStepTime
                configuration.lastStepTimeDiff = BigDecimal.valueOf(timeDiff.toDouble())
            }
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
    }
}
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
import java.text.DecimalFormat
import java.util.*
import kotlin.math.pow

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
            setDrawIcons(true) // Enable icons for false steps
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

                    // Check if autocorrelation algorithm is enabled
                    if (clonedConfig.autocorcAlg) {
                        context.processAutocorrelationAlgorithm()
                    } else {
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
                setDrawValues(true)
                setCircleColor(processed.color)
                setDrawCircles(true)
                circleRadius = 4f
                lineWidth = 2f
                setDrawIcons(!isDrawIconsEnabled) // Enable icons for false steps
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

    private data class Sample(
        val magnitude: Double,
        val timestamp: Double
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
        val rotation = SensorData()
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
        
        // For false step detection
        private val vectorMagn = FloatArray(3)
        private var resultMagn = 0f
        private var resultMagnPrev = 0f
        private val sumResMagn = mutableListOf<Float>()
        private var countFour = 0
        private val resLast4Steps = mutableListOf<Float>()
        private var falseStep = false
        
        // For algorithm 3 (time filtering)
        private val last3Acc = mutableListOf<BigDecimal>()
        private var s = 0
        private var sOld = 0
        private var cutoffSelected = 3.0 // Default cutoff frequency for Butterworth filter
        private var oldMagn = 0.0
        
        // For autocorrelation algorithm
        private val recordList = mutableListOf<Sample>()
        private var startingPoint = 0L

        init {
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

        fun processAutocorrelationAlgorithm() {
            val keys = jsonObject.keys()
            recordList.clear()
            
            while (keys.hasNext()) {
                val key = keys.next()
                val eventJson = jsonObject.getJSONObject(key)
                
                if (eventJson.has("acceleration_x")) {
                    val x = eventJson.getString("acceleration_x").toDouble()
                    val y = eventJson.getString("acceleration_y").toDouble()
                    val z = eventJson.getString("acceleration_z").toDouble()
                    
                    val magnitude = Math.sqrt(x * x + y * y + z * z)
                    
                    val sample = if (recordList.isEmpty()) {
                        startingPoint = key.toLong()
                        Sample(magnitude, key.toDouble())
                    } else {
                        val totalDiffInMillis = key.toLong() - startingPoint
                        val diffSeconds = DecimalFormat("#").format(totalDiffInMillis / 1000)
                        val diffMillis = totalDiffInMillis % 1000
                        val mill = diffMillis.toDouble() / 1000
                        val secMilPassed = diffSeconds.toDouble() + mill
                        Sample(magnitude, secMilPassed)
                    }
                    
                    recordList.add(sample)
                }
            }
            
            // Here you would process the autocorrelation algorithm
            // For now, we'll just count the steps based on the record list size
            // You should implement the actual autocorrelation logic here
            stepsCount = recordList.size / 10 // Placeholder logic
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

            // Apply filters regardless of real-time mode for step detection
            when (configuration.filterType) {
                0 -> applyBagileviFilter(instant)
                1 -> applyLowPassFilter(instant)
                2 -> applyNoFilter(instant)
                3 -> applyRotationMatrixFilter(instant)
                4 -> applyButterworthFilter(instant)
            }

            if (configuration.recognitionAlgorithm == 1 && configuration.filterType != 4) {
                applyIntersectionCorrection(instant)
            }

            if (stepDetected) {
                // Check for false steps based on time filtering (algorithm 2)
                if (configuration.recognitionAlgorithm == 2) {
                    checkTimeFilteringFalseStep()
                }
                
                // Check for false steps based on Butterworth filter
                if (configuration.filterType == 4) {
                    checkButterworthFalseStep()
                }
                
                // Check for false steps based on magnetometer data (non-real-time mode)
                if (configuration.realTimeMode == 1) {
                    checkFalseStep()
                }
                
                if (!falseStep) {
                    stepsCount++
                    // Store chart entry instead of adding directly to chart
                    chartEntries.add(Entry(counter.toFloat(), lastAccelerationMagnitude!!.toFloat()))
                } else {
                    falseStep = false
                    // Could mark false steps in the chart with icons
                }
            } else {
                // Add magnetometer values for false step detection
                if (configuration.realTimeMode == 1) {
                    sumResMagn.add(resultMagn)
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
                
                // Calculate magnetometer magnitude for false step detection
                if (configuration.realTimeMode == 1) {
                    val magn = magnetometer.rawValues
                    vectorMagn[0] = magn[0].toFloat()
                    vectorMagn[1] = magn[1].toFloat()
                    vectorMagn[2] = magn[2].toFloat()
                    resultMagn = kotlin.math.sqrt(
                        kotlin.math.sqrt(vectorMagn[0] * vectorMagn[0] + vectorMagn[1] * vectorMagn[1]).pow(2) + vectorMagn[2] * vectorMagn[2]
                    )
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
                if (configuration.realTimeMode == 1) {
                    val magn = magnetometer.rawValues
                    vectorMagn[0] = magn[0].toFloat()
                    vectorMagn[1] = magn[1].toFloat()
                    vectorMagn[2] = magn[2].toFloat()
                    resultMagn = kotlin.math.sqrt(
                        kotlin.math.sqrt(vectorMagn[0] * vectorMagn[0] + vectorMagn[1] * vectorMagn[1]).pow(2) + vectorMagn[2] * vectorMagn[2]
                    )
                }

                val filtered = filters.butterworthFilter(accelerometer.rawValues, cutoffSelected, samplingRateValue)
                accelerometer.filteredValues = filtered
                accelerometer.filteredResultant = calculations.resultant(filtered)

                val detectionResult = when (configuration.recognitionAlgorithm) {
                    2 -> keyPointDetection.recognizeLocalExtremaTimeFiltering(accelerometer.filteredResultant, instant)
                    1 -> keyPointDetection.recognizeLocalExtremaRealtime(accelerometer.filteredResultant, instant)
                    else -> false
                }

                if (detectionResult) {
                    stepDetected = stepDetection.detectStepByPeakDifference()
                } else {
                    stepDetected = false
                }
            }
        }
        
        private fun checkFalseStep() {
            if (configuration.realTimeMode == 1) {
                val averageRes = calculations.sumOfMagnet(sumResMagn) / sumResMagn.size
                sumResMagn.clear()

                if (countFour < 4) {
                    resLast4Steps.add(averageRes)
                    countFour++
                } else {
                    falseStep = calculations.checkFalseStep(resLast4Steps, averageRes)
                    resLast4Steps.removeAt(0)
                    resLast4Steps.add(averageRes)
                    countFour = 0
                }
                resultMagnPrev = averageRes
            }
        }
        
        private fun checkTimeFilteringFalseStep() {
            if (configuration.recognitionAlgorithm == 2) {
                val aNPeakValley = configuration.lastStepFirstPhaseTime - configuration.lastStepSecondPhaseTime
                val aN1PeakValley = configuration.exMax!! - configuration.exMin!!
                val diff = kotlin.math.abs(aNPeakValley.toDouble() - aN1PeakValley.toDouble())

                falseStep = (diff == 0.0)
            }
        }

        /*private fun checkButterworthFalseStep() {
            if (configuration.filterType == 4) {
                val magnNPeakValley = configuration.lastLocalMaxAccel.toDouble() -
                                     configuration.lastLocalMinAccel.toDouble()

                if (oldMagn != 0.0) {
                    val diffMagn = magnNPeakValley - oldMagn
                    val fi2 = kotlin.math.abs(kotlin.math.ceil(samplingRateValue / kotlin.math.abs(diffMagn) * 2))
                    val aNPeakValley = configuration.lastStepFirstPhaseTime - configuration.lastStepSecondPhaseTime
                    val aN1PeakValley = configuration.exMax!! - configuration.exMin!!
                    val timeDifference = kotlin.math.abs(aNPeakValley.toDouble() - aN1PeakValley.toDouble())

                    if (!falseStep) {
                        if (fi2 > 180 && timeDifference < 2.1) {
                            falseStep = true
                        }
                    }

                    cutoffSelected = when {
                        fi2 < 183 -> {
                            if (timeDifference > 40) samplingRateValue / 20.0
                            else samplingRateValue / 7.0
                        }
                        fi2 < 81 -> {
                            if (timeDifference > 20) samplingRateValue / 15.0
                            else samplingRateValue / 6.0
                        }
                        else -> 0.0
                    }
                }

                oldMagn = magnNPeakValley
            }
        }*/

        private fun checkButterworthFalseStep() {
            if (configuration.filterType == 4) {
                val magnNPeakValley = configuration.lastLocalMaxAccel.toDouble() -
                        configuration.lastLocalMinAccel.toDouble()

                if (oldMagn != 0.0) {
                    val diffMagn = kotlin.math.abs(magnNPeakValley - oldMagn)
                    val timeDiff = kotlin.math.abs(
                        (configuration.lastStepFirstPhaseTime - configuration.lastStepSecondPhaseTime).toDouble() -
                                (configuration.exMax!! - configuration.exMin!!).toDouble()
                    )

                    if (!falseStep && diffMagn < 0.4 && timeDiff < 1.8) {
                        falseStep = true
                    }

                    val base = samplingRateValue / 6.0
                    val adjustment = if (diffMagn < 0.8) 0.5 else 1.0
                    cutoffSelected = (base * adjustment).coerceIn(1.5, 10.0)
                }

                oldMagn = magnNPeakValley
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
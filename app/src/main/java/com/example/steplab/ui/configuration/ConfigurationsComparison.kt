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
import com.example.steplab.ui.main.MainActivity
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
import java.util.*
import androidx.activity.addCallback

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

        onBackPressedDispatcher.addCallback(this){
            startActivity(
                Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    private fun setupViews() {
        chart = findViewById(R.id.line_chart)
        recyclerView = findViewById(R.id.recycler_view)
        startButton = findViewById(R.id.start_new_comparison)
        selectTestButton = findViewById(R.id.select_another_test)
        progressBar = findViewById(R.id.progress_bar)

        startButton.setOnClickListener {
            startActivity(
                Intent(applicationContext, SelectConfigurationsToCompare::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
        selectTestButton.setOnClickListener {
            startActivity(
                Intent(applicationContext, SelectTest::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra("configurations", configurations)
            )
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
        val keysList = jsonObject.keys().asSequence().toList().sortedBy { it.toLong() }
        var index = 0

        val baseLine = LineDataSet(null, "Magnitude of Acceleration").apply {
            color = Color.RED
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            setDrawIcons(true)
        }
        chartData.addDataSet(baseLine)
        chart.data = chartData

        for (key in keysList) {
            val obj = jsonObject.getJSONObject(key)
            if (obj.has("acceleration_magnitude")) {
                chartData.addEntry(
                    Entry(index.toFloat(), obj.getDouble("acceleration_magnitude").toFloat()),
                    0
                )
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
            stepCounts = stepDataset
        ) { selectedIndex ->
            // Highlight the corresponding chart series (dataset + 1; 0 is baseline)
            highlightChart(selectedIndex)
        }
        recyclerView.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(this@ConfigurationsComparison)
            setHasFixedSize(true)
        }
    }

    private fun highlightChart(selectedIndex: Int) {
        val total = chartData.dataSetCount
        if (total <= 1) return // only baseline exists

        for (i in 0 until total) {
            val set = chartData.getDataSetByIndex(i) as LineDataSet
            when (i) {
                0 -> { // baseline
                    set.lineWidth = 2f
                    set.setDrawCircles(false)
                }
                selectedIndex + 1 -> { // selected configuration (offset +1 because 0 is baseline)
                    set.lineWidth = 3f
                    set.setDrawCircles(true)
                    set.circleRadius = 4f
                }
                else -> {
                    set.lineWidth = 1f
                    set.setDrawCircles(true)
                    set.circleRadius = 3f
                }
            }
        }
        chart.data = chartData
        chartData.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
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

                    if (clonedConfig.autocorcAlg) {
                        context.processAutocorrelationAlgorithm()
                    } else {
                        val keys = jsonObject.keys()
                        var processedEvents = 0

                        while (keys.hasNext()) {
                            val key = keys.next()
                            val eventJson = jsonObject.getJSONObject(key)
                            context.myOnSensorChanged(key.toLong(), eventJson)

                            processedEvents++
                            if (processedEvents % 100 == 0) {
                                kotlinx.coroutines.yield()
                            }
                        }
                    }

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

            withContext(Dispatchers.Main) {
                updateUIWithProcessedConfigurations(processedConfigurations)
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun updateUIWithProcessedConfigurations(processedConfigurations: List<ProcessedConfiguration>) {
        for ((index, processed) in processedConfigurations.withIndex()) {
            val configDataSet = LineDataSet(processed.chartEntries, "${index + 1}").apply {
                color = processed.color
                setDrawValues(true)
                setCircleColor(processed.color)
                setDrawCircles(true)
                circleRadius = 4f
                lineWidth = 2f
                setDrawIcons(!isDrawIconsEnabled) // keep icons for "false steps" if used
            }

            myLines.add(configDataSet)
            chartData.addDataSet(configDataSet)
            chart.data = chartData

            dataset.add(processed.configuration)
            colorDataset.add(processed.color)
            stepDataset.add(processed.stepCount)
        }

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

    private inner class ConfigurationContext(
        val configuration: Configuration,
        val index: Int
    ) {
        private val stepDetectionProcessor = StepDetectionProcessor(configuration)

        var stepsCount: Int
            get() = stepDetectionProcessor.stepsCount
            private set(value) {}

        val chartEntries: MutableList<Entry>
            get() = stepDetectionProcessor.chartEntries

        fun processAutocorrelationAlgorithm() {
            stepDetectionProcessor.processAutocorrelationAlgorithm(jsonObject)
        }

        fun myOnSensorChanged(instant: Long, eventJson: JSONObject) {
            stepDetectionProcessor.processBatchSensorData(instant, eventJson)
        }
    }
}

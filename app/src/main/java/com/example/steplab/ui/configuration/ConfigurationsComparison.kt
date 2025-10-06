package com.example.steplab.ui.configuration

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.ui.main.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.*
import com.example.steplab.data.local.*
import com.example.steplab.ui.test.SavedTests
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
    private lateinit var saveButton: Button
    private lateinit var buttonsLayout: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val dataset = arrayListOf<Configuration>()
    private val colorDataset = arrayListOf<Int>()
    private val stepDataset = arrayListOf<Int>()
    private val myLines = arrayListOf<LineDataSet>()
    private lateinit var chartData: LineData
    private var counter = 0
    private var isViewMode = false // True when viewing saved configurations

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.configurations_comparison)

        configurations = intent.getSerializableExtra("configurations") as ArrayList<Configuration>
        val testId = intent.getSerializableExtra("test") as String
        isViewMode = intent.getBooleanExtra("viewMode", false)

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
            if (isViewMode) {
                // If in view mode, go back to SavedTests activity
                startActivity(
                    Intent(applicationContext, SavedTests::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            } else {
                // Normal mode, go back to MainActivity
                startActivity(
                    Intent(applicationContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            }
        }
    }

    private fun setupViews() {
        chart = findViewById(R.id.line_chart)
        recyclerView = findViewById(R.id.recycler_view)
        startButton = findViewById(R.id.start_new_comparison)
        selectTestButton = findViewById(R.id.select_another_test)
        saveButton = findViewById(R.id.save_comparison)
        buttonsLayout = findViewById(R.id.buttons_layout)
        progressBar = findViewById(R.id.progress_bar)

        // Hide entire buttons layout if in view mode
        if (isViewMode) {
            buttonsLayout.visibility = View.GONE
        }

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
        saveButton.setOnClickListener {
            showSaveDialog()
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

    private fun estimateFsFromJson(json: JSONObject): Int {
        val keys = json.keys().asSequence().map { it.toLong() }.sorted().toList()
        if (keys.size < 3) return 50
        val dtMsAvg = (keys.last() - keys.first()).toDouble() / (keys.size - 1).coerceAtLeast(1)
        val fs = (1000.0 / dtMsAvg).toInt().coerceIn(30, 200)
        return fs
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
                        val fsBatch = estimateFsFromJson(jsonObject)
                        context.setFsForBatch(fsBatch)
                        context.processAutocorrelationAlgorithm()
                    } else {
                        // Sort keys by timestamp to ensure temporal order
                        val keysSorted = jsonObject.keys().asSequence().toList().sortedBy { it.toLong() }
                        var processedEvents = 0

                        for (key in keysSorted) {
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

        fun setFsForBatch(fs: Int) {
            stepDetectionProcessor.setFixedFsForBatch(fs)
        }
    }

    private fun showSaveDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.enter_unique_name)
            setPadding(50, 20, 50, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_configuration_comparison))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Save the configuration comparison
                saveConfigurationComparison(name)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveConfigurationComparison(name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = MainActivity.getDatabase()
                
                // Check if name already exists
                val existing = database?.databaseDao()?.getSavedConfigurationComparisonByName(name)
                if (existing != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ConfigurationsComparison, getString(R.string.name_already_exists), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Serialize configurations
                val configurationsJson = ConfigurationSerializer.serializeConfigurations(configurations)

                // Create saved entity
                val savedComparison = EntitySavedConfigurationComparison(
                    name = name,
                    testId = testApp.testId,
                    testName = testApp.fileName ?: "Unknown Test",
                    configurationsJson = configurationsJson
                )

                // Save to database
                database?.databaseDao()?.insertSavedConfigurationComparison(savedComparison)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConfigurationsComparison, getString(R.string.comparison_saved), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ConfigurationsComparison, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

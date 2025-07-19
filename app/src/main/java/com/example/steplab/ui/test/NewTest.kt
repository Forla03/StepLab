package com.example.steplab.ui.test

import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.steplab.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.Calculations
import com.example.steplab.data.local.EntityTest
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal
import java.util.LinkedHashMap

class NewTest : AppCompatActivity(), SensorEventListener {

    private lateinit var chartLine: LineDataSet
    private lateinit var chartData: LineData
    private lateinit var chart: LineChart
    private lateinit var startStopButton: Button

    private var testData = LinkedHashMap<String, JSONObject>()
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var timestamp: Long = 0
    private var counter = 0
    private val calculations = Calculations()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_test)

        chart = findViewById(R.id.line_chart)
        startStopButton = findViewById(R.id.new_test_button)

        chart.description.isEnabled = false
        chart.xAxis.setDrawLabels(false)

        chartLine = LineDataSet(null, "Magnitude of Acceleration").apply {
            color = Color.RED
            setDrawCircles(false)
            setDrawValues(false)
        }

        chartData = LineData().apply { addDataSet(chartLine) }
        chart.data = chartData

        chartData.addEntry(Entry(0f, 0f), 0)
        chartData.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
        chart.moveViewToX(chartData.entryCount.toFloat())

        startStopButton.setText(R.string.start_new_test)
        startStopButton.setOnClickListener { startRecording() }
    }

    private fun startRecording() {
        startStopButton.text = getString(R.string.stop_new_test)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

        listOf(accelerometer, magnetometer, gravitySensor).forEach { sensor ->
            sensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        }

        startStopButton.setOnClickListener { stopRecording() }
    }

    private fun stopRecording() {
        startStopButton.text = getString(R.string.save_new_test)
        sensorManager?.unregisterListener(this)

        val dialogView = layoutInflater.inflate(R.layout.number_of_steps, null)
        val dialogBuilder = AlertDialog.Builder(this).setView(dialogView)
        val dialog = dialogBuilder.create()

        val stepCountInput: EditText = dialogView.findViewById(R.id.number_of_steps)
        val notesInput: EditText = dialogView.findViewById(R.id.additional_notes)
        val saveButton: Button = dialogView.findViewById(R.id.save_new_test)

        saveButton.setOnClickListener {
            val stepCount = stepCountInput.text.toString()
            if (stepCount.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        MainActivity.getDatabase()?.let { db ->
                            val entity = EntityTest(
                                fileName = System.currentTimeMillis().toString(),
                                numberOfSteps = stepCount.toIntOrNull() ?: 0,
                                testValues = JSONObject(testData as Map<*, *>).toString(),
                                additionalNotes = notesInput.text.toString()
                            )
                            db.databaseDao()?.insertTest(entity)

                            Toast.makeText(this@NewTest, getString(R.string.new_test_saved), Toast.LENGTH_SHORT).show()
                            startActivity(Intent(applicationContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@NewTest, "Errore nel salvataggio del test", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.number_steps_error), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onSensorChanged(event: SensorEvent) {
        timestamp = System.currentTimeMillis()
        val values = event.values.map { BigDecimal.valueOf(it.toDouble()) }

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val magnitude = calculations.resultant(values.toTypedArray())
                chartData.addEntry(Entry(counter.toFloat(), magnitude.toFloat()), 0)

                val json = JSONObject().apply {
                    put("acceleration_x", values[0].toPlainString())
                    put("acceleration_y", values[1].toPlainString())
                    put("acceleration_z", values[2].toPlainString())
                    put("acceleration_magnitude", magnitude.toPlainString())
                }

                testData[timestamp.toString()] = json
                updateChart()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val json = JSONObject().apply {
                    put("magnetometer_x", values[0].toPlainString())
                    put("magnetometer_y", values[1].toPlainString())
                    put("magnetometer_z", values[2].toPlainString())
                }

                testData[timestamp.toString()] = json
            }

            Sensor.TYPE_GRAVITY -> {
                val json = JSONObject().apply {
                    put("gravity_x", values[0].toPlainString())
                    put("gravity_y", values[1].toPlainString())
                    put("gravity_z", values[2].toPlainString())
                }

                testData[timestamp.toString()] = json
            }
        }
    }

    private fun updateChart() {
        chartData.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(MainActivity.NUMBER_OF_DOTS_IN_GRAPH.toFloat())
        chart.moveViewToX(chartData.entryCount.toFloat())
        counter++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    @Deprecated(message = "Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }
}

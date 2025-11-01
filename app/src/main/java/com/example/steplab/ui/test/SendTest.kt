package com.example.steplab.ui.test

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.ui.main.MainActivity
import com.example.steplab.R
import com.example.steplab.ui.main.StepLabApplication
import com.example.steplab.utils.JsonToCsvConverter
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class SendTest : AppCompatActivity() {

    private lateinit var sendTestButton: Button
    private lateinit var recyclerView: RecyclerView

    private val dataset = ArrayList<CardTest>()
    private var filesToShare = ArrayList<Uri>()
    private lateinit var adapter: AdapterForSendTestCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.send_test)

        recyclerView = findViewById(R.id.recycler_view)
        sendTestButton = findViewById(R.id.send_test)

        adapter = AdapterForSendTestCard(this, dataset, 0, sendTestButton)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        lifecycleScope.launch {
            try {
                val testList = StepLabApplication.database.databaseDao()?.getAllTests()
                testList?.forEach { test ->
                    dataset.add(
                        CardTest(
                            test.testId.toString(),
                            test.testValues ?: "",
                            test.numberOfSteps ?: 0,
                            test.additionalNotes ?: "",
                            test.fileName ?: ""
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        sendTestButton.setOnClickListener {
            // Check if any tests are selected
            val selectedTests = dataset.filter { it.selected }
            if (selectedTests.isEmpty()) {
                return@setOnClickListener
            }

            // Show format selection dialog
            showExportFormatDialog()
        }
    }

    private fun showExportFormatDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.export_format_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.format_radio_group)
        val radioJson = dialogView.findViewById<RadioButton>(R.id.radio_json)
        val radioCsv = dialogView.findViewById<RadioButton>(R.id.radio_csv)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnExport = dialogView.findViewById<Button>(R.id.btn_export)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnExport.setOnClickListener {
            val exportAsCsv = radioCsv.isChecked
            dialog.dismiss()
            exportTests(exportAsCsv)
        }

        dialog.show()
    }

    private fun exportTests(exportAsCsv: Boolean) {
        filesToShare.clear()

        dataset.forEach { card ->
            if (card.selected) {
                val file = File(applicationContext.filesDir, card.filePathName)
                
                try {
                    if (exportAsCsv) {
                        // Export as CSV
                        exportTestAsCsv(card, file)
                    } else {
                        // Export as JSON
                        exportTestAsJson(card, file)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (filesToShare.isNotEmpty()) {
            val mimeType = if (exportAsCsv) "text/csv" else "application/json"
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesToShare)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share test files via"))
        }
    }

    private fun exportTestAsJson(card: CardTest, existingFile: File) {
        // Create JSON file with .json extension
        // Use testId to ensure unique filenames
        val baseName = card.filePathName.removeSuffix(".json").removeSuffix(".txt")
        val jsonFileName = "${baseName}_${card.testId}.json"
        val jsonFile = File(applicationContext.filesDir, jsonFileName)
        
        // Build JSON structure
        val exportData = JSONObject().apply {
            put("number_of_steps", card.numberOfSteps)
            put("additional_notes", card.additionalNotes)
            put("test_values", JSONObject(card.testValues))
        }
        
        // Write JSON file
        jsonFile.writeText(exportData.toString())

        val uri = FileProvider.getUriForFile(
            applicationContext,
            "com.example.steplab.fileprovider",
            jsonFile
        )
        filesToShare.add(uri)
    }

    private fun exportTestAsCsv(card: CardTest, existingFile: File) {
        // Create CSV file with .csv extension
        // Use testId to ensure unique filenames
        val baseName = card.filePathName.removeSuffix(".json").removeSuffix(".txt")
        val csvFileName = "${baseName}_${card.testId}.csv"
        val csvFile = File(applicationContext.filesDir, csvFileName)

        // Build JSON structure (need full structure for conversion)
        val jsonData = JSONObject().apply {
            put("number_of_steps", card.numberOfSteps)
            put("additional_notes", card.additionalNotes)
            put("test_values", JSONObject(card.testValues))
        }

        // Convert to CSV
        val converter = JsonToCsvConverter()
        val result = converter.convertJsonToCsv(jsonData.toString())

        if (result.success && result.csvString != null) {
            csvFile.writeText(result.csvString)
            
            val uri = FileProvider.getUriForFile(
                applicationContext,
                "com.example.steplab.fileprovider",
                csvFile
            )
            filesToShare.add(uri)
        } else {
            // Fallback to JSON if CSV conversion fails
            exportTestAsJson(card, existingFile)
        }
    }

    @Deprecated(message = "Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }
}

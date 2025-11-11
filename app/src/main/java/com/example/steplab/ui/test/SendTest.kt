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
                var orphanedCount = 0
                
                testList?.forEach { test ->
                    // Verify file integrity - only add tests with existing files
                    val file = File(applicationContext.filesDir, test.fileName)
                    if (file.exists()) {
                        // Load only metadata - sensor data will be read from file when needed
                        dataset.add(
                            CardTest(
                                test.testId.toString(),
                                "", // testValues no longer stored in database
                                test.numberOfSteps ?: 0,
                                test.additionalNotes ?: "",
                                test.fileName ?: ""
                            )
                        )
                    } else {
                        // File missing - orphaned database record
                        orphanedCount++
                        android.util.Log.w(
                            "SendTest",
                            "Orphaned test found: testId=${test.testId}, missing file: ${test.fileName}"
                        )
                        // Optionally: delete orphaned record from database
                        // StepLabApplication.database.databaseDao()?.deleteTest(test.testId)
                    }
                }
                
                adapter.notifyDataSetChanged()
                
                // Notify user if orphaned tests were found
                if (orphanedCount > 0) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@SendTest,
                            "Warning: $orphanedCount test(s) have missing files and were not loaded",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
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
        var failedExports = 0

        dataset.forEach { card ->
            if (card.selected) {
                val file = File(applicationContext.filesDir, card.filePathName)
                
                // Verify file exists before attempting export
                if (!file.exists()) {
                    failedExports++
                    android.util.Log.e("SendTest", "Cannot export test ${card.testId}: file not found: ${card.filePathName}")
                    return@forEach
                }
                
                try {
                    if (exportAsCsv) {
                        // Export as CSV
                        exportTestAsCsv(card, file)
                    } else {
                        // Export as JSON
                        exportTestAsJson(card, file)
                    }
                } catch (e: Exception) {
                    failedExports++
                    android.util.Log.e("SendTest", "Failed to export test ${card.testId}", e)
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
        } else if (failedExports > 0) {
            // Show error if no files were successfully exported
            android.widget.Toast.makeText(
                this,
                "Failed to export $failedExports test(s). Files may be missing.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportTestAsJson(card: CardTest, existingFile: File) {
        // Create JSON file with .json extension
        // Use testId to ensure unique filenames
        val baseName = card.filePathName.removeSuffix(".json").removeSuffix(".txt")
        val jsonFileName = "${baseName}_${card.testId}.json"
        val jsonFile = File(applicationContext.filesDir, jsonFileName)
        
        // Read the complete test data from the original file
        val sourceFile = File(applicationContext.filesDir, card.filePathName)
        if (!sourceFile.exists()) {
            return
        }
        
        try {
            val jsonContent = sourceFile.readText()
            jsonFile.writeText(jsonContent)

            val uri = FileProvider.getUriForFile(
                applicationContext,
                "com.example.steplab.fileprovider",
                jsonFile
            )
            filesToShare.add(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun exportTestAsCsv(card: CardTest, existingFile: File) {
        // Create CSV file with .csv extension
        // Use testId to ensure unique filenames
        val baseName = card.filePathName.removeSuffix(".json").removeSuffix(".txt")
        val csvFileName = "${baseName}_${card.testId}.csv"
        val csvFile = File(applicationContext.filesDir, csvFileName)

        // Read the complete test data from the original file
        val sourceFile = File(applicationContext.filesDir, card.filePathName)
        if (!sourceFile.exists()) {
            return
        }

        try {
            val jsonContent = sourceFile.readText()

            // Convert to CSV
            val converter = JsonToCsvConverter()
            val result = converter.convertJsonToCsv(jsonContent)

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
        } catch (e: Exception) {
            e.printStackTrace()
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

package com.example.steplab.ui.main

import android.content.Intent
import android.database.CursorWindow
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.steplab.R
import com.example.steplab.ui.configuration.SelectConfigurationsToCompare
import com.example.steplab.ui.test.LiveTesting
import com.example.steplab.ui.test.NewTest
import com.example.steplab.ui.test.SavedTests
import com.example.steplab.ui.test.SendTest
import com.example.steplab.utils.CsvToJsonConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var btnEnterConfiguration: Button
    private lateinit var btnRegisterNewTest: Button
    private lateinit var btnCompareConfigurations: Button
    private lateinit var btnImportTest: Button
    private lateinit var btnSendTest: Button
    private lateinit var btnSavedTests: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnEnterConfiguration = findViewById(R.id.enter_configuration)
        btnRegisterNewTest = findViewById(R.id.register_new_test)
        btnCompareConfigurations = findViewById(R.id.compare_configurations)
        btnImportTest = findViewById(R.id.import_test)
        btnSendTest = findViewById(R.id.send_test)
        btnSavedTests = findViewById(R.id.saved_tests)

        // Disable actions that require existing tests
        setHasTests(false)
        setHasSavedTests(false)

        // Enable buttons only if tests exist (run on IO)
        lifecycleScope.launch {
            val hasAnyTests = withContext(Dispatchers.IO) {
                try {
                    StepLabApplication.database.databaseDao()?.getAllTests()?.isNotEmpty() == true
                } catch (_: Exception) {
                    false
                }
            }
            val hasAnySavedTests = withContext(Dispatchers.IO) {
                try {
                    StepLabApplication.database.databaseDao()?.getAllSavedConfigurationComparisons()?.isNotEmpty() == true
                } catch (_: Exception) {
                    false
                }
            }
            setHasTests(hasAnyTests)
            setHasSavedTests(hasAnySavedTests)
        }

        // Optional: enlarge CursorWindow buffer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                field.isAccessible = true
                field.set(null, 100 * 1024 * 1024) // 100MB
            } catch (_: Exception) { /* no-op */ }
        }

        // Navigation
        btnEnterConfiguration.setOnClickListener {
            startActivity(Intent(this, LiveTesting::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        btnRegisterNewTest.setOnClickListener {
            startActivity(Intent(this, NewTest::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        btnCompareConfigurations.setOnClickListener {
            startActivity(Intent(this, SelectConfigurationsToCompare::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        btnSendTest.setOnClickListener {
            startActivity(Intent(this, SendTest::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        btnSavedTests.setOnClickListener {
            startActivity(Intent(this, SavedTests::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }

        // Import multiple files: supports both .txt (native format) and .csv
        btnImportTest.setOnClickListener {
            multipleFilesImportLauncher.launch("*/*")
        }

        // Back press: ask before exiting to home
        onBackPressedDispatcher.addCallback(this) {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(getString(R.string.exit))
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    // Send app to background
                    moveTaskToBack(true)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    /**
     * Pick multiple files (CSV or TXT) and import them.
     * - CSV files are automatically converted to JSON format
     * - TXT files are imported as-is (must already be in JSON format)
     * - Default values: steps = 50, notes = filename
     */
    private val multipleFilesImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult

        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Importing Files")
            .setMessage("Processing 0 of ${uris.size} files...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val csvConverter = CsvToJsonConverter()
                    val importResults = mutableListOf<ImportResult>()

                    for ((index, uri) in uris.withIndex()) {
                        // Update progress message on UI thread
                        withContext(Dispatchers.Main) {
                            progressDialog.setMessage("Processing ${index + 1} of ${uris.size} files...\n${getFileNameFromUri(uri)}")
                        }
                        
                        android.util.Log.d("MainActivity", "Starting import ${index + 1}/${uris.size}: ${getFileNameFromUri(uri)}")

                        val result = runCatching {
                        var createdFile: File? = null
                        var insertedTestId: Int? = null
                        
                        try {
                            val fileName = getFileNameFromUri(uri)
                            val content = contentResolver.openInputStream(uri)?.use { input ->
                                BufferedReader(InputStreamReader(input)).use { br ->
                                    buildString {
                                        var line: String?
                                        while (br.readLine().also { line = it } != null) {
                                            append(line).append('\n')
                                        }
                                    }
                                }
                            } ?: error("Cannot open file: $fileName")

                            // Detect file type and process accordingly
                            val (jsonContent, actualSteps) = if (fileName.endsWith(".csv", ignoreCase = true)) {
                                // CSV file: convert to JSON
                                contentResolver.openInputStream(uri)?.use { input ->
                                    val conversionResult = csvConverter.convertCsvToJson(
                                        inputStream = input,
                                        additionalNotes = fileName, // Use filename as default notes
                                        numberOfStepsOverride = 50   // Default to 50 steps
                                    )
                                    if (!conversionResult.success || conversionResult.jsonString.isNullOrEmpty()) {
                                        throw Exception(conversionResult.errorMessage ?: "CSV conversion failed")
                                    }
                                    
                                    // Extract test_values from wrapper
                                    val root = JSONObject(conversionResult.jsonString!!)
                                    val testValuesStr = root.optJSONObject("test_values")?.toString() ?: "{}"
                                    
                                    Pair(conversionResult.jsonString!!, 50) // Always use 50 as default
                                } ?: throw Exception("Cannot re-open CSV file")
                            } else {
                                // JSON/TXT file: use as-is (support both .json and .txt for backward compatibility)
                                try {
                                    val json = JSONObject(content)
                                    // Validate it has the required structure
                                    if (!json.has("test_values")) {
                                        throw Exception("Invalid format: missing 'test_values'")
                                    }
                                    val steps = json.optInt("number_of_steps", 50)
                                    Pair(content, steps)
                                } catch (e: Exception) {
                                    throw Exception("Invalid JSON format: ${e.message}")
                                }
                            }

                            // Parse the final JSON
                            val json = JSONObject(jsonContent)
                            val numberOfSteps = json.optInt("number_of_steps", actualSteps)
                            val additionalNotes = json.optString("additional_notes", fileName)

                        // Persist a copy inside internal storage
                        val timestamp = System.currentTimeMillis()
                        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
                        
                        // Generate unique filename using timestamp + milliseconds + UUID to prevent collisions
                        val uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8)
                        val internalFileName = String.format(
                            "%04d-%02d-%02d_%02d:%02d:%02d_%03d_%s.json",
                            calendar.get(java.util.Calendar.YEAR),
                            calendar.get(java.util.Calendar.MONTH) + 1,
                            calendar.get(java.util.Calendar.DAY_OF_MONTH),
                            calendar.get(java.util.Calendar.HOUR_OF_DAY),
                            calendar.get(java.util.Calendar.MINUTE),
                            calendar.get(java.util.Calendar.SECOND),
                            timestamp % 1000,  // milliseconds
                            uniqueId           // unique identifier
                        )
                        
                        // Additional safety: check if file exists (should never happen with UUID)
                        createdFile = File(applicationContext.filesDir, internalFileName)
                        if (createdFile!!.exists()) {
                            throw Exception("File already exists: $internalFileName (this should never happen)")
                        }
                        
                        // Write file to disk
                        createdFile!!.writeText(jsonContent)
                        android.util.Log.d("MainActivity", "File written: $internalFileName (${createdFile.length()} bytes)")

                        // Save only metadata in DB - sensor data stays in file
                        val entity = com.example.steplab.data.local.EntityTest(
                            numberOfSteps = numberOfSteps,
                            additionalNotes = additionalNotes,
                            fileName = internalFileName,
                            recordedAt = timestamp
                        )
                        StepLabApplication.database.databaseDao()?.insertTest(entity)
                        android.util.Log.i("MainActivity", "Test imported successfully: $fileName → $internalFileName")

                        ImportResult(true, fileName, null)
                        
                        } catch (e: Exception) {                     
                            // Delete file if it was created
                            createdFile?.let { file ->
                                if (file.exists()) {
                                    try {
                                        file.delete()
                                        android.util.Log.w("MainActivity", "Rolled back file creation: ${file.name}")
                                    } catch (deleteException: Exception) {
                                        android.util.Log.e("MainActivity", "Failed to delete file during rollback", deleteException)
                                    }
                                }
                            }
                            
                            // Database insert uses OnConflictStrategy.ABORT now, 
                            // so it won't insert if there's a conflict
                            
                            // Re-throw exception to be caught by runCatching
                            throw e
                        }
                    }

                    importResults.add(
                        result.getOrElse { 
                            ImportResult(false, getFileNameFromUri(uri), it.message) 
                        }
                    )
                }

                importResults
            }

            // Dismiss progress dialog
            progressDialog.dismiss()

            // Show summary dialog
            val successCount = results.count { it.success }
            val failureCount = results.count { !it.success }

            val message = buildString {
                append("Import Summary:\n\n")
                append("✓ Success: $successCount file(s)\n")
                if (failureCount > 0) {
                    append("✗ Failed: $failureCount file(s)\n\n")
                    append("Failed files:\n")
                    results.filter { !it.success }.forEach {
                        append("• ${it.fileName}: ${it.error}\n")
                    }
                }
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Import Complete")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ ->
                    if (successCount > 0) {
                        setHasTests(true)
                    }
                }
                .show()
            } catch (e: Exception) {
                // Ensure dialog is dismissed even on error
                progressDialog.dismiss()
                
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Import Error")
                    .setMessage("An unexpected error occurred:\n${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "Unknown file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private data class ImportResult(
        val success: Boolean,
        val fileName: String,
        val error: String?
    )

    private fun setHasTests(has: Boolean) {
        btnCompareConfigurations.isEnabled = has
        btnSendTest.isEnabled = has
    }

    private fun setHasSavedTests(has: Boolean) {
        btnSavedTests.isEnabled = has
    }

    companion object {
        const val NUMBER_OF_DOTS_IN_GRAPH = 300
    }
}

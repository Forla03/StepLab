package com.example.steplab.ui.main

import android.content.Intent
import android.database.CursorWindow
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.steplab.R
import com.example.steplab.data.local.MyDatabase
import com.example.steplab.ui.configuration.SelectConfigurationsToCompare
import com.example.steplab.ui.test.LiveTesting
import com.example.steplab.ui.test.NewTest
import com.example.steplab.ui.test.SavedTests
import com.example.steplab.ui.test.SendTest
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

        // Initialize Room database
        databaseInstance = Room.databaseBuilder(
            applicationContext,
            MyDatabase::class.java,
            "tests.db"
        ).addMigrations(MyDatabase.MIGRATION_1_2).build()

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
                    databaseInstance?.databaseDao()?.getAllTests()?.isNotEmpty() == true
                } catch (_: Exception) {
                    false
                }
            }
            val hasAnySavedTests = withContext(Dispatchers.IO) {
                try {
                    databaseInstance?.databaseDao()?.getAllSavedConfigurationComparisons()?.isNotEmpty() == true
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

        // Import via SAF: "text/plain" (.txt with JSON content)
        btnImportTest.setOnClickListener {
            importLauncher.launch("text/plain")
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
     * Modern Activity Result API: pick a text file and import it.
     */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Read the picked file as text
                    val content = contentResolver.openInputStream(uri)?.use { input ->
                        BufferedReader(InputStreamReader(input)).use { br ->
                            buildString {
                                var line: String?
                                while (br.readLine().also { line = it } != null) {
                                    append(line).append('\n')
                                }
                            }
                        }
                    } ?: error("Cannot open selected file")

                    // Parse JSON payload
                    val json = JSONObject(content)
                    val testValues = json.getString("test_values")
                    val numberOfSteps = json.getString("number_of_steps").toInt()
                    val additionalNotes = json.optString("additional_notes", "")

                    // Persist a copy inside internal storage (keep .txt for text/plain)
                    val fileName = "imported_test_${System.currentTimeMillis()}.txt"
                    val file = File(applicationContext.filesDir, fileName)
                    file.writeText(content)

                    // Save metadata in DB
                    val entity = com.example.steplab.data.local.EntityTest(
                        testValues = testValues,
                        numberOfSteps = numberOfSteps,
                        additionalNotes = additionalNotes,
                        fileName = fileName
                    )
                    databaseInstance?.databaseDao()?.insertTest(entity)

                    true
                }
            }

            if (result.isSuccess) {
                Toast.makeText(this@MainActivity, getString(R.string.test_imported), Toast.LENGTH_SHORT).show()
                // Now that at least one test exists, enable related actions
                setHasTests(true)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Import error"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setHasTests(has: Boolean) {
        btnCompareConfigurations.isEnabled = has
        btnSendTest.isEnabled = has
    }

    private fun setHasSavedTests(has: Boolean) {
        btnSavedTests.isEnabled = has
    }

    companion object {
        private var databaseInstance: MyDatabase? = null
        const val NUMBER_OF_DOTS_IN_GRAPH = 300

        fun getDatabase(): MyDatabase? = databaseInstance
    }
}

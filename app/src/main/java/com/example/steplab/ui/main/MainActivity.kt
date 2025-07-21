package com.example.steplab

import android.content.DialogInterface
import android.content.Intent
import android.database.CursorWindow
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.steplab.data.local.MyDatabase
import com.example.steplab.ui.configuration.SelectConfigurationsToCompare
import com.example.steplab.ui.test.LiveTesting
import com.example.steplab.ui.test.NewTest
import com.example.steplab.ui.test.SendTest
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private lateinit var btnEnterConfiguration: Button
    private lateinit var btnRegisterNewTest: Button
    private lateinit var btnCompareConfigurations: Button
    private lateinit var btnImportTest: Button
    private lateinit var btnSendTest: Button

    private var chooseFileIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Room database
        databaseInstance = Room.databaseBuilder(
            applicationContext,
            MyDatabase::class.java, "tests.db"
        ).build()

        btnEnterConfiguration = findViewById(R.id.enter_configuration)
        btnRegisterNewTest = findViewById(R.id.register_new_test)
        btnCompareConfigurations = findViewById(R.id.compare_configurations)
        btnImportTest = findViewById(R.id.import_test)
        btnSendTest = findViewById(R.id.send_test)

        btnCompareConfigurations.isEnabled = false
        btnSendTest.isEnabled = false

        // Enable buttons only if tests exist
        lifecycleScope.launch {
            try {
                val testCount = databaseInstance!!.databaseDao()?.getAllTests()?.size
                if (testCount != null) {
                    if (testCount > 0) {
                        btnCompareConfigurations.isEnabled = true
                        btnSendTest.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Increase cursor buffer size (optional)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                field.isAccessible = true
                field.set(null, 100 * 1024 * 1024) // 100MB
            } catch (_: Exception) {}
        }

        btnEnterConfiguration.setOnClickListener {
            startActivity(Intent(this, LiveTesting::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }

        btnRegisterNewTest.setOnClickListener {
            startActivity(Intent(this, NewTest::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }

        btnCompareConfigurations.setOnClickListener {
            startActivity(Intent(this, SelectConfigurationsToCompare::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }

        btnImportTest.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            importLauncher.launch(intent)
        }

        btnSendTest.setOnClickListener {
            startActivity(Intent(this, SendTest::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data?.data == null) return@registerForActivityResult

        val content = StringBuilder()

        try {
            val inputStream = contentResolver.openInputStream(data.data!!)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                content.append(line).append('\n')
            }

            inputStream?.close()

            val jsonObject = JSONObject(content.toString())

            // Insert into DB using coroutine
            lifecycleScope.launch {
                try {
                    val testValues = jsonObject.getString("test_values")
                    val numberOfSteps = jsonObject.getString("number_of_steps").toInt()
                    val additionalNotes = jsonObject.getString("additional_notes")
                    val fileName = "imported_test_${System.currentTimeMillis()}.json"

                    // Create the physical file
                    val file = File(applicationContext.filesDir, fileName)
                    file.writeText(content.toString())

                    val entity = com.example.steplab.data.local.EntityTest(
                        testValues = testValues,
                        numberOfSteps = numberOfSteps,
                        additionalNotes = additionalNotes,
                        fileName = fileName
                    )

                    databaseInstance?.databaseDao()?.insertTest(entity)

                    Toast.makeText(this@MainActivity, getString(R.string.test_imported), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(applicationContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
        } catch (e: JSONException) {
            e.printStackTrace()
            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
        }
    }



    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.exit))
            .setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                startActivity(Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .show()
    }

    companion object {
        private var databaseInstance: MyDatabase? = null
        const val NUMBER_OF_DOTS_IN_GRAPH = 300

        fun getDatabase(): MyDatabase? = databaseInstance
    }
}

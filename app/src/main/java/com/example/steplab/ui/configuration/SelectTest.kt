package com.example.steplab.ui.configuration

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import com.example.steplab.ui.main.MainActivity
import com.example.steplab.ui.test.CardTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectTest : AppCompatActivity() {

    private var configurations: ArrayList<Configuration>? = null
    private val testDataset = ArrayList<CardTest>()
    private lateinit var testAdapter: AdapterForTestCard
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.select_test)

        @Suppress("UNCHECKED_CAST")
        configurations = intent.getSerializableExtra("configurations") as? ArrayList<Configuration>

        recyclerView = findViewById(R.id.recycler_view)
        testAdapter = AdapterForTestCard(testDataset, this, configurations ?: arrayListOf())

        recyclerView.apply {
            adapter = testAdapter
            layoutManager = LinearLayoutManager(this@SelectTest)
            // Keep true if items have fixed height to help the RecyclerView optimize.
            setHasFixedSize(true)
            // Disable item change animations to avoid jank on large updates.
            itemAnimator = null
        }

        // Load DB + mapping off-main, then update UI on main.
        lifecycleScope.launch {
            try {
                val items: List<CardTest> = withContext(Dispatchers.IO) {
                    val db = MainActivity.getDatabase() ?: return@withContext emptyList()
                    val rows = db.databaseDao()?.getAllTests().orEmpty()

                    // Map only the lightweight fields for list items.
                    rows.map { test ->
                        CardTest(
                            testId = test.testId.toString(),
                            testValues = "", // keep list light; load on detail if needed
                            numberOfSteps = test.numberOfSteps ?: 0,
                            additionalNotes = test.additionalNotes.orEmpty(),
                            filePathName = test.fileName.orEmpty()
                        )
                    }
                }

                // Update adapter on main thread
                testDataset.clear()
                testDataset.addAll(items)
                testAdapter.notifyDataSetChanged()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Handle back press without creating a new MainActivity instance.
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }
}

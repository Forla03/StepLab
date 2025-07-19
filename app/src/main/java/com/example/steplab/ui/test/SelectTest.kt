package com.example.steplab.ui.test

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import kotlinx.coroutines.launch

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
            setHasFixedSize(true)
        }
        lifecycleScope.launch {
            try {
                MainActivity.getDatabase()?.let { db ->

                    testDataset.clear()
                    db.databaseDao()?.getAllTests()?.forEach { test ->
                        testDataset.add(
                            CardTest(
                                test.testId.toString(),
                                test.testValues ?: "",
                                test.numberOfSteps ?: 0,
                                test.additionalNotes ?: "",
                                test.fileName ?: ""
                            )
                        )
                    }
                    testAdapter.notifyDataSetChanged()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }
}

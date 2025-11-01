package com.example.steplab.ui.test

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.R
import com.example.steplab.ui.main.StepLabApplication
import com.example.steplab.data.local.EntitySavedConfigurationComparison
import com.example.steplab.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SavedTests : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SavedTestsAdapter
    private val savedTestsList = mutableListOf<EntitySavedConfigurationComparison>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.saved_tests)

        recyclerView = findViewById(R.id.recycler_view)
        adapter = SavedTestsAdapter(savedTestsList, this)
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SavedTests)
            this.adapter = this@SavedTests.adapter
            setHasFixedSize(true)
        }

        loadSavedTests()

        onBackPressedDispatcher.addCallback(this) {
            startActivity(
                Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    private fun loadSavedTests() {
        lifecycleScope.launch {
            try {
                val savedTests = withContext(Dispatchers.IO) {
                    StepLabApplication.database.databaseDao()?.getAllSavedConfigurationComparisons() ?: emptyList()
                }
                
                savedTestsList.clear()
                savedTestsList.addAll(savedTests)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteSavedTest(savedTest: EntitySavedConfigurationComparison, position: Int) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    StepLabApplication.database.databaseDao()?.deleteSavedConfigurationComparison(savedTest.id)
                }
                
                savedTestsList.removeAt(position)
                adapter.notifyItemRemoved(position)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
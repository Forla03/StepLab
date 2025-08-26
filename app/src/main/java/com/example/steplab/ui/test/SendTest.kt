package com.example.steplab.ui.test

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.ui.main.MainActivity
import com.example.steplab.R
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
                val testList = MainActivity.getDatabase()?.databaseDao()?.getAllTests()
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
            filesToShare.clear()

            dataset.forEach { card ->
                if (card.selected) {
                    val file = File(applicationContext.filesDir, card.filePathName)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(
                            applicationContext,
                            "com.example.steplab.fileprovider",
                            file
                        )
                        filesToShare.add(uri)
                    } else {
                        try {
                            val fileWithExtension = if (card.filePathName.endsWith(".json")) {
                                File(applicationContext.filesDir, card.filePathName)
                            } else {
                                File(applicationContext.filesDir, "${card.filePathName}.json")
                            }
                            
                            val exportData = JSONObject().apply {
                                put("test_values", card.testValues)
                                put("number_of_steps", card.numberOfSteps.toString())
                                put("additional_notes", card.additionalNotes)
                            }
                            fileWithExtension.writeText(exportData.toString())
                            
                            val uri = FileProvider.getUriForFile(
                                applicationContext,
                                "com.example.steplab.fileprovider",
                                fileWithExtension
                            )
                            filesToShare.add(uri)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            if (filesToShare.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/plain"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesToShare)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share test files via"))
            }
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

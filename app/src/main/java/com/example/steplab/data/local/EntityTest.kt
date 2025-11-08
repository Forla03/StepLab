package com.example.steplab.data.local

import android.content.Context
import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.io.File

@Entity
data class EntityTest(
    @PrimaryKey(autoGenerate = true)
    val testId: Int = 0,
    val numberOfSteps: Int,
    val additionalNotes: String,
    val fileName: String,
    val recordedAt: Long = System.currentTimeMillis()
) {
    /**
     * Loads the complete test data from the JSON file stored in internal storage.
     * This method reads the file on-demand, avoiding database bloat.
     * 
     * @param context Android context to access filesDir
     * @return JSONObject containing the full test data structure, or null if file doesn't exist or is invalid
     */
    fun loadTestData(context: Context): JSONObject? {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                Log.e("EntityTest", "File not found: $fileName")
                return null
            }
            
            val content = file.readText()
            JSONObject(content)
        } catch (e: Exception) {
            Log.e("EntityTest", "Error loading test data from $fileName", e)
            null
        }
    }
    
    /**
     * Loads only the test_values portion of the JSON file.
     * 
     * @param context Android context to access filesDir
     * @return JSONObject containing sensor data keyed by timestamp, or null if not available
     */
    fun loadTestValues(context: Context): JSONObject? {
        return try {
            loadTestData(context)?.optJSONObject("test_values")
        } catch (e: Exception) {
            Log.e("EntityTest", "Error loading test values from $fileName", e)
            null
        }
    }
}

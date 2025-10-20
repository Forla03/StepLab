package com.example.steplab.utils

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.sqrt

class CsvToJsonConverter {

    data class ConversionResult(
        val success: Boolean,
        val jsonString: String?,
        val recordCount: Int = 0,
        val errorMessage: String? = null
    )

    /**
     * Detects CSV format and converts to JSON.
     * Supports:
     * 1. MotionTracker format: multiple rows per timestamp with sensor type
     * 2. StepLab format: one row per timestamp with all sensor data
     */
    fun convertCsvToJson(
        inputStream: InputStream,
        additionalNotes: String = "",
        numberOfStepsOverride: Int? = null
    ): ConversionResult {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            if (lines.isEmpty()) {
                return ConversionResult(false, null, errorMessage = "CSV file is empty")
            }

            // Extract metadata from comment lines (if present)
            val metadata = extractMetadata(lines)
            
            // Find the actual header line (first non-comment line)
            val headerLineIndex = lines.indexOfFirst { !it.trim().startsWith("#") }
            if (headerLineIndex < 0) {
                return ConversionResult(false, null, errorMessage = "No header found in CSV")
            }
            
            val header = splitCsvLine(lines[headerLineIndex])
            
            // Detect CSV format
            val isStepLabFormat = isStepLabCsvFormat(header)
            
            // Use metadata from CSV if available, otherwise use parameters
            val finalNotes = metadata["additional_notes"] ?: additionalNotes
            val finalSteps = metadata["number_of_steps"]?.toIntOrNull() ?: numberOfStepsOverride
            
            if (isStepLabFormat) {
                convertStepLabCsv(lines.drop(headerLineIndex), header, finalNotes, finalSteps)
            } else {
                convertMotionTrackerCsv(lines, header, finalNotes, finalSteps)
            }
        } catch (e: Exception) {
            ConversionResult(false, null, errorMessage = "Error reading CSV: ${e.message}")
        }
    }

    /**
     * Extract metadata from comment lines (lines starting with #).
     * Format: # key: value
     */
    private fun extractMetadata(lines: List<String>): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#")) break // Stop at first non-comment line
            
            // Parse "# key: value"
            val content = trimmed.substring(1).trim() // Remove #
            val colonIndex = content.indexOf(":")
            if (colonIndex > 0) {
                val key = content.substring(0, colonIndex).trim()
                var value = content.substring(colonIndex + 1).trim()
                
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length - 1)
                        .replace("\"\"", "\"") // Unescape doubled quotes
                }
                
                metadata[key] = value
            }
        }
        
        return metadata
    }

    /**
     * Detects if CSV is in StepLab format (all sensors in one row).
     * StepLab CSV has direct sensor column names like "acceleration_x", "gyroscope_x", etc.
     */
    private fun isStepLabCsvFormat(header: List<String>): Boolean {
        val normalized = header.map { normalize(it) }
        
        // Check for StepLab-specific column patterns
        val hasStepLabPattern = normalized.any { col ->
            col.contains("accelerationx") || 
            col.contains("accelerationy") || 
            col.contains("accelerationz") ||
            col.contains("accelerationmagnitude")
        }
        
        return hasStepLabPattern
    }

    /**
     * Convert StepLab format CSV (one row per timestamp with all sensor data).
     */
    private fun convertStepLabCsv(
        lines: List<String>,
        header: List<String>,
        additionalNotes: String,
        numberOfStepsOverride: Int?
    ): ConversionResult {
        val normalized = header.map { normalize(it) }
        
        // Find column indices
        val timestampIdx = normalized.indexOfFirst { it.contains("timestamp") || it.contains("time") }
        if (timestampIdx < 0) {
            return ConversionResult(false, null, errorMessage = "Missing Timestamp column")
        }

        val testValues = LinkedHashMap<String, JSONObject>()
        var successCount = 0

        for (i in 1 until lines.size) {
            try {
                val row = splitCsvLine(lines[i])
                if (row.size <= timestampIdx) continue

                val timestamp = row[timestampIdx].trim()
                if (timestamp.isEmpty()) continue

                val sensorData = JSONObject()
                var hasData = false

                // Map all columns to JSON object
                for (j in header.indices) {
                    if (j == timestampIdx) continue
                    if (j >= row.size) continue
                    
                    val value = row[j].trim()
                    if (value.isEmpty() || value.equals("nan", ignoreCase = true) || 
                        value.equals("null", ignoreCase = true)) continue

                    // Use original header name (not normalized)
                    sensorData.put(header[j], value)
                    hasData = true
                }

                if (hasData) {
                    testValues[timestamp] = sensorData
                    successCount++
                }
            } catch (_: Exception) {
                // Skip invalid rows
            }
        }

        if (successCount == 0) {
            return ConversionResult(false, null, errorMessage = "No valid data rows found in CSV")
        }

        val root = JSONObject().apply {
            put("number_of_steps", numberOfStepsOverride ?: 50)
            put("additional_notes", additionalNotes)
            put("test_values", JSONObject(testValues as Map<*, *>))
        }

        return ConversionResult(
            success = true,
            jsonString = root.toString(),
            recordCount = successCount
        )
    }

    /**
     * Convert MotionTracker format CSV.
     * All sensor data from the same row are combined into a single JSON object
     * with the original timestamp (no artificial timestamp increments).
     */
    private fun convertMotionTrackerCsv(
        lines: List<String>,
        header: List<String>,
        additionalNotes: String,
        numberOfStepsOverride: Int?
    ): ConversionResult {
        val index = detectColumns(header)
        if (index.timestamp < 0) {
            return ConversionResult(false, null, errorMessage = "Missing Timestamp column")
        }

        val testValues = LinkedHashMap<String, JSONObject>()
        var successCount = 0
        var errorCount = 0

        for (i in 1 until lines.size) {
            try {
                val row = splitCsvLine(lines[i])
                if (row.size <= index.timestamp) continue

                val ts = row[index.timestamp].trim()
                if (ts.isEmpty()) continue

                val timestamp = try { ts.toLong().toString() } catch (_: Exception) { ts }
                
                // Create a single JSON object with all sensor data from this row
                val sensorData = JSONObject()
                var hasAnyData = false

                // Accelerometer
                val ax = getNumeric(row, index.accelX)
                val ay = getNumeric(row, index.accelY)
                val az = getNumeric(row, index.accelZ)
                
                if (ax != null) {
                    sensorData.put("acceleration_x", ax)
                    hasAnyData = true
                }
                if (ay != null) {
                    sensorData.put("acceleration_y", ay)
                    hasAnyData = true
                }
                if (az != null) {
                    sensorData.put("acceleration_z", az)
                    hasAnyData = true
                }
                if (ax != null && ay != null && az != null) {
                    val mag = calcMagnitude(ax, ay, az)
                    if (mag != null) sensorData.put("acceleration_magnitude", mag)
                }
             
                // Gyroscope
                val gx = getNumeric(row, index.gyroX)
                val gy = getNumeric(row, index.gyroY)
                val gz = getNumeric(row, index.gyroZ)
                
                if (gx != null) {
                    sensorData.put("gyroscope_x", gx)
                    hasAnyData = true
                }
                if (gy != null) {
                    sensorData.put("gyroscope_y", gy)
                    hasAnyData = true
                }
                if (gz != null) {
                    sensorData.put("gyroscope_z", gz)
                    hasAnyData = true
                }

                // Magnetometer
                val mx = getNumeric(row, index.magnetX)
                val my = getNumeric(row, index.magnetY)
                val mz = getNumeric(row, index.magnetZ)
                
                if (mx != null) {
                    sensorData.put("magnetometer_x", mx)
                    hasAnyData = true
                }
                if (my != null) {
                    sensorData.put("magnetometer_y", my)
                    hasAnyData = true
                }
                if (mz != null) {
                    sensorData.put("magnetometer_z", mz)
                    hasAnyData = true
                }
              
                // Gravity
                val grx = getNumeric(row, index.gravityX)
                val gry = getNumeric(row, index.gravityY)
                val grz = getNumeric(row, index.gravityZ)
                
                if (grx != null) {
                    sensorData.put("gravity_x", grx)
                    hasAnyData = true
                }
                if (gry != null) {
                    sensorData.put("gravity_y", gry)
                    hasAnyData = true
                }
                if (grz != null) {
                    sensorData.put("gravity_z", grz)
                    hasAnyData = true
                }

                // Rotation
                val rx = getNumeric(row, index.rotationX)
                val ry = getNumeric(row, index.rotationY)
                val rz = getNumeric(row, index.rotationZ)
                
                if (rx != null) {
                    sensorData.put("rotation_x", rx)
                    hasAnyData = true
                }
                if (ry != null) {
                    sensorData.put("rotation_y", ry)
                    hasAnyData = true
                }
                if (rz != null) {
                    sensorData.put("rotation_z", rz)
                    hasAnyData = true
                }
                
                // Metadata (add to all sensor types if present)
                getRaw(row, index.sex)?.let { 
                    sensorData.put("sex", it)
                    hasAnyData = true
                }
                getRaw(row, index.age)?.let { 
                    sensorData.put("age", it)
                    hasAnyData = true
                }
                getRaw(row, index.height)?.let { 
                    sensorData.put("height", it)
                    hasAnyData = true
                }
                getRaw(row, index.weight)?.let { 
                    sensorData.put("weight", it)
                    hasAnyData = true
                }
                getRaw(row, index.position)?.let { 
                    sensorData.put("position", it)
                    hasAnyData = true
                }
                getRaw(row, index.activity)?.let { 
                    sensorData.put("activity", it)
                    hasAnyData = true
                }

                if (hasAnyData) {
                    testValues[timestamp] = sensorData
                    successCount++
                }
            } catch (_: Exception) {
                errorCount++
            }
        }

        if (successCount == 0) {
            return ConversionResult(false, null, errorMessage = "No valid data rows found in CSV")
        }

        val root = JSONObject().apply {
            put("number_of_steps", numberOfStepsOverride ?: successCount)
            put("additional_notes", additionalNotes)
            put("test_values", JSONObject(testValues as Map<*, *>))
        }

        return ConversionResult(
            success = true,
            jsonString = root.toString(),
            recordCount = successCount,
            errorMessage = if (errorCount > 0) "Skipped $errorCount invalid rows" else null
        )
    }

    // -------------------- Helpers --------------------

    private data class Cols(
        val timestamp: Int,
        val accelX: Int, val accelY: Int, val accelZ: Int,
        val gyroX: Int,  val gyroY: Int,  val gyroZ: Int,
        val magnetX: Int, val magnetY: Int, val magnetZ: Int,
        val gravityX: Int, val gravityY: Int, val gravityZ: Int,
        val rotationX: Int, val rotationY: Int, val rotationZ: Int,
        val sex: Int, val age: Int, val height: Int, val weight: Int,
        val position: Int, val activity: Int
    )

    private fun detectColumns(h: List<String>): Cols {
        val norm = h.map { normalize(it) }

        fun find(vararg keys: String): Int {
            return norm.indexOfFirst { col ->
                keys.any { k -> col == k || (col.startsWith(k) && col.length >= k.length) }
            }
        }

        // timestamp/ms/epoch
        val ts = firstValidIndex(find("timestamp", "time", "ts"))

        // accelerometer
        val ax = firstValidIndex(find("accelerometerx", "accx", "ax"))
        val ay = firstValidIndex(find("accelerometery", "accy", "ay"))
        val az = firstValidIndex(find("accelerometerz", "accz", "az"))

        // gyroscope
        val gx = firstValidIndex(find("gyroscopex", "gyrx", "gx"))
        val gy = firstValidIndex(find("gyroscopey", "gyry", "gy"))
        val gz = firstValidIndex(find("gyroscopez", "gyrz", "gz"))

        // magnetometer
        val mx = firstValidIndex(find("magnetometerx", "magx", "mx"))
        val my = firstValidIndex(find("magnetometery", "magy", "my"))
        val mz = firstValidIndex(find("magnetometerz", "magz", "mz"))

        // gravity
        val grx = firstValidIndex(find("gravityx", "gravx"))
        val gry = firstValidIndex(find("gravityy", "gravy"))
        val grz = firstValidIndex(find("gravityz", "gravz"))

        // rotation vector
        val rx = firstValidIndex(find("rotationx", "rotx", "rotvecx"))
        val ry = firstValidIndex(find("rotationy", "roty", "rotvecy"))
        val rz = firstValidIndex(find("rotationz", "rotz", "rotvecz"))

        // metadata
        val sex = firstValidIndex(find("sex", "gender"))
        val age = firstValidIndex(find("age"))
        val height = firstValidIndex(find("height"))
        val weight = firstValidIndex(find("weight"))
        val position = firstValidIndex(find("position", "phoneposition", "placement"))
        val activity = firstValidIndex(find("activity", "walktype", "walkingtype"))

        return Cols(ts, ax, ay, az, gx, gy, gz, mx, my, mz, grx, gry, grz, rx, ry, rz, sex, age, height, weight, position, activity)
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("\\s+".toRegex(), "")
            .replace("[^a-z0-9]".toRegex(), "") // leva punti, trattini, ecc.

    private fun firstValidIndex(i: Int): Int = if (i >= 0) i else -1

    private fun splitCsvLine(line: String): List<String> =
        line.split(',').map { it.trim() }

    private fun getNumeric(row: List<String>, idx: Int): String? {
        if (idx < 0 || idx >= row.size) return null
        val raw = row[idx].trim()
        if (raw.isEmpty()) return null
        val v = raw.lowercase()
        if (v == "nan" || v == "null" || v == "inf" || v == "-inf") return null
        return if (NUMERIC_REGEX.matches(raw)) raw else null
    }

    private fun getRaw(row: List<String>, idx: Int): String? {
        if (idx < 0 || idx >= row.size) return null
        val raw = row[idx].trim()
        return if (raw.isEmpty()) null else raw
    }

    private fun calcMagnitude(ax: String, ay: String, az: String): String? {
        return try {
            val x = ax.toDouble()
            val y = ay.toDouble()
            val z = az.toDouble()
            val m = sqrt(x * x + y * y + z * z)
            if (m.isFinite()) stripTrailingZeros(m) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun stripTrailingZeros(d: Double): String {
        val s = d.toString()
        return if (s.contains('E') || s.contains('e')) {
            s
        } else {
            s.trimEnd('0').trimEnd('.')
        }
    }

    companion object {
        private val NUMERIC_REGEX =
            Regex("^[+-]?((\\d+\\.?\\d*)|(\\.\\d+))([eE][+-]?\\d+)?$")
    }
}

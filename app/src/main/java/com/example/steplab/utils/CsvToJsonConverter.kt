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

            val header = splitCsvLine(lines.first())
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

                    var hasAnyData = false
                    var timestampOffset = 0L
                    val baseTimestamp = try { ts.toLong() } catch (_: Exception) { 0L }

                    val ax = getNumeric(row, index.accelX)
                    val ay = getNumeric(row, index.accelY)
                    val az = getNumeric(row, index.accelZ)

                    if (ax != null || ay != null || az != null) {
                        val accelObj = JSONObject()
                        if (ax != null) accelObj.put("acceleration_x", ax)
                        if (ay != null) accelObj.put("acceleration_y", ay)
                        if (az != null) accelObj.put("acceleration_z", az)
                        if (ax != null && ay != null && az != null) {
                            val mag = calcMagnitude(ax, ay, az)
                            if (mag != null) accelObj.put("acceleration_magnitude", mag)
                        }
                                               
                        getRaw(row, index.sex)?.let { accelObj.put("sex", it) }
                        getRaw(row, index.age)?.let { accelObj.put("age", it) }
                        getRaw(row, index.height)?.let { accelObj.put("height", it) }
                        getRaw(row, index.weight)?.let { accelObj.put("weight", it) }
                        getRaw(row, index.position)?.let { accelObj.put("position", it) }
                        getRaw(row, index.activity)?.let { accelObj.put("activity", it) }
                        
                        testValues["${baseTimestamp + timestampOffset++}"] = accelObj
                        hasAnyData = true
                    }
                 
                    val gx = getNumeric(row, index.gyroX)
                    val gy = getNumeric(row, index.gyroY)
                    val gz = getNumeric(row, index.gyroZ)
                    
                    if (gx != null || gy != null || gz != null) {
                        val gyroObj = JSONObject()
                        if (gx != null) gyroObj.put("gyroscope_x", gx)
                        if (gy != null) gyroObj.put("gyroscope_y", gy)
                        if (gz != null) gyroObj.put("gyroscope_z", gz)
                                            
                        getRaw(row, index.sex)?.let { gyroObj.put("sex", it) }
                        getRaw(row, index.age)?.let { gyroObj.put("age", it) }
                        getRaw(row, index.height)?.let { gyroObj.put("height", it) }
                        getRaw(row, index.weight)?.let { gyroObj.put("weight", it) }
                        getRaw(row, index.position)?.let { gyroObj.put("position", it) }
                        getRaw(row, index.activity)?.let { gyroObj.put("activity", it) }
                        
                        testValues["${baseTimestamp + timestampOffset++}"] = gyroObj
                        hasAnyData = true
                    }

                    val mx = getNumeric(row, index.magnetX)
                    val my = getNumeric(row, index.magnetY)
                    val mz = getNumeric(row, index.magnetZ)
                    
                    if (mx != null || my != null || mz != null) {
                        val magnetObj = JSONObject()
                        if (mx != null) magnetObj.put("magnetometer_x", mx)
                        if (my != null) magnetObj.put("magnetometer_y", my)
                        if (mz != null) magnetObj.put("magnetometer_z", mz)
                                               
                        getRaw(row, index.sex)?.let { magnetObj.put("sex", it) }
                        getRaw(row, index.age)?.let { magnetObj.put("age", it) }
                        getRaw(row, index.height)?.let { magnetObj.put("height", it) }
                        getRaw(row, index.weight)?.let { magnetObj.put("weight", it) }
                        getRaw(row, index.position)?.let { magnetObj.put("position", it) }
                        getRaw(row, index.activity)?.let { magnetObj.put("activity", it) }
                        
                        testValues["${baseTimestamp + timestampOffset++}"] = magnetObj
                        hasAnyData = true
                    }
                  
                    val grx = getNumeric(row, index.gravityX)
                    val gry = getNumeric(row, index.gravityY)
                    val grz = getNumeric(row, index.gravityZ)
                    
                    if (grx != null || gry != null || grz != null) {
                        val gravityObj = JSONObject()
                        if (grx != null) gravityObj.put("gravity_x", grx)
                        if (gry != null) gravityObj.put("gravity_y", gry)
                        if (grz != null) gravityObj.put("gravity_z", grz)
                                           
                        getRaw(row, index.sex)?.let { gravityObj.put("sex", it) }
                        getRaw(row, index.age)?.let { gravityObj.put("age", it) }
                        getRaw(row, index.height)?.let { gravityObj.put("height", it) }
                        getRaw(row, index.weight)?.let { gravityObj.put("weight", it) }
                        getRaw(row, index.position)?.let { gravityObj.put("position", it) }
                        getRaw(row, index.activity)?.let { gravityObj.put("activity", it) }
                        
                        testValues["${baseTimestamp + timestampOffset++}"] = gravityObj
                        hasAnyData = true
                    }

                    val rx = getNumeric(row, index.rotationX)
                    val ry = getNumeric(row, index.rotationY)
                    val rz = getNumeric(row, index.rotationZ)
                    
                    if (rx != null || ry != null || rz != null) {
                        val rotationObj = JSONObject()
                        if (rx != null) rotationObj.put("rotation_x", rx)
                        if (ry != null) rotationObj.put("rotation_y", ry)
                        if (rz != null) rotationObj.put("rotation_z", rz)
                                              
                        getRaw(row, index.sex)?.let { rotationObj.put("sex", it) }
                        getRaw(row, index.age)?.let { rotationObj.put("age", it) }
                        getRaw(row, index.height)?.let { rotationObj.put("height", it) }
                        getRaw(row, index.weight)?.let { rotationObj.put("weight", it) }
                        getRaw(row, index.position)?.let { rotationObj.put("position", it) }
                        getRaw(row, index.activity)?.let { rotationObj.put("activity", it) }
                        
                        testValues["${baseTimestamp + timestampOffset++}"] = rotationObj
                        hasAnyData = true
                    }

                    if (hasAnyData) successCount++
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

            ConversionResult(
                success = true,
                jsonString = root.toString(),
                recordCount = successCount,
                errorMessage = if (errorCount > 0) "Skipped $errorCount invalid rows" else null
            )
        } catch (e: Exception) {
            ConversionResult(false, null, errorMessage = "Error reading CSV: ${e.message}")
        }
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

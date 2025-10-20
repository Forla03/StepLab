package com.example.steplab.utils

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Converts JSON test data to CSV format.
 * Supports two CSV formats:
 * 1. StepLab format
 * 2. MotionTracker format
 */
class JsonToCsvConverter {

    data class ConversionResult(
        val success: Boolean,
        val csvString: String?,
        val errorMessage: String? = null
    )

    /**
     * Convert JSON test data to CSV format (StepLab format).
     * One row per timestamp with all available sensor data.
     * Metadata (number_of_steps, additional_notes) are stored as special comment rows.
     */
    fun convertJsonToCsv(jsonString: String): ConversionResult {
        return try {
            val root = JSONObject(jsonString)
            val testValuesJson = root.optJSONObject("test_values")
                ?: return ConversionResult(false, null, "Missing test_values in JSON")

            if (testValuesJson.length() == 0) {
                return ConversionResult(false, null, "Empty test_values")
            }

            // Extract metadata
            val numberOfSteps = root.optInt("number_of_steps", 0)
            val additionalNotes = root.optString("additional_notes", "")

            // Collect all timestamps and organize data
            val dataRows = mutableMapOf<Long, MutableMap<String, String>>()
            val timestamps = testValuesJson.keys().asSequence().toList()

            timestamps.forEach { timestampStr ->
                val timestamp = timestampStr.toLongOrNull() ?: 0L
                val sensorData = testValuesJson.getJSONObject(timestampStr)
                
                val row = dataRows.getOrPut(timestamp) { mutableMapOf() }
                
                // Extract all fields from sensor data
                sensorData.keys().forEach { key ->
                    row[key] = sensorData.getString(key)
                }
            }

            // Determine all possible columns (field names)
            val allFields = mutableSetOf<String>()
            dataRows.values.forEach { row ->
                allFields.addAll(row.keys)
            }

            // Define column order
            val orderedColumns = buildColumnOrder(allFields)

            // Build CSV with metadata as comment lines
            val csvBuilder = StringBuilder()
            
            // Add metadata as comment lines (starting with #)
            csvBuilder.append("# number_of_steps: $numberOfSteps\n")
            csvBuilder.append("# additional_notes: ${escapeCsvValue(additionalNotes)}\n")
            
            // Build CSV header
            csvBuilder.append("Timestamp")
            orderedColumns.forEach { col ->
                csvBuilder.append(",$col")
            }
            csvBuilder.append("\n")

            // Build CSV rows
            dataRows.toSortedMap().forEach { (timestamp, row) ->
                csvBuilder.append(timestamp)
                orderedColumns.forEach { col ->
                    csvBuilder.append(",")
                    csvBuilder.append(row[col] ?: "")
                }
                csvBuilder.append("\n")
            }

            ConversionResult(true, csvBuilder.toString())
        } catch (e: Exception) {
            ConversionResult(false, null, "Error converting JSON to CSV: ${e.message}")
        }
    }

    /**
     * Escape CSV values that contain special characters.
     */
    private fun escapeCsvValue(value: String): String {
        return if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            "\"${value.replace("\"", "\"\"")}\"" // Escape quotes by doubling them
        } else {
            value
        }
    }

    /**
     * Build ordered column list with logical grouping.
     */
    private fun buildColumnOrder(fields: Set<String>): List<String> {
        val ordered = mutableListOf<String>()
        
        // Accelerometer fields
        if (fields.contains("acceleration_x")) ordered.add("acceleration_x")
        if (fields.contains("acceleration_y")) ordered.add("acceleration_y")
        if (fields.contains("acceleration_z")) ordered.add("acceleration_z")
        if (fields.contains("acceleration_magnitude")) ordered.add("acceleration_magnitude")
        
        // Gyroscope fields
        if (fields.contains("gyroscope_x")) ordered.add("gyroscope_x")
        if (fields.contains("gyroscope_y")) ordered.add("gyroscope_y")
        if (fields.contains("gyroscope_z")) ordered.add("gyroscope_z")
        
        // Magnetometer fields
        if (fields.contains("magnetometer_x")) ordered.add("magnetometer_x")
        if (fields.contains("magnetometer_y")) ordered.add("magnetometer_y")
        if (fields.contains("magnetometer_z")) ordered.add("magnetometer_z")
        
        // Gravity fields
        if (fields.contains("gravity_x")) ordered.add("gravity_x")
        if (fields.contains("gravity_y")) ordered.add("gravity_y")
        if (fields.contains("gravity_z")) ordered.add("gravity_z")
        
        // Rotation fields
        if (fields.contains("rotation_x")) ordered.add("rotation_x")
        if (fields.contains("rotation_y")) ordered.add("rotation_y")
        if (fields.contains("rotation_z")) ordered.add("rotation_z")
        
        // Metadata fields
        if (fields.contains("sex")) ordered.add("sex")
        if (fields.contains("age")) ordered.add("age")
        if (fields.contains("height")) ordered.add("height")
        if (fields.contains("weight")) ordered.add("weight")
        if (fields.contains("position")) ordered.add("position")
        if (fields.contains("activity")) ordered.add("activity")
        
        // Any remaining fields not in the predefined order
        fields.forEach { field ->
            if (field !in ordered) {
                ordered.add(field)
            }
        }
        
        return ordered
    }
}

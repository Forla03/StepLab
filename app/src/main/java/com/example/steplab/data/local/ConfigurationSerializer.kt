package com.example.steplab.data.local

import com.example.steplab.algorithms.Configuration
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

object ConfigurationSerializer {

    fun serializeConfigurations(configurations: List<Configuration>): String {
        val jsonArray = JSONArray()
        
        configurations.forEach { config ->
            val jsonObject = JSONObject().apply {
                // Basic settings
                put("samplingFrequencyIndex", config.samplingFrequencyIndex)
                put("realTimeMode", config.realTimeMode)
                put("recognitionAlgorithm", config.recognitionAlgorithm)
                put("filterType", config.filterType)
                put("cutoffFrequencyIndex", config.cutoffFrequencyIndex)
                put("falseStepDetectionEnabled", config.falseStepDetectionEnabled)
                put("autocorcAlg", config.autocorcAlg)
                put("detectionThreshold", config.detectionThreshold.toString())
                
                // Rotation matrix
                val rotationMatrix = JSONArray()
                config.rotationMatrix.forEach { row ->
                    val jsonRow = JSONArray()
                    row.forEach { value ->
                        jsonRow.put(value.toString())
                    }
                    rotationMatrix.put(jsonRow)
                }
                put("rotationMatrix", rotationMatrix)
                
                // Additional fields
                put("lastLocalMaxAccel", config.lastLocalMaxAccel.toString())
                put("lastLocalMinAccel", config.lastLocalMinAccel.toString())
                put("lastStepSecondPhaseTime", config.lastStepSecondPhaseTime)
                put("lastStepFirstPhaseTime", config.lastStepFirstPhaseTime)
                put("lastXAxisIntersectionTime", config.lastXAxisIntersectionTime)
            }
            jsonArray.put(jsonObject)
        }
        
        return jsonArray.toString()
    }

    fun deserializeConfigurations(json: String): List<Configuration> {
        val configurations = mutableListOf<Configuration>()
        val jsonArray = JSONArray(json)
        
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            
            val config = Configuration().apply {
                samplingFrequencyIndex = jsonObject.getInt("samplingFrequencyIndex")
                realTimeMode = jsonObject.getInt("realTimeMode")
                recognitionAlgorithm = jsonObject.getInt("recognitionAlgorithm")
                filterType = jsonObject.getInt("filterType")
                cutoffFrequencyIndex = jsonObject.getInt("cutoffFrequencyIndex")
                falseStepDetectionEnabled = jsonObject.getBoolean("falseStepDetectionEnabled")
                autocorcAlg = jsonObject.getBoolean("autocorcAlg")
                detectionThreshold = BigDecimal(jsonObject.getString("detectionThreshold"))
                
                // Rotation matrix
                val rotationMatrixJson = jsonObject.getJSONArray("rotationMatrix")
                val newRotationMatrix = Array(3) { Array(3) { BigDecimal.ZERO } }
                for (rowIndex in 0 until rotationMatrixJson.length()) {
                    val rowJson = rotationMatrixJson.getJSONArray(rowIndex)
                    for (colIndex in 0 until rowJson.length()) {
                        newRotationMatrix[rowIndex][colIndex] = BigDecimal(rowJson.getString(colIndex))
                    }
                }
                rotationMatrix = newRotationMatrix
                
                lastLocalMaxAccel = BigDecimal(jsonObject.getString("lastLocalMaxAccel"))
                lastLocalMinAccel = BigDecimal(jsonObject.getString("lastLocalMinAccel"))
                lastStepSecondPhaseTime = jsonObject.getLong("lastStepSecondPhaseTime")
                lastStepFirstPhaseTime = jsonObject.getLong("lastStepFirstPhaseTime")
                lastXAxisIntersectionTime = jsonObject.getLong("lastXAxisIntersectionTime")
            }
            
            configurations.add(config)
        }
        
        return configurations
    }
}
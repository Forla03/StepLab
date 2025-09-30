package com.example.steplab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_configuration_comparisons")
data class EntitySavedConfigurationComparison(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val testId: Int,
    val testName: String, // Store test file name for display
    val configurationsJson: String, // Serialized configurations as JSON
    val createdAt: Long = System.currentTimeMillis()
)
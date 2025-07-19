package com.example.steplab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class EntityTest(
    @PrimaryKey(autoGenerate = true)
    val testId: Int = 0,
    val testValues: String,
    val numberOfSteps: Int,
    val additionalNotes: String,
    val fileName: String
)

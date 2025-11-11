package com.example.steplab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface DatabaseDao {
    // EntityTest methods
    @Query("SELECT * FROM EntityTest")
    suspend fun getAllTests(): List<EntityTest>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTest(newTest: EntityTest)

    @Query("DELETE FROM EntityTest WHERE testId = :id")
    suspend fun deleteTest(id: Int)

    @Query("DELETE FROM saved_configuration_comparisons WHERE testId = :testId")
    suspend fun deleteSavedConfigurationComparisonsByTestId(testId: Int)

    @Query("SELECT * FROM EntityTest WHERE testId = :id")
    suspend fun getTestFromId(id: Int): EntityTest?

    @Query("SELECT * FROM EntityTest WHERE fileName = :fileName")
    suspend fun getTestFromPath(fileName: String): EntityTest?

    // SavedConfigurationComparison methods
    @Query("SELECT * FROM saved_configuration_comparisons ORDER BY createdAt DESC")
    suspend fun getAllSavedConfigurationComparisons(): List<EntitySavedConfigurationComparison>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedConfigurationComparison(savedComparison: EntitySavedConfigurationComparison)

    @Query("DELETE FROM saved_configuration_comparisons WHERE id = :id")
    suspend fun deleteSavedConfigurationComparison(id: Int)

    @Query("SELECT * FROM saved_configuration_comparisons WHERE id = :id")
    suspend fun getSavedConfigurationComparisonById(id: Int): EntitySavedConfigurationComparison?

    @Query("SELECT * FROM saved_configuration_comparisons WHERE name = :name")
    suspend fun getSavedConfigurationComparisonByName(name: String): EntitySavedConfigurationComparison?
}
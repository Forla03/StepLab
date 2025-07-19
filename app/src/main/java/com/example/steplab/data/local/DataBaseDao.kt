package com.example.steplab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface DatabaseDao {
    @Query("SELECT * FROM EntityTest")
    suspend fun getAllTests(): List<EntityTest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(newTest: EntityTest)

    @Query("DELETE FROM EntityTest WHERE testId = :id")
    suspend fun deleteTest(id: Int)

    @Query("SELECT * FROM EntityTest WHERE testId = :id")
    suspend fun getTestFromId(id: Int): EntityTest?

    @Query("SELECT * FROM EntityTest WHERE fileName = :fileName")
    suspend fun getTestFromPath(fileName: String): EntityTest?
}
package com.example.steplab.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EntityTest::class], version = 1)
 abstract class MyDatabase : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao?
}
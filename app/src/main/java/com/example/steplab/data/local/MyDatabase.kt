package com.example.steplab.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntityTest::class, EntitySavedConfigurationComparison::class], 
    version = 2
)
abstract class MyDatabase : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao?

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_configuration_comparisons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `testId` INTEGER NOT NULL,
                        `testName` TEXT NOT NULL,
                        `configurationsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
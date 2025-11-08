package com.example.steplab.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntityTest::class, EntitySavedConfigurationComparison::class], 
    version = 4
)
abstract class MyDatabase : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao?

    companion object {
        /**
         * Migration from version 1 to 2:
         * - Adds the saved_configuration_comparisons table
         * - Establishes foreign key relationship with EntityTest
         * - Creates index on testId for better query performance
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new table with foreign key constraint
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_configuration_comparisons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `testId` INTEGER NOT NULL,
                        `testName` TEXT NOT NULL,
                        `configurationsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`testId`) REFERENCES `EntityTest`(`testId`) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create index on testId for better query performance
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_saved_configuration_comparisons_testId` 
                    ON `saved_configuration_comparisons` (`testId`)
                """.trimIndent())
            }
        }
        
        /**
         * Migration from version 2 to 3:
         * - Adds foreign key constraint to saved_configuration_comparisons
         * - The foreign key ensures referential integrity with EntityTest
         * - Requires recreating the table since SQLite doesn't support adding foreign keys to existing tables
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite doesn't support adding foreign keys to existing tables
                // We need to recreate the table with the foreign key constraint
                
                // 1. Create new table with foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_configuration_comparisons_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `testId` INTEGER NOT NULL,
                        `testName` TEXT NOT NULL,
                        `configurationsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`testId`) REFERENCES `EntityTest`(`testId`) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // 2. Copy data from old table to new table
                database.execSQL("""
                    INSERT INTO saved_configuration_comparisons_new 
                    (id, name, testId, testName, configurationsJson, createdAt)
                    SELECT id, name, testId, testName, configurationsJson, createdAt
                    FROM saved_configuration_comparisons
                """.trimIndent())
                
                // 3. Drop old table
                database.execSQL("DROP TABLE saved_configuration_comparisons")
                
                // 4. Rename new table to original name
                database.execSQL("ALTER TABLE saved_configuration_comparisons_new RENAME TO saved_configuration_comparisons")
                
                // 5. Recreate index (it was dropped with the old table)
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_saved_configuration_comparisons_testId` 
                    ON `saved_configuration_comparisons` (`testId`)
                """.trimIndent())
            }
        }
        
        /**
         * Migration from version 3 to 4:
         * - Removes the testValues column from EntityTest to eliminate data duplication
         * - Adds recordedAt column to store test recording timestamp
         * - Test sensor data is now read on-demand from JSON files in filesDir
         * - This reduces database size by ~99% and improves query performance
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite doesn't support dropping columns directly
                // We need to recreate the table without testValues
                
                // 1. Create new table without testValues, with recordedAt
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `EntityTest_new` (
                        `testId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `numberOfSteps` INTEGER NOT NULL,
                        `additionalNotes` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `recordedAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // 2. Copy data from old table to new table (excluding testValues)
                // Use current time for recordedAt if not available
                database.execSQL("""
                    INSERT INTO EntityTest_new 
                    (testId, numberOfSteps, additionalNotes, fileName, recordedAt)
                    SELECT testId, numberOfSteps, additionalNotes, fileName, ${System.currentTimeMillis()}
                    FROM EntityTest
                """.trimIndent())
                
                // 3. Drop old table
                database.execSQL("DROP TABLE EntityTest")
                
                // 4. Rename new table to original name
                database.execSQL("ALTER TABLE EntityTest_new RENAME TO EntityTest")
            }
        }
        
        /**
         * Template for future migrations:
         * 
         * Example: Adding a new column to EntityTest
         * val MIGRATION_2_3 = object : Migration(2, 3) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         database.execSQL("ALTER TABLE EntityTest ADD COLUMN newColumn TEXT DEFAULT ''")
         *     }
         * }
         * 
         * Example: Creating a new table
         * val MIGRATION_3_4 = object : Migration(3, 4) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         database.execSQL("""
         *             CREATE TABLE IF NOT EXISTS `NewTable` (
         *                 `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
         *                 `field1` TEXT NOT NULL,
         *                 `field2` INTEGER NOT NULL
         *             )
         *         """.trimIndent())
         *     }
         * }
         */
    }
}
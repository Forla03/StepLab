package com.example.steplab.ui.main

import android.app.Application
import androidx.room.Room
import com.example.steplab.data.local.MyDatabase

/**
 * Application class for StepLab.
 * Initializes the Room database once at app startup and provides a thread-safe singleton instance.
 */
class StepLabApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize database once for the entire application lifecycle
        database = Room.databaseBuilder(
            applicationContext,
            MyDatabase::class.java,
            "tests.db"
        )
            .addMigrations(
                MyDatabase.MIGRATION_1_2, 
                MyDatabase.MIGRATION_2_3,
                MyDatabase.MIGRATION_3_4
            )
            .build()
    }

    companion object {
        /**
         * Thread-safe singleton instance of the database.
         * Initialized once in onCreate() and accessible throughout the app.
         * Access via StepLabApplication.database
         */
        lateinit var database: MyDatabase
            private set
    }
}

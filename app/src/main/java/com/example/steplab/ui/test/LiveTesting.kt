package com.example.steplab.ui.test

import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.steplab.ui.main.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import com.example.steplab.ui.configuration.EnterSettingsFragment


class LiveTesting : AppCompatActivity() {

    private lateinit var configuration: Configuration
    private var pedometerRunningFragment: PedometerRunningFragment? = null

    private lateinit var newPedometerButton: Button
    private lateinit var startPedometerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.live_testing)

        newPedometerButton = findViewById(R.id.new_pedometer)
        startPedometerButton = findViewById(R.id.start_pedometer)

        newPedometerButton.setOnClickListener {
            configuration = Configuration()
            
            // Unregister previous fragment's sensors if it exists
            pedometerRunningFragment?.let {
                (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(it)
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, EnterSettingsFragment(configuration))
                .commit()

            newPedometerButton.visibility = View.GONE
            startPedometerButton.visibility = View.VISIBLE
        }

        startPedometerButton.setOnClickListener {
            // Create a new fragment instance with the current configuration
            // This ensures clean state for each new pedometer session
            pedometerRunningFragment = PedometerRunningFragment.newInstance(configuration)

            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, pedometerRunningFragment!!)
                .commit()

            newPedometerButton.visibility = View.VISIBLE
            startPedometerButton.visibility = View.GONE
        }

        // Trigger the initial configuration setup
        newPedometerButton.callOnClick()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }
}

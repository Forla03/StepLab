package com.example.steplab.ui.test

import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.steplab.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import com.example.steplab.ui.configuration.EnterSettingsFragment


class LiveTesting : AppCompatActivity() {

    private var configuration: Configuration? = null
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
            (getSystemService(SENSOR_SERVICE) as SensorManager)
                .unregisterListener(pedometerRunningFragment)

            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, EnterSettingsFragment(configuration!!))
                .commit()

            newPedometerButton.visibility = View.GONE
            startPedometerButton.visibility = View.VISIBLE
        }

        startPedometerButton.setOnClickListener {
            pedometerRunningFragment = configuration?.let { PedometerRunningFragment.newInstance(it) }

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

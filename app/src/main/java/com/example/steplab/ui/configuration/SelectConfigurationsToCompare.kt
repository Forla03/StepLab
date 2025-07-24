package com.example.steplab.ui.configuration

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.steplab.MainActivity
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import com.example.steplab.ui.test.SelectTest

class SelectConfigurationsToCompare : AppCompatActivity() {

    private lateinit var addConfiguration: Button
    private lateinit var startComparison: Button
    private lateinit var numberSelected: TextView
    private lateinit var frameLayout: FrameLayout
    private lateinit var enterSettingsFragment: EnterSettingsFragment

    private var numberOfConfigurationsSelected = 0
    private var configurations: ArrayList<Configuration?> = arrayListOf()
    private var appConfiguration: Configuration = Configuration()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.compare_configurations)

        addConfiguration = findViewById(R.id.add_configuration)
        startComparison = findViewById(R.id.start_comparison)
        numberSelected = findViewById(R.id.number_selected)
        frameLayout = findViewById(R.id.frame_layout)

        enterSettingsFragment = EnterSettingsFragment(appConfiguration, false, false)
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, enterSettingsFragment)
            .commit()

        addConfiguration.setOnClickListener {
            startComparison.isEnabled = true
            numberOfConfigurationsSelected++

            if (numberOfConfigurationsSelected < 7) {
                numberSelected.text = "($numberOfConfigurationsSelected)"
                configurations.add(appConfiguration)

                Toast.makeText(
                    this,
                    getString(R.string.configuration_saved),
                    Toast.LENGTH_SHORT
                ).show()

                if (numberOfConfigurationsSelected < 6) {
                    appConfiguration = Configuration()
                    enterSettingsFragment = EnterSettingsFragment(appConfiguration, false, false)
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.enter_from_left,
                            R.anim.exit_to_right,
                            R.anim.enter_from_right,
                            R.anim.exit_to_left
                        )
                        .replace(R.id.frame_layout, enterSettingsFragment)
                        .commit()
                }

                if (numberOfConfigurationsSelected == 6) {
                    frameLayout.alpha = 0.5f
                    enterSettingsFragment.disableAllViews()
                    addConfiguration.isEnabled = false
                }
            }
        }

        startComparison.setOnClickListener {
            startActivity(
                Intent(applicationContext, SelectTest::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra("configurations", configurations)
            )
        }
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

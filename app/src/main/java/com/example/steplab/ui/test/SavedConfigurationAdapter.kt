package com.example.steplab.ui.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import java.math.BigDecimal

class SavedConfigurationAdapter(
    private val configurations: List<Configuration>
) : RecyclerView.Adapter<SavedConfigurationAdapter.ConfigurationViewHolder>() {

    inner class ConfigurationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val configNumber: TextView = itemView.findViewById(R.id.config_number)
        val configAlgorithm: TextView = itemView.findViewById(R.id.config_algorithm)
        val configDetails: TextView = itemView.findViewById(R.id.config_details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigurationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.saved_configuration_item, parent, false)
        return ConfigurationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConfigurationViewHolder, position: Int) {
        val config = configurations[position]
        
        holder.configNumber.text = (position + 1).toString()
        
        // Algorithm type - handle autocorrelation first
        val algorithmText = if (config.autocorcAlg) {
            "Autocorrelation"
        } else {
            when (config.recognitionAlgorithm) {
                0 -> "Peak Only"
                1 -> "Peak + Intersection"
                2 -> " Peak + Time Filtering"
                else -> "Unknown"
            }
        }
        holder.configAlgorithm.text = "$algorithmText Algorithm"
        
        // Build details string - only show mode and filter
        val details = buildString {
            // Real-time mode
            val realTimeText = when (config.realTimeMode) {
                0 -> "Real-time"
                1 -> "Non Real-time"
                else -> "Unknown"
            }
            append("Mode: $realTimeText")
            
            // If autocorrelation is active, show Butterworth band-pass filter
            if (config.autocorcAlg) {
                append(", Filter: Butterworth band-pass")
            } else {
                // Filter type
                val filterText = when (config.filterType) {
                    0 -> "Bagilevi"
                    1 -> {
                        var text = "Low-pass"
                        // Add cutoff frequency for Low-pass filter
                        if (config.cutoffFrequencyIndex >= 0) {
                            val freqText = when (config.cutoffFrequencyIndex) {
                                0 -> "2Hz"
                                1 -> "3Hz" 
                                2 -> "10Hz"
                                3 -> "2% Sampling Rate"
                                else -> "Unknown"
                            }
                            text += " ($freqText)"
                        }
                        text
                    }
                    2 -> "None"
                    3 -> "Rotation Matrix"
                    4 -> "Butterworth"
                    else -> "Unknown"
                }
                append(", Filter: $filterText")
                
                // Add false step detection if enabled
                if (config.falseStepDetectionEnabled) {
                    append(", False Step Detection")
                }
            }
        }
        
        holder.configDetails.text = details
    }

    override fun getItemCount(): Int = configurations.size
}
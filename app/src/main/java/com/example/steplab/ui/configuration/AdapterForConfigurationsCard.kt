package com.example.steplab.ui.configuration

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class AdapterForConfigurationsCard(
    private val configurations: ArrayList<Configuration>,
    private val colors: ArrayList<Int>,
    private val stepCounts: ArrayList<Int>,
    private val chartDataSets: java.util.ArrayList<LineDataSet>,
    private val context: Context,
    private val fullChartData: LineData,
    private val chart: LineChart
) : RecyclerView.Adapter<AdapterForConfigurationsCard.ViewHolder>() {

    private var tempChartData: LineData? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val modality: TextView = itemView.findViewById(R.id.modality)
        val algorithm: TextView = itemView.findViewById(R.id.algorithm)
        val filter: TextView = itemView.findViewById(R.id.filter)
        val frequency: TextView = itemView.findViewById(R.id.frequency)
        val steps: TextView = itemView.findViewById(R.id.steps)
        val number: TextView = itemView.findViewById(R.id.number)
        val stepsLabel: TextView = itemView.findViewById(R.id.steps_text)
        val card: CardView = itemView.findViewById(R.id.this_card)
        val cardLinearLayout: LinearLayout = itemView.findViewById(R.id.card_linearlayout_id)
        val mainLayout: LinearLayout = itemView.findViewById(R.id.this_layout)
        val secondaryLayout: LinearLayout = itemView.findViewById(R.id.other_layout)
        val numberBorder: LinearLayout = itemView.findViewById(R.id.number_border)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.configuration_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configurations[position]

        holder.number.text = (position + 1).toString()
        holder.numberBorder.backgroundTintList = ColorStateList.valueOf(colors[position])
        holder.steps.text = stepCounts[position].toString()

        // Modality
        val modalityText = when (config.realTimeMode) {
            0 -> context.getString(R.string.real_time)
            else -> context.getString(R.string.not_real_time)
        }
        val falseStepText = if (config.falseStepDetectionEnabled) " + False Step Detection" else ""
        holder.modality.text = context.getString(R.string.modality_italic) + modalityText + falseStepText

        // Step detection algorithm
        holder.algorithm.text = context.getString(R.string.step_recognition_algorithm_italic) + when (config.recognitionAlgorithm) {
            0 -> context.getString(R.string.peak)
            1 -> context.getString(R.string.intersection)
            2 -> context.getString(R.string.timeFiltering)
            -2 -> "Autocorrelation Algorithm"  // Special case for autocorrelation
            else -> ""
        }

        // Filter
        when (config.filterType) {
            0 -> {
                holder.filter.text = context.getString(R.string.filter_italic) + context.getString(R.string.bagilevi_filter)
                holder.frequency.visibility = View.GONE
            }
            1 -> {
                holder.filter.text = context.getString(R.string.filter_italic) + context.getString(R.string.low_pass_filter)
                holder.frequency.visibility = View.VISIBLE
                holder.frequency.text = context.getString(R.string.cutoff_frequency_italic) + when (config.cutoffFrequencyIndex) {
                    0 -> "2 Hz"
                    1 -> "3 Hz"
                    2 -> "10 Hz"
                    3 -> context.getString(R.string.divided_fifty)
                    else -> ""
                }
            }
            2 -> {
                holder.filter.text = context.getString(R.string.filter_italic) + context.getString(R.string.no_filter)
                holder.frequency.visibility = View.GONE
            }
            3 -> {
                holder.filter.text = context.getString(R.string.filter_italic) + context.getString(R.string.rotation_filter)
                holder.frequency.visibility = View.GONE
            }
            4 -> {
                holder.filter.text = context.getString(R.string.filter_italic) + context.getString(R.string.butterworth_filter)
                holder.frequency.visibility = View.VISIBLE
                holder.frequency.text = context.getString(R.string.cutoff_frequency_italic) + context.getString(R.string.dynamic_frequency)
            }
            -2 -> {
                holder.filter.text = context.getString(R.string.filter_italic) + "Butterworth Band Pass Filter"  // Special case for autocorrelation
                holder.frequency.visibility = View.GONE
            }
        }

        // Click listeners to highlight line
        val onClick = { _: View -> highlightLine(position) }
        listOf(
            holder.number, holder.numberBorder, holder.cardLinearLayout,
            holder.modality, holder.algorithm, holder.filter,
            holder.frequency, holder.steps, holder.card,
            holder.mainLayout, holder.secondaryLayout, holder.stepsLabel
        ).forEach { it.setOnClickListener(onClick) }
    }

    private fun highlightLine(position: Int) {
        tempChartData = LineData()
        val iterator = fullChartData.dataSets.iterator()
        while (iterator.hasNext()) {
            tempChartData!!.addDataSet(iterator.next())
        }

        fullChartData.clearValues()
        val total = tempChartData!!.dataSetCount

        for (i in 0 until total) {
            val set = tempChartData!!.getDataSetByIndex(i) as LineDataSet
            set.lineWidth = if (i == position + 1) 3f else 1f
            fullChartData.addDataSet(set)
        }

        chart.clear()
        chart.data = fullChartData
        fullChartData.notifyDataChanged()
        chart.notifyDataSetChanged()
    }

    override fun getItemCount(): Int = configurations.size
}

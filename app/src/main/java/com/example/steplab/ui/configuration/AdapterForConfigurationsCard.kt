package com.example.steplab.ui.configuration

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

class AdapterForConfigurationsCard(
    private val configurations: List<Configuration>,
    private val colors: List<Int>,
    private val stepCounts: List<Int>,
    private val onSelect: (Int) -> Unit // callback to notify selection
) : RecyclerView.Adapter<AdapterForConfigurationsCard.ViewHolder>() {

    private var selected = RecyclerView.NO_POSITION

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
        val v = LayoutInflater.from(parent.context).inflate(R.layout.configuration_card, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ctx = holder.itemView.context
        val config = configurations[position]

        holder.number.text = (position + 1).toString()
        holder.numberBorder.backgroundTintList = ColorStateList.valueOf(colors[position])
        holder.steps.text = stepCounts[position].toString()

        val modalityText = if (config.realTimeMode == 0)
            ctx.getString(R.string.real_time) else ctx.getString(R.string.not_real_time)
        val falseStepText = if (config.falseStepDetectionEnabled) " + False Step Detection" else ""
        holder.modality.text = ctx.getString(R.string.modality_italic) + modalityText + falseStepText

        holder.algorithm.text = ctx.getString(R.string.step_recognition_algorithm_italic) + when (config.recognitionAlgorithm) {
            0 -> ctx.getString(R.string.peak)
            1 -> ctx.getString(R.string.intersection)
            2 -> ctx.getString(R.string.timeFiltering)
            -2 -> "Autocorrelation Algorithm"
            else -> ""
        }

        when (config.filterType) {
            0 -> { // Bagilevi
                holder.filter.text = ctx.getString(R.string.filter_italic) + ctx.getString(R.string.bagilevi_filter)
                holder.frequency.visibility = View.GONE
            }
            1 -> { // Low pass
                holder.filter.text = ctx.getString(R.string.filter_italic) + ctx.getString(R.string.low_pass_filter)
                holder.frequency.visibility = View.VISIBLE
                holder.frequency.text = ctx.getString(R.string.cutoff_frequency_italic) + when (config.cutoffFrequencyIndex) {
                    0 -> "2 Hz"
                    1 -> "3 Hz"
                    2 -> "10 Hz"
                    3 -> ctx.getString(R.string.divided_fifty)
                    else -> ""
                }
            }
            2 -> { // No filter
                holder.filter.text = ctx.getString(R.string.filter_italic) + ctx.getString(R.string.no_filter)
                holder.frequency.visibility = View.GONE
            }
            3 -> { // Rotation
                holder.filter.text = ctx.getString(R.string.filter_italic) + ctx.getString(R.string.rotation_filter)
                holder.frequency.visibility = View.GONE
            }
            4 -> { // Butterworth
                holder.filter.text = ctx.getString(R.string.filter_italic) + ctx.getString(R.string.butterworth_filter)
                holder.frequency.visibility = View.VISIBLE
                holder.frequency.text = ctx.getString(R.string.cutoff_frequency_italic) + ctx.getString(R.string.dynamic_frequency)
            }
            -2 -> { // Autocorrelation pipeline
                holder.filter.text = ctx.getString(R.string.filter_italic) + "Butterworth Band Pass Filter"
                holder.frequency.visibility = View.GONE
            }
        }

        holder.itemView.isSelected = (position == selected)

        val clickListener = View.OnClickListener {
            val old = selected
            val newPos = holder.adapterPosition
            if (newPos == RecyclerView.NO_POSITION) return@OnClickListener

            selected = newPos
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
            notifyItemChanged(selected)
            onSelect(selected)
        }

        listOf(
            holder.number, holder.numberBorder, holder.cardLinearLayout,
            holder.modality, holder.algorithm, holder.filter,
            holder.frequency, holder.steps, holder.card,
            holder.mainLayout, holder.secondaryLayout, holder.stepsLabel
        ).forEach { it.setOnClickListener(clickListener) }
    }

    override fun getItemCount(): Int = configurations.size
}

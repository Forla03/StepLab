package com.example.steplab.ui.test

import android.content.Context
import android.content.Intent
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.R
import com.example.steplab.ui.test.SavedConfigurationAdapter
import com.example.steplab.data.local.ConfigurationSerializer
import com.example.steplab.data.local.EntitySavedConfigurationComparison
import com.example.steplab.ui.configuration.ConfigurationsComparison
import java.text.SimpleDateFormat
import java.util.*

class SavedTestsAdapter(
    private val savedTests: List<EntitySavedConfigurationComparison>,
    private val context: Context
) : RecyclerView.Adapter<SavedTestsAdapter.SavedTestViewHolder>() {

    private val expandedItems = mutableSetOf<Int>()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    inner class SavedTestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val testName: TextView = itemView.findViewById(R.id.test_name)
        val createdAt: TextView = itemView.findViewById(R.id.created_at)
        val expandButton: ImageButton = itemView.findViewById(R.id.expand_button)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
        val detailsLayout: LinearLayout = itemView.findViewById(R.id.details_layout)
        val testLabel: TextView = itemView.findViewById(R.id.test_label)
        val configurationsLabel: TextView = itemView.findViewById(R.id.configurations_label)
        val configurationsRecyclerView: RecyclerView = itemView.findViewById(R.id.configurations_recycler_view)
        val mainLayout: LinearLayout = itemView.findViewById(R.id.main_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedTestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.saved_test_card, parent, false)
        return SavedTestViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavedTestViewHolder, position: Int) {
        val savedTest = savedTests[position]
        val isExpanded = expandedItems.contains(position)

        holder.testName.text = savedTest.name
        holder.createdAt.text = dateFormatter.format(Date(savedTest.createdAt))

        // Set details visibility
        holder.detailsLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.expandButton.setImageResource(
            if (isExpanded) R.drawable.ic_keyboard_arrow_up_24 
            else R.drawable.ic_keyboard_arrow_down_24
        )

        if (isExpanded) {
            holder.testLabel.text = "${context.getString(R.string.test_colon)} ${savedTest.testName}"
            
            val configurations = try {
                ConfigurationSerializer.deserializeConfigurations(savedTest.configurationsJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            // Setup RecyclerView for configurations
            val configAdapter = SavedConfigurationAdapter(configurations)
            holder.configurationsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = configAdapter
                isNestedScrollingEnabled = false
            }
        }

        // Click on name to view the comparison
        holder.testName.setOnClickListener {
            viewSavedComparison(savedTest)
        }

        holder.mainLayout.setOnClickListener {
            viewSavedComparison(savedTest)
        }

        // Expand/collapse button
        holder.expandButton.setOnClickListener {
            if (isExpanded) {
                expandedItems.remove(position)
            } else {
                expandedItems.add(position)
            }
            notifyItemChanged(position)
        }

        // Delete button
        holder.deleteButton.setOnClickListener {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)

            AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.delete_saved_comparison))
                .setPositiveButton(context.getString(R.string.yes)) { _, _ ->
                    val pos = holder.adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setPositiveButton
                    val current = savedTests[pos]
                    (context as SavedTests).deleteSavedTest(current, pos)
                }
                .setNegativeButton(context.getString(R.string.No), null)
                .show()
        }
    }

    override fun getItemCount(): Int = savedTests.size

    private fun viewSavedComparison(savedTest: EntitySavedConfigurationComparison) {
        try {
            val configurations = ConfigurationSerializer.deserializeConfigurations(savedTest.configurationsJson)
            
            val intent = Intent(context, ConfigurationsComparison::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra("configurations", ArrayList(configurations))
                putExtra("test", savedTest.testId.toString())
                putExtra("viewMode", true) // This will hide the Save button
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
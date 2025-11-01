package com.example.steplab.ui.configuration

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.R
import com.example.steplab.ui.main.StepLabApplication
import com.example.steplab.algorithms.Configuration
import com.example.steplab.ui.test.CardTest
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.Calendar

class AdapterForTestCard(
    private val testDataset: ArrayList<CardTest>,
    private val context: Context,
    private val configurations: ArrayList<Configuration>
) : RecyclerView.Adapter<AdapterForTestCard.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val number: TextView = itemView.findViewById(R.id.number)
        val date: TextView = itemView.findViewById(R.id.date)
        val steps: TextView = itemView.findViewById(R.id.steps)
        val notes: TextView = itemView.findViewById(R.id.notes)
        val select: ImageButton = itemView.findViewById(R.id.select)
        val layout: LinearLayout = itemView.findViewById(R.id.layout)
        val calendar: Calendar = Calendar.getInstance()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.test_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.number.text = "${position + 1}. "

        try {
            val testValues = testDataset[position].testValues
            if (testValues.isNullOrEmpty()) {
                holder.calendar.timeInMillis = System.currentTimeMillis()
            } else {
                val timestampKey = JSONObject(testValues).keys().next().toLong()
                holder.calendar.timeInMillis = timestampKey
            }

            val day = holder.calendar[Calendar.DAY_OF_MONTH].toString().padStart(2, '0')
            val month = (holder.calendar[Calendar.MONTH] + 1).toString().padStart(2, '0')
            val year = holder.calendar[Calendar.YEAR].toString()
            val hour = holder.calendar[Calendar.HOUR_OF_DAY].toString().padStart(2, '0')
            val minute = holder.calendar[Calendar.MINUTE].toString().padStart(2, '0')
            val second = holder.calendar[Calendar.SECOND].toString().padStart(2, '0')

            holder.date.text = "$day/$month/$year - $hour:$minute:$second"

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        holder.steps.text = context.getString(R.string.number_of_steps_counted) +
                ": ${testDataset[position].numberOfSteps}"

        val notes = testDataset[position].additionalNotes
        holder.notes.text = notes
        holder.notes.visibility = if (notes.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.select.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

            context.startActivity(
                Intent(context, ConfigurationsComparison::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra("configurations", configurations)
                    .putExtra("test", testDataset[pos].testId)
            )
        }

        holder.layout.setOnLongClickListener {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)

            AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.delete))
                .setPositiveButton(context.getString(R.string.yes)) { _: DialogInterface?, _: Int ->
                    val pos = holder.adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setPositiveButton

                    val fileDeleted = File(context.filesDir, testDataset[pos].filePathName).delete()

                    val lifecycleOwner = context as? LifecycleOwner
                    lifecycleOwner?.lifecycleScope?.launch {
                        val db = StepLabApplication.database
                        val dao = db.databaseDao()
                        // delete saved comparisons associated with this test, then the test itself
                        dao?.deleteSavedConfigurationComparisonsByTestId(testDataset[pos].testId.toInt())
                        dao?.deleteTest(testDataset[pos].testId.toInt())
                    }

                    testDataset.removeAt(pos)
                    notifyItemRemoved(pos)
                }
                .setNegativeButton(context.getString(R.string.No), null)
                .create()
                .show()

            false
        }
    }

    override fun getItemCount(): Int = testDataset.size
}

package com.example.steplab.ui.test

import android.content.Context
import android.content.DialogInterface
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.steplab.ui.main.MainActivity
import com.example.steplab.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AdapterForSendTestCard(
    private val context: Context,
    private val dataset: ArrayList<CardTest>,
    private var selectedTests: Int,
    private val sendTestButton: Button
) : RecyclerView.Adapter<AdapterForSendTestCard.ViewHolder>() {

    private var isBinding: Boolean = false

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val selectedCheckBox: CheckBox = itemView.findViewById(R.id.selected)
        val filePathTextView: TextView = itemView.findViewById(R.id.file_path_name)
        val notesTextView: TextView = itemView.findViewById(R.id.notes)
        val stepsTextView: TextView = itemView.findViewById(R.id.number_of_steps)
        val layout: LinearLayout = itemView.findViewById(R.id.layout)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.send_test_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = dataset.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataset[position]

        holder.filePathTextView.text = item.filePathName
        holder.stepsTextView.text =
            "${context.getString(R.string.number_of_steps)} ${item.numberOfSteps}"
        holder.notesTextView.text = item.additionalNotes
        holder.notesTextView.visibility = if (item.additionalNotes.isEmpty()) View.GONE else View.VISIBLE

        isBinding = true
        holder.selectedCheckBox.isChecked = item.selected
        isBinding = false

        holder.selectedCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) {
                item.selected = isChecked
                notifyDataSetChanged()
                selectedTests += if (isChecked) 1 else -1
                sendTestButton.isEnabled = selectedTests > 0
            }
        }

        holder.deleteButton.setOnClickListener {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)

            AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.delete))
                .setPositiveButton(context.getString(R.string.yes)) { _: DialogInterface?, _: Int ->
                    if (item.selected) selectedTests--
                    sendTestButton.isEnabled = selectedTests > 0

                    File(context.filesDir, item.filePathName).delete()

                    CoroutineScope(Dispatchers.IO).launch {
                        MainActivity.getDatabase()?.databaseDao()?.deleteTest(item.testId.toInt())
                    }

                    dataset.removeAt(position)
                    notifyItemRemoved(position)
                }
                .setNegativeButton(context.getString(R.string.No), null)
                .create()
                .show()
        }
    }
}

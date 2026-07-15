package com.fatsar.toplanti.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.toplanti.R
import com.fatsar.toplanti.databinding.ItemTaskBinding
import com.fatsar.toplanti.model.TaskItem
import com.fatsar.toplanti.model.TaskStatus

/** Görev listesi: sahip, termin, durum; "Onay gerekli" işareti (FR-031, FR-032). */
class TaskAdapter(
    private val onClick: (TaskItem) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val items = mutableListOf<TaskItem>()

    @Suppress("UNUSED_PARAMETER")
    fun submit(tasks: List<TaskItem>, context: Context) {
        items.clear()
        // Önce onay bekleyenler görünür (PRD 10.5 "İnceleme gerekli")
        items.addAll(tasks.sortedBy { if (it.status == TaskStatus.NEEDS_REVIEW) 0 else 1 })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: TaskItem) {
            val c = binding.root.context
            binding.taskTitle.text = task.title
            val owner = task.ownerText.ifBlank { c.getString(R.string.owner_unspecified) }
            val due = task.dueTextOriginal.ifBlank { c.getString(R.string.due_unspecified) }
            binding.taskMeta.text = "$owner • $due"
            binding.reviewChip.visibility =
                if (task.status == TaskStatus.NEEDS_REVIEW) View.VISIBLE else View.GONE
            binding.doneMark.visibility =
                if (task.status == TaskStatus.DONE) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onClick(task) }
        }
    }
}

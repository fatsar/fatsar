package com.fatsar.toplanti.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.toplanti.R
import com.fatsar.toplanti.databinding.ItemDateHeaderBinding
import com.fatsar.toplanti.databinding.ItemMeetingBinding
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.util.Fmt
import java.util.Calendar

/** Tarih başlıklarıyla gruplanmış toplantı listesi (FR-050, FR-051). */
class MeetingListAdapter(
    private val onClick: (Meeting) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val label: String) : Row()
        data class Item(val meeting: Meeting) : Row()
    }

    private val rows = mutableListOf<Row>()
    private var untitled = ""

    fun submit(meetings: List<Meeting>, untitledLabel: String) {
        untitled = untitledLabel
        rows.clear()
        var lastDay = ""
        for (m in meetings) {
            val day = Fmt.date(dayStart(m.meetingDate))
            if (day != lastDay) {
                rows.add(Row.Header(day))
                lastDay = day
            }
            rows.add(Row.Item(m))
        }
        notifyDataSetChanged()
    }

    private fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            HeaderVH(ItemDateHeaderBinding.inflate(inflater, parent, false))
        else
            ItemVH(ItemMeetingBinding.inflate(inflater, parent, false))
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).binding.headerText.text = row.label
            is Row.Item -> (holder as ItemVH).bind(row.meeting)
        }
    }

    private class HeaderVH(val binding: ItemDateHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class ItemVH(val binding: ItemMeetingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(m: Meeting) {
            val ctx = binding.root.context
            binding.title.text = m.displayTitle(untitled)
            val statusText = when (m.status) {
                MeetingStatus.RECORDING -> ctx.getString(R.string.status_recording)
                MeetingStatus.PROCESSING -> ctx.getString(R.string.status_processing)
                MeetingStatus.REVIEW -> ctx.getString(R.string.status_review)
                MeetingStatus.DONE -> ctx.getString(R.string.status_done)
                MeetingStatus.FAILED -> ctx.getString(R.string.status_failed)
            }
            val extras = mutableListOf(Fmt.duration(m.durationMs), statusText)
            if (m.tags.isNotEmpty()) extras.add(m.tags.joinToString(", ") { "#$it" })
            binding.subtitle.text = extras.joinToString(" • ")
            binding.root.setOnClickListener { onClick(m) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}

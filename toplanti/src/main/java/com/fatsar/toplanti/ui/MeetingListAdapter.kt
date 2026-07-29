package com.fatsar.toplanti.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.toplanti.R
import com.fatsar.toplanti.databinding.ItemDateHeaderBinding
import com.fatsar.toplanti.databinding.ItemMeetingBinding
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingMode
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

            val meta = mutableListOf(Fmt.duration(m.durationMs))
            if (m.tags.isNotEmpty()) meta.add(m.tags.joinToString(" ") { "#$it" })
            binding.subtitle.text = meta.joinToString("  •  ")

            // Kayıt / içe aktarma ayrımı ikonla gösterilir (FR-007)
            binding.modeIcon.setImageResource(
                if (m.mode == MeetingMode.IMPORTED) R.drawable.ic_folder_open else R.drawable.ic_mic
            )

            val (labelRes, bgRes, fgRes) = statusStyle(m.status)
            binding.statusChip.text = ctx.getString(labelRes)
            binding.statusChip.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, bgRes))
            binding.statusChip.setTextColor(ContextCompat.getColor(ctx, fgRes))
            binding.iconTile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, bgRes))
            binding.modeIcon.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, fgRes))

            // İnceleme bekleyen toplantılar kenarlıkla öne çıkar
            val needsReview = m.status == MeetingStatus.REVIEW
            val density = ctx.resources.displayMetrics.density
            binding.root.strokeWidth = ((if (needsReview) 2 else 1) * density).toInt()
            binding.root.setStrokeColor(
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        ctx,
                        if (needsReview) R.color.warn_600 else R.color.card_stroke
                    )
                )
            )

            binding.root.setOnClickListener { onClick(m) }
        }
    }

    /** @return (etiket, arka plan rengi, metin/ikon rengi) */
    private fun statusStyle(status: MeetingStatus): Triple<Int, Int, Int> = when (status) {
        MeetingStatus.RECORDING -> Triple(R.string.status_recording, R.color.rec_100, R.color.rec_600)
        MeetingStatus.PROCESSING -> Triple(R.string.status_processing, R.color.brand_100, R.color.brand_600)
        MeetingStatus.REVIEW -> Triple(R.string.status_review, R.color.warn_100, R.color.warn_600)
        MeetingStatus.DONE -> Triple(R.string.status_done, R.color.ok_100, R.color.ok_600)
        MeetingStatus.FAILED -> Triple(R.string.status_failed, R.color.rec_100, R.color.rec_600)
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}

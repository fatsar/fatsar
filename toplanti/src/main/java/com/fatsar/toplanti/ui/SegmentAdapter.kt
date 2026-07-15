package com.fatsar.toplanti.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.toplanti.databinding.ItemSegmentBinding
import com.fatsar.toplanti.model.TranscriptSegment
import com.fatsar.toplanti.util.Fmt

/**
 * Transkript listesi: zaman damgası, konuşmacı, metin ve düşük güven
 * işareti (FR-015, FR-016). Dokunma → kaynaktan dinleme; uzun basma → düzeltme.
 */
class SegmentAdapter(
    private val speakerName: (String) -> String,
    private val showClean: () -> Boolean,
    private val onClick: (TranscriptSegment) -> Unit,
    private val onLongClick: (TranscriptSegment) -> Unit
) : RecyclerView.Adapter<SegmentAdapter.VH>() {

    private val items = mutableListOf<TranscriptSegment>()

    fun submit(segments: List<TranscriptSegment>) {
        items.clear()
        items.addAll(segments)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSegmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemSegmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(seg: TranscriptSegment) {
            binding.timeText.text = Fmt.timestamp(seg.startMs)
            binding.speakerText.text = speakerName(seg.speakerId)
            binding.segmentText.text = seg.displayText(showClean())
            binding.lowConfidenceMark.visibility =
                if (seg.isLowConfidence) View.VISIBLE else View.GONE
            binding.editedMark.visibility = if (seg.userText != null) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onClick(seg) }
            binding.root.setOnLongClickListener {
                onLongClick(seg)
                true
            }
        }
    }
}

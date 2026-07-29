package com.fatsar.toplanti.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.toplanti.R
import com.fatsar.toplanti.databinding.ItemSegmentBinding
import com.fatsar.toplanti.model.TranscriptSegment
import com.fatsar.toplanti.nlp.TurkishText
import com.fatsar.toplanti.util.Fmt
import kotlin.math.abs

/**
 * Transkript listesi: konuşmacı avatarı, zaman damgası, metin balonu ve
 * düşük güven işareti (FR-015, FR-016). Dokunma → kaynaktan dinleme;
 * uzun basma → düzeltme.
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

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position], position > 0 && items[position - 1].speakerId == items[position].speakerId)

    inner class VH(private val binding: ItemSegmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(seg: TranscriptSegment, sameSpeakerAsPrevious: Boolean) {
            val ctx = binding.root.context
            val name = speakerName(seg.speakerId)
            val color = ContextCompat.getColor(ctx, speakerColor(seg.speakerId))

            binding.speakerAvatar.text = initialOf(name)
            binding.speakerAvatar.backgroundTintList = ColorStateList.valueOf(color)
            // Aynı konuşmacı arka arkaya konuşuyorsa avatarı yalnızca ilk segmentte göster
            binding.speakerAvatar.visibility = if (sameSpeakerAsPrevious) View.INVISIBLE else View.VISIBLE
            binding.speakerText.visibility = if (sameSpeakerAsPrevious) View.GONE else View.VISIBLE
            binding.speakerText.text = name
            binding.speakerText.setTextColor(color)

            binding.timeText.text = Fmt.timestamp(seg.startMs)
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

        private fun initialOf(name: String): String {
            val first = name.trim().firstOrNull { it.isLetterOrDigit() } ?: '?'
            return TurkishText.capitalizeTr(first.toString())
        }

        private fun speakerColor(speakerId: String): Int {
            if (speakerId.isBlank()) return SPEAKER_COLORS[0]
            return SPEAKER_COLORS[abs(speakerId.hashCode()) % SPEAKER_COLORS.size]
        }
    }

    private companion object {
        val SPEAKER_COLORS = intArrayOf(
            R.color.speaker_1, R.color.speaker_2, R.color.speaker_3,
            R.color.speaker_4, R.color.speaker_5, R.color.speaker_6
        )
    }
}

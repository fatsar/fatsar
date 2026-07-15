package com.fatsar.toplanti.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.toplanti.R
import com.fatsar.toplanti.databinding.ItemAttachmentBinding
import com.fatsar.toplanti.model.Attachment
import com.fatsar.toplanti.model.AttachmentType
import com.fatsar.toplanti.util.Fmt

/** Ek listesi: tür, ad, boyut ve toplantı içi zaman referansı (FR-042). */
class AttachmentAdapter(
    private val onClick: (Attachment) -> Unit,
    private val onLongClick: (Attachment) -> Unit
) : RecyclerView.Adapter<AttachmentAdapter.VH>() {

    private val items = mutableListOf<Attachment>()

    fun submit(attachments: List<Attachment>) {
        items.clear()
        items.addAll(attachments)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemAttachmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(att: Attachment) {
            val c = binding.root.context
            binding.attachmentIcon.setImageResource(
                if (att.type == AttachmentType.VIDEO) R.drawable.ic_video else R.drawable.ic_image
            )
            binding.attachmentName.text = att.caption.ifBlank { att.fileName }
            val time = if (att.meetingOffsetMs >= 0)
                c.getString(R.string.attachment_at, Fmt.duration(att.meetingOffsetMs))
            else c.getString(R.string.attachment_later)
            binding.attachmentMeta.text = "$time • ${Fmt.fileSize(att.sizeBytes)}"
            binding.root.setOnClickListener { onClick(att) }
            binding.root.setOnLongClickListener {
                onLongClick(att)
                true
            }
        }
    }
}

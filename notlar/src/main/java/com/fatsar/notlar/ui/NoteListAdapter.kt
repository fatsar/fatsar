package com.fatsar.notlar.ui

import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.notlar.R
import com.fatsar.notlar.databinding.ItemNoteBinding
import com.fatsar.notlar.model.Note
import com.fatsar.notlar.search.NoteSearch
import com.fatsar.notlar.util.TimeText

/**
 * Kenar çubuğundaki not listesi. Satırlar klavyeyle gezilebilir (odaklanabilir,
 * Enter/Space ile açılır) ve arama sözcükleri satır içinde vurgulanır.
 */
class NoteListAdapter(
    private val onOpen: (Note) -> Unit
) : ListAdapter<Note, NoteListAdapter.NoteHolder>(DIFF) {

    /** Vurgulanacak arama sözcükleri; değişince görünen satırlar yenilenir. */
    var query: String = ""
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    /** Ana bölmede açık olan not; listede işaretli görünür. */
    var selectedId: String? = null
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            val context = binding.root.context
            val title = note.title.ifBlank { context.getString(R.string.untitled) }
            binding.noteTitle.text = if (note.title.isBlank()) title else highlight(title)

            val snippet = note.snippet
            binding.noteSnippet.visibility = if (snippet.isBlank()) View.GONE else View.VISIBLE
            if (snippet.isNotBlank()) binding.noteSnippet.text = highlight(snippet)

            binding.noteTime.text = context.getString(
                R.string.edited_at,
                TimeText.format(context, note.updatedAt)
            )
            binding.notePinBadge.visibility = if (note.pinned) View.VISIBLE else View.GONE
            binding.noteSketchBadge.visibility =
                if (note.sketchName != null) View.VISIBLE else View.GONE

            binding.root.isActivated = note.id == selectedId
            binding.root.contentDescription = "$title, ${binding.noteTime.text}"
            binding.root.setOnClickListener { onOpen(note) }
            binding.root.setOnKeyListener { _, keyCode, event ->
                val open = event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
                if (open) onOpen(note)
                open
            }
        }

        private fun highlight(text: String): CharSequence {
            val spans = NoteSearch.highlights(text, query)
            if (spans.isEmpty()) return text
            val color = com.google.android.material.color.MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorTertiaryContainer
            )
            val spannable = SpannableString(text)
            for (range in spans) {
                if (range.last >= text.length) continue
                spannable.setSpan(
                    BackgroundColorSpan(color),
                    range.first,
                    range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return spannable
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
        }
    }
}

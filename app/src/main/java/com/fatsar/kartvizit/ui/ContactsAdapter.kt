package com.fatsar.kartvizit.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.kartvizit.R
import com.fatsar.kartvizit.databinding.ItemContactBinding
import com.fatsar.kartvizit.model.ContactRecord
import java.util.Locale

class ContactsAdapter(
    private val onClick: (ContactRecord) -> Unit,
    private val onAddToContacts: (ContactRecord) -> Unit,
    private val onDelete: (ContactRecord) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.Holder>() {

    private val items = mutableListOf<ContactRecord>()

    fun submit(list: List<ContactRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: ContactRecord) {
            val context = binding.root.context

            val displayName = record.name.ifBlank {
                record.company.ifBlank { context.getString(R.string.unnamed_contact) }
            }
            binding.textName.text = displayName

            // Baş harfli, isme göre renklendirilmiş avatar
            binding.avatar.text = initials(displayName)
            binding.avatar.backgroundTintList =
                ColorStateList.valueOf(avatarColor(displayName))

            val companyLine = listOf(record.title, record.company)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            binding.textCompany.text = companyLine
            binding.textCompany.visibility = if (companyLine.isBlank()) View.GONE else View.VISIBLE

            // Telefon satırı: birincil numara + kalan sayısı
            val phone = record.phones.firstOrNull()
            if (phone != null) {
                val extra = record.phones.size - 1
                val suffix = if (extra > 0) "  +$extra" else ""
                binding.textPhone.text = "${phone.type.trLabel()}  ${phone.number}$suffix"
                binding.rowPhone.visibility = View.VISIBLE
            } else {
                binding.rowPhone.visibility = View.GONE
            }

            // E-posta satırı
            val email = record.emails.firstOrNull()
            if (email != null) {
                val extra = record.emails.size - 1
                binding.textEmail.text = if (extra > 0) "$email  +$extra" else email
                binding.rowEmail.visibility = View.VISIBLE
            } else {
                binding.rowEmail.visibility = View.GONE
            }

            // Kategori rozeti
            if (record.category.isBlank()) {
                binding.chipCategory.visibility = View.GONE
            } else {
                binding.chipCategory.visibility = View.VISIBLE
                binding.chipCategory.text = record.category
            }

            // Gönderilmiş olsa bile buton tıklanabilir kalır: kullanıcı kişiyi
            // rehbere yeniden gönderebilir (öncesinde kopya uyarısı gösterilir).
            binding.btnAddContact.isEnabled = true
            binding.btnAddContact.text = context.getString(
                if (record.addedToContacts) R.string.sent_to_contacts else R.string.add_to_contacts
            )

            binding.root.setOnClickListener { onClick(record) }
            binding.btnAddContact.setOnClickListener { onAddToContacts(record) }
            binding.btnDelete.setOnClickListener { onDelete(record) }
        }

        private fun initials(name: String): String {
            val tokens = name.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
            val letters = when {
                tokens.isEmpty() -> "?"
                tokens.size == 1 -> tokens[0].take(2)
                else -> "${tokens.first().first()}${tokens.last().first()}"
            }
            return letters.uppercase(Locale("tr", "TR"))
        }

        private fun avatarColor(name: String): Int {
            val hash = name.fold(0) { acc, c -> acc * 31 + c.code }
            return AVATAR_COLORS[Math.floorMod(hash, AVATAR_COLORS.size)]
        }
    }

    private companion object {
        // Baş harf avatarı arka plan paleti (beyaz metinle iyi kontrast)
        val AVATAR_COLORS = intArrayOf(
            Color.parseColor("#00796B"), Color.parseColor("#3949AB"),
            Color.parseColor("#00838F"), Color.parseColor("#5E35B1"),
            Color.parseColor("#C2185B"), Color.parseColor("#E64A19"),
            Color.parseColor("#2E7D32"), Color.parseColor("#6D4C41"),
            Color.parseColor("#00695C"), Color.parseColor("#455A64")
        )
    }
}

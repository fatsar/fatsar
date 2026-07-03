package com.fatsar.kartvizit.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.kartvizit.R
import com.fatsar.kartvizit.databinding.ItemContactBinding
import com.fatsar.kartvizit.model.ContactRecord

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

            binding.textName.text = record.name.ifBlank {
                record.company.ifBlank { context.getString(R.string.unnamed_contact) }
            }

            val companyLine = listOf(record.title, record.company)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            binding.textCompany.text = companyLine
            binding.textCompany.visibility =
                if (companyLine.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            val details = (record.phones + record.emails).joinToString("  •  ")
            binding.textDetails.text = details
            binding.textDetails.visibility =
                if (details.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            binding.textCategory.text = record.category
            binding.textCategory.visibility =
                if (record.category.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            if (record.addedToContacts) {
                binding.btnAddContact.isEnabled = false
                binding.btnAddContact.text = context.getString(R.string.in_contacts)
            } else {
                binding.btnAddContact.isEnabled = true
                binding.btnAddContact.text = context.getString(R.string.add_to_contacts)
            }

            binding.root.setOnClickListener { onClick(record) }
            binding.btnAddContact.setOnClickListener { onAddToContacts(record) }
            binding.btnDelete.setOnClickListener { onDelete(record) }
        }
    }
}

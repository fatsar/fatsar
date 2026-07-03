package com.fatsar.kartvizit

import android.Manifest
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fatsar.kartvizit.contacts.DeviceContacts
import com.fatsar.kartvizit.data.ContactRepository
import com.fatsar.kartvizit.databinding.ActivityEditContactBinding
import com.fatsar.kartvizit.export.ExportManager
import com.fatsar.kartvizit.model.ContactRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OCR sonucunu gözden geçirme / düzenleme ekranı. Kaydetme sırasında Excel
 * dosyası güncellenir ve istenirse kişi telefon rehberine eklenir.
 */
class EditContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditContactBinding
    private var existing: ContactRecord? = null
    private var pendingRecord: ContactRecord? = null

    private val contactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val record = pendingRecord
            pendingRecord = null
            if (record != null) {
                if (grants[Manifest.permission.WRITE_CONTACTS] == true) {
                    insertToContactsAndFinish(record)
                } else {
                    toast(getString(R.string.contacts_permission_denied))
                    finishWithSaved()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val id = intent.getStringExtra(EXTRA_ID)
        if (id != null) {
            existing = ContactRepository.get(this, id)
        }

        setupCategoryField()

        val record = existing
        if (record != null) {
            binding.inputName.setText(record.name)
            binding.inputTitle.setText(record.title)
            binding.inputCompany.setText(record.company)
            binding.inputPhones.setText(record.phones.joinToString(", "))
            binding.inputEmails.setText(record.emails.joinToString(", "))
            binding.inputWebsite.setText(record.website)
            binding.inputAddress.setText(record.address)
            binding.inputCategory.setText(record.category, false)
            binding.inputNotes.setText(record.notes)
            binding.checkAddToContacts.isChecked = false
            binding.checkAddToContacts.isEnabled = !record.addedToContacts
            if (record.addedToContacts) {
                binding.checkAddToContacts.setText(R.string.already_in_contacts)
            }
        } else {
            binding.inputName.setText(intent.getStringExtra(EXTRA_NAME).orEmpty())
            binding.inputTitle.setText(intent.getStringExtra(EXTRA_TITLE).orEmpty())
            binding.inputCompany.setText(intent.getStringExtra(EXTRA_COMPANY).orEmpty())
            binding.inputPhones.setText(intent.getStringExtra(EXTRA_PHONES).orEmpty())
            binding.inputEmails.setText(intent.getStringExtra(EXTRA_EMAILS).orEmpty())
            binding.inputWebsite.setText(intent.getStringExtra(EXTRA_WEBSITE).orEmpty())
            binding.inputAddress.setText(intent.getStringExtra(EXTRA_ADDRESS).orEmpty())
            binding.inputNotes.setText(intent.getStringExtra(EXTRA_RAW_TEXT).orEmpty())
            binding.checkAddToContacts.isChecked = true
        }

        binding.btnSave.setOnClickListener { save() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Mevcut kategorileri öneri olarak sunar; kullanıcı yenisini de yazabilir. */
    private fun setupCategoryField() {
        val categories = ContactRepository.getAll(this)
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        binding.inputCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, categories)
        )
    }

    private fun save() {
        val record = (existing ?: ContactRecord()).apply {
            name = binding.inputName.text?.toString()?.trim().orEmpty()
            title = binding.inputTitle.text?.toString()?.trim().orEmpty()
            company = binding.inputCompany.text?.toString()?.trim().orEmpty()
            phones = splitList(binding.inputPhones.text?.toString())
            emails = splitList(binding.inputEmails.text?.toString())
            website = binding.inputWebsite.text?.toString()?.trim().orEmpty()
            address = binding.inputAddress.text?.toString()?.trim().orEmpty()
            category = binding.inputCategory.text?.toString()?.trim().orEmpty()
            notes = binding.inputNotes.text?.toString()?.trim().orEmpty()
        }

        if (record.name.isBlank() && record.company.isBlank() &&
            record.phones.isEmpty() && record.emails.isEmpty()
        ) {
            toast(getString(R.string.nothing_to_save))
            return
        }

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ContactRepository.upsert(this@EditContactActivity, record)
                ExportManager.regenerateExcel(this@EditContactActivity)
            }
            if (binding.checkAddToContacts.isChecked && !record.addedToContacts) {
                pendingRecord = record
                contactsPermission.launch(
                    arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                )
            } else {
                finishWithSaved()
            }
        }
    }

    private fun insertToContactsAndFinish(record: ContactRecord) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                DeviceContacts.insert(this@EditContactActivity, record)
            }
            if (ok) {
                record.addedToContacts = true
                withContext(Dispatchers.IO) {
                    ContactRepository.upsert(this@EditContactActivity, record)
                }
                toast(getString(R.string.saved_and_added_to_contacts))
                finish()
            } else {
                toast(getString(R.string.add_to_contacts_failed))
                finishWithSaved()
            }
        }
    }

    private fun finishWithSaved() {
        toast(getString(R.string.record_saved))
        finish()
    }

    private fun splitList(value: String?): List<String> =
        value.orEmpty()
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_COMPANY = "extra_company"
        const val EXTRA_PHONES = "extra_phones"
        const val EXTRA_EMAILS = "extra_emails"
        const val EXTRA_WEBSITE = "extra_website"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_RAW_TEXT = "extra_raw_text"
    }
}

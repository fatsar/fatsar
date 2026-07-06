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
import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.model.TypedPhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
        if (id != null) existing = ContactRepository.get(this, id)

        setupCategoryField()

        // Var olan kayıt düzenleniyorsa ondan, değilse taramadan gelen ön dolu
        // JSON'dan alanları doldur.
        val source = existing ?: intent.getStringExtra(EXTRA_PREFILL_JSON)
            ?.let { runCatching { ContactRecord.fromJson(JSONObject(it)) }.getOrNull() }

        if (source != null) {
            binding.inputName.setText(source.name)
            binding.inputTitle.setText(source.title)
            binding.inputCompany.setText(source.company)
            binding.inputMobile.setText(source.phonesOf(PhoneType.MOBILE).joinToString(", "))
            binding.inputWork.setText(
                (source.phonesOf(PhoneType.WORK) + source.phonesOf(PhoneType.OTHER)).joinToString(", ")
            )
            binding.inputFax.setText(source.phonesOf(PhoneType.FAX).joinToString(", "))
            binding.inputHome.setText(source.phonesOf(PhoneType.HOME).joinToString(", "))
            binding.inputEmails.setText(source.emails.joinToString(", "))
            binding.inputWebsite.setText(source.website)
            binding.inputAddress.setText(source.address)
            binding.inputCategory.setText(source.category, false)
            binding.inputNotes.setText(source.notes)
        }

        val isSaved = existing != null && existing!!.addedToContacts
        binding.checkAddToContacts.isChecked = existing == null
        binding.checkAddToContacts.isEnabled = !isSaved
        if (isSaved) binding.checkAddToContacts.setText(R.string.already_in_contacts)

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
        val phones = buildList {
            addAll(typedPhones(binding.inputMobile.text?.toString(), PhoneType.MOBILE))
            addAll(typedPhones(binding.inputWork.text?.toString(), PhoneType.WORK))
            addAll(typedPhones(binding.inputFax.text?.toString(), PhoneType.FAX))
            addAll(typedPhones(binding.inputHome.text?.toString(), PhoneType.HOME))
        }

        val record = (existing ?: ContactRecord()).apply {
            name = binding.inputName.text?.toString()?.trim().orEmpty()
            title = binding.inputTitle.text?.toString()?.trim().orEmpty()
            company = binding.inputCompany.text?.toString()?.trim().orEmpty()
            this.phones = phones
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

    private fun typedPhones(value: String?, type: PhoneType): List<TypedPhone> =
        splitList(value).map { TypedPhone(it, type) }

    private fun splitList(value: String?): List<String> =
        value.orEmpty()
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_PREFILL_JSON = "extra_prefill_json"
    }
}

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
import com.fatsar.kartvizit.data.ProfileStore
import com.fatsar.kartvizit.data.ThemeStore
import com.fatsar.kartvizit.databinding.ActivityEditContactBinding
import com.fatsar.kartvizit.export.CloudBackup
import com.fatsar.kartvizit.export.ExportManager
import com.fatsar.kartvizit.model.ContactRecord
import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.model.TypedPhone
import com.fatsar.kartvizit.ui.SystemBars
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        // Ana ekranla aynı renk teması kullanılsın
        setTheme(ThemeStore.themeRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivityEditContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Kenardan kenara çizimde araç çubuğu ve form sistem çubuklarının
        // altında kalmasın
        SystemBars.apply(
            root = binding.root,
            header = binding.appBar,
            bottomPadded = binding.scroll
        )

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

        // Rehbere ekleme ARTIK OTOMATİK DEĞİL: kutu her zaman boş gelir, yani
        // taranan kart yalnızca uygulamaya kaydedilir. Kullanıcı isterse burada
        // işaretler ya da sonradan listedeki "Rehbere Ekle" butonunu kullanır.
        // Daha önce gönderilmiş kayıtta kutu "yeniden gönder" anlamına gelir
        // (kaydederken kopya uyarısı gösterilir).
        val alreadySent = existing?.addedToContacts == true
        binding.checkAddToContacts.isChecked = false
        if (alreadySent) binding.checkAddToContacts.setText(R.string.resend_checkbox)

        binding.btnSave.setOnClickListener { save() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Profilleri (İş/Özel + eklenenler) öneri olarak sunar; yenisi de yazılabilir. */
    private fun setupCategoryField() {
        val profiles = ProfileStore.profiles(this)
        val extras = ContactRepository.getAll(this)
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { c -> profiles.none { it.equals(c, ignoreCase = true) } }
            .sorted()
        binding.inputCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, profiles + extras)
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
                // Yeni bir profil (kategori) yazıldıysa kalıcı profil listesine ekle
                // "Genel" ayrılmış sekme adıdır; profil olarak eklenmez
                if (record.category.isNotBlank() &&
                    !record.category.equals(getString(R.string.profile_all), ignoreCase = true)
                ) {
                    ProfileStore.addProfile(this@EditContactActivity, record.category)
                }
                ContactRepository.upsert(this@EditContactActivity, record)
                ExportManager.regenerateExcel(this@EditContactActivity)
                CloudBackup.maybeAutoBackup(this@EditContactActivity)
            }
            when {
                !binding.checkAddToContacts.isChecked -> finishWithSaved()
                // İlk kez gönderim: doğrudan izin iste
                !record.addedToContacts -> launchContactPermission(record)
                // Daha önce gönderilmiş: kopya oluşabileceği için önce onay iste
                else -> confirmResendThenInsert(record)
            }
        }
    }

    private fun launchContactPermission(record: ContactRecord) {
        pendingRecord = record
        contactsPermission.launch(
            arrayOf(Manifest.permission.WRITE_CONTACTS)
        )
    }

    /** İkinci kez rehbere gönderim öncesi kopya uyarısı. */
    private fun confirmResendThenInsert(record: ContactRecord) {
        val who = record.name.ifBlank {
            record.company.ifBlank { getString(R.string.unnamed_contact) }
        }
        val message = if (record.lastSentAt > 0) {
            getString(R.string.resend_message_dated, who, dateStr(record.lastSentAt))
        } else {
            getString(R.string.resend_message, who)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.resend_title)
            .setMessage(message)
            .setPositiveButton(R.string.resend) { _, _ -> launchContactPermission(record) }
            .setNegativeButton(R.string.cancel) { _, _ -> finishWithSaved() }
            .setOnCancelListener { finishWithSaved() }
            .show()
    }

    private fun dateStr(timestamp: Long): String =
        SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).format(Date(timestamp))

    private fun insertToContactsAndFinish(record: ContactRecord) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                DeviceContacts.insert(this@EditContactActivity, record)
            }
            if (ok) {
                record.addedToContacts = true
                record.lastSentAt = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    ContactRepository.upsert(this@EditContactActivity, record)
                    CloudBackup.maybeAutoBackup(this@EditContactActivity)
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

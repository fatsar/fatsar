package com.fatsar.kartvizit

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import com.fatsar.kartvizit.contacts.DeviceContacts
import com.fatsar.kartvizit.data.ContactRepository
import com.fatsar.kartvizit.databinding.ActivityMainBinding
import com.fatsar.kartvizit.export.ExcelManager
import com.fatsar.kartvizit.model.ContactRecord
import com.fatsar.kartvizit.ocr.CardTextParser
import com.fatsar.kartvizit.ocr.OcrLine
import com.fatsar.kartvizit.ui.ContactsAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ContactsAdapter

    private var cameraImageUri: Uri? = null
    private var pendingContactAdd: ContactRecord? = null

    // Tembel oluşturma: tanıyıcı yalnızca ilk tarama sırasında yüklenir.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraImageUri
            if (success && uri != null) processImage(uri)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) processImage(uri)
        }

    private val contactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val record = pendingContactAdd
            pendingContactAdd = null
            if (grants[Manifest.permission.WRITE_CONTACTS] == true && record != null) {
                addToDeviceContacts(record)
            } else {
                toast(getString(R.string.contacts_permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = ContactsAdapter(
            onClick = { record ->
                startActivity(
                    Intent(this, EditContactActivity::class.java)
                        .putExtra(EditContactActivity.EXTRA_ID, record.id)
                )
            },
            onAddToContacts = { record -> requestAddToContacts(record) },
            onDelete = { record -> confirmDelete(record) }
        )
        binding.recycler.adapter = adapter

        binding.btnCamera.setOnClickListener { launchCamera() }
        binding.btnGallery.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        if (savedInstanceState != null) {
            cameraImageUri =
                BundleCompat.getParcelable(savedInstanceState, STATE_CAMERA_URI, Uri::class.java)
        } else {
            handleShareIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CAMERA_URI, cameraImageUri)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share_excel -> {
            shareExcelByEmail(); true
        }
        R.id.action_save_excel -> {
            saveExcelToDownloads(); true
        }
        R.id.action_set_email -> {
            showEmailDialog(); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Başka bir uygulamadan paylaşılan görseli (ör. bilgisayarda taranmış fotoğraf) kabul eder. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) processImage(uri)
        }
    }

    private fun launchCamera() {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val photoFile = File(imagesDir, "kartvizit_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        cameraImageUri = uri
        takePicture.launch(uri)
    }

    /** Görüntüyü cihaz üzerinde (çevrimdışı) OCR ile okur ve düzenleme ekranını açar. */
    private fun processImage(uri: Uri) {
        binding.progress.visibility = View.VISIBLE
        setButtonsEnabled(false)
        lifecycleScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(this@MainActivity, uri)
                }
                val result = recognizer.process(image).await()
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        OcrLine(line.text, line.boundingBox?.height()?.toFloat() ?: 0f)
                    }
                }
                if (lines.isEmpty()) {
                    toast(getString(R.string.no_text_found))
                } else {
                    val parsed = CardTextParser.parse(lines)
                    startActivity(
                        Intent(this@MainActivity, EditContactActivity::class.java)
                            .putExtra(EditContactActivity.EXTRA_NAME, parsed.name)
                            .putExtra(EditContactActivity.EXTRA_TITLE, parsed.title)
                            .putExtra(EditContactActivity.EXTRA_COMPANY, parsed.company)
                            .putExtra(EditContactActivity.EXTRA_PHONES, parsed.phones.joinToString(", "))
                            .putExtra(EditContactActivity.EXTRA_EMAILS, parsed.emails.joinToString(", "))
                            .putExtra(EditContactActivity.EXTRA_WEBSITE, parsed.website)
                            .putExtra(EditContactActivity.EXTRA_ADDRESS, parsed.address)
                            .putExtra(EditContactActivity.EXTRA_RAW_TEXT, parsed.rawText)
                    )
                }
            } catch (e: Exception) {
                toast(getString(R.string.scan_failed, e.localizedMessage ?: ""))
            } finally {
                binding.progress.visibility = View.GONE
                setButtonsEnabled(true)
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnCamera.isEnabled = enabled
        binding.btnGallery.isEnabled = enabled
    }

    private fun refreshList() {
        val records = ContactRepository.getAll(this)
        adapter.submit(records)
        binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun requestAddToContacts(record: ContactRecord) {
        pendingContactAdd = record
        contactsPermission.launch(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        )
    }

    private fun addToDeviceContacts(record: ContactRecord) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { DeviceContacts.insert(this@MainActivity, record) }
            if (ok) {
                record.addedToContacts = true
                withContext(Dispatchers.IO) {
                    ContactRepository.upsert(this@MainActivity, record)
                }
                refreshList()
                toast(getString(R.string.added_to_contacts))
            } else {
                toast(getString(R.string.add_to_contacts_failed))
            }
        }
    }

    private fun confirmDelete(record: ContactRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, record.name.ifBlank { record.company }))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        ContactRepository.delete(this@MainActivity, record.id)
                        ExcelManager.regenerate(this@MainActivity)
                    }
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareExcelByEmail() {
        if (ContactRepository.getAll(this).isEmpty()) {
            toast(getString(R.string.no_records_yet))
            return
        }
        lifecycleScope.launch {
            val chooser = withContext(Dispatchers.IO) {
                ExcelManager.buildEmailIntent(this@MainActivity, recipientEmail())
            }
            startActivity(chooser)
        }
    }

    private fun saveExcelToDownloads() {
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching { ExcelManager.saveToDownloads(this@MainActivity) }.getOrNull()
            }
            toast(
                if (path != null) getString(R.string.excel_saved, path)
                else getString(R.string.excel_save_failed)
            )
        }
    }

    private fun recipientEmail(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_EMAIL, null)

    private fun showEmailDialog() {
        val input = TextInputEditText(this).apply {
            setText(recipientEmail().orEmpty())
            hint = getString(R.string.email_hint)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_set_email)
            .setMessage(R.string.set_email_message)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(PREF_EMAIL, input.text?.toString()?.trim())
                    .apply()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val STATE_CAMERA_URI = "camera_uri"
        private const val PREFS = "settings"
        private const val PREF_EMAIL = "recipient_email"
    }
}

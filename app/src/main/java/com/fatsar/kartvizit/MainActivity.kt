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
import com.fatsar.kartvizit.export.ExportManager
import com.fatsar.kartvizit.model.ContactRecord
import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.model.TypedPhone
import com.fatsar.kartvizit.ocr.CardRegionDetector
import com.fatsar.kartvizit.ocr.CardSegmenter
import com.fatsar.kartvizit.ocr.CardTextParser
import com.fatsar.kartvizit.ocr.OcrLine
import com.fatsar.kartvizit.ocr.ScanEnricher
import com.fatsar.kartvizit.ocr.ScannedBarcode
import com.fatsar.kartvizit.ocr.ScannedContact
import com.fatsar.kartvizit.ui.ContactsAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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

    /** null = tüm kategoriler, "" = kategorisiz, diğer = tam eşleşme. */
    private var categoryFilter: String? = null

    // Tembel oluşturma: tanıyıcılar yalnızca ilk tarama sırasında yüklenir.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }
    private var scannersUsed = false

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

    override fun onDestroy() {
        super.onDestroy()
        // Yalnızca en az bir tarama yapıldıysa (tembel oluşturulduysa) kapat
        if (scannersUsed) {
            recognizer.close()
            barcodeScanner.close()
        }
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
        R.id.action_share_vcf -> {
            shareVcf(); true
        }
        R.id.action_save_excel -> {
            saveExcelToDownloads(); true
        }
        R.id.action_filter_category -> {
            showCategoryFilterDialog(); true
        }
        R.id.action_set_email -> {
            showEmailDialog(); true
        }
        R.id.action_version -> {
            toast(getString(R.string.version_info, BuildConfig.VERSION_NAME, BuildConfig.BUILD_SHA)); true
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

    /**
     * Görüntüyü cihaz üzerinde (çevrimdışı) OCR + karekod tanıma ile okur.
     * Tek fotoğrafta birden fazla kartvizit varsa hepsini ayırır. Tek kart
     * bulunursa düzenleme ekranını açar; birden fazlaysa hepsini kaydeder.
     */
    private fun processImage(uri: Uri) {
        binding.progress.visibility = View.VISIBLE
        setButtonsEnabled(false)
        scannersUsed = true
        lifecycleScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(this@MainActivity, uri)
                }
                val text = recognizer.process(image).await()
                val barcodes = runCatching { barcodeScanner.process(image).await() }
                    .getOrDefault(emptyList())

                val lines = text.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        val box = line.boundingBox
                        // Yazı boyutu = kutunun KISA kenarı: satır ister yatay
                        // ister dik (90° döndürülmüş kart) olsun doğru kalır
                        val textSize = if (box != null) {
                            minOf(box.width(), box.height()).toFloat()
                        } else 0f
                        OcrLine(
                            text = line.text,
                            height = textSize,
                            left = box?.left ?: 0,
                            top = box?.top ?: 0,
                            right = box?.right ?: 0,
                            bottom = box?.bottom ?: 0
                        )
                    }
                }
                val scannedBarcodes = barcodes.mapNotNull { convertBarcode(it) }

                if (lines.isEmpty() && scannedBarcodes.isEmpty()) {
                    toast(getString(R.string.no_text_found))
                    return@launch
                }

                val clusters = withContext(Dispatchers.Default) {
                    detectClusters(uri, lines, image.width, image.height)
                }
                val records = buildRecords(clusters, scannedBarcodes)
                when (records.size) {
                    0 -> toast(getString(R.string.no_text_found))
                    1 -> startActivity(
                        Intent(this@MainActivity, EditContactActivity::class.java)
                            .putExtra(EditContactActivity.EXTRA_PREFILL_JSON, records[0].toJson().toString())
                    )
                    else -> saveMultiple(records)
                }
            } catch (e: Exception) {
                toast(getString(R.string.scan_failed, e.localizedMessage ?: ""))
            } finally {
                binding.progress.visibility = View.GONE
                setButtonsEnabled(true)
            }
        }
    }

    /**
     * Kart kümelerini belirler. Önce görüntüden kart dikdörtgenlerini bulmayı
     * dener (açık kart / koyu zemin kontrastı); bu, kartın içindeki beyaz
     * boşlukların kartı bölmesini önler ve her kartı tüm satırlarıyla korur.
     * Kontrast yetersizse metin-kutusu tabanlı ayırmaya geri düşer.
     */
    private fun detectClusters(
        uri: Uri,
        lines: List<OcrLine>,
        imageWidth: Int,
        imageHeight: Int
    ): List<List<OcrLine>> {
        if (lines.isEmpty()) return listOf(emptyList())
        val groups = runCatching {
            CardRegionDetector.detectAndGroup(this, uri, imageWidth, imageHeight, lines)
        }.getOrNull()
        if (groups != null && groups.size >= 2) return groups
        if (groups != null && groups.size == 1) return groups
        return CardSegmenter.segment(lines, imageWidth, imageHeight)
    }

    /** Her kümeyi ayrı kart olarak çözümler, karekodları ilgili karta ekler. */
    private fun buildRecords(
        clusters: List<List<OcrLine>>,
        barcodes: List<ScannedBarcode>
    ): List<ContactRecord> {
        return clusters.mapIndexedNotNull { index, cluster ->
            val parsed = CardTextParser.parse(cluster)
            // Karekodu içeren/ en yakın kümeye ata (tek küme varsa hepsi ona gider)
            val assigned = if (clusters.size == 1) barcodes
            else barcodes.filter { nearestClusterIndex(it, clusters) == index }

            val enriched = ScanEnricher.enrich(parsed, assigned)
            val card = enriched.card
            val notes = listOf(card.rawText, enriched.extraNotes)
                .filter { it.isNotBlank() }.joinToString("\n")

            if (card.name.isBlank() && card.company.isBlank() &&
                card.phones.isEmpty() && card.emails.isEmpty()
            ) return@mapIndexedNotNull null

            ContactRecord(
                name = card.name,
                title = card.title,
                company = card.company,
                phones = card.phones,
                emails = card.emails,
                website = card.website,
                address = card.address,
                notes = notes
            )
        }
    }

    private fun saveMultiple(records: List<ContactRecord>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                records.forEach { ContactRepository.upsert(this@MainActivity, it) }
                ExportManager.regenerateExcel(this@MainActivity)
            }
            refreshList()
            toast(getString(R.string.multiple_cards_saved, records.size))
        }
    }

    /** Karekod merkezini içeren kümeyi, yoksa merkezi en yakın kümeyi bulur. */
    private fun nearestClusterIndex(barcode: ScannedBarcode, clusters: List<List<OcrLine>>): Int {
        var bestIndex = 0
        var bestDistance = Long.MAX_VALUE
        clusters.forEachIndexed { index, cluster ->
            if (cluster.isEmpty()) return@forEachIndexed
            val left = cluster.minOf { it.left }
            val top = cluster.minOf { it.top }
            val right = cluster.maxOf { it.right }
            val bottom = cluster.maxOf { it.bottom }
            if (barcode.centerX in left..right && barcode.centerY in top..bottom) return index
            val cx = (left + right) / 2L
            val cy = (top + bottom) / 2L
            val dx = cx - barcode.centerX
            val dy = cy - barcode.centerY
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** ML Kit barkodunu, çözümlemeden bağımsız [ScannedBarcode] modeline çevirir. */
    private fun convertBarcode(barcode: Barcode): ScannedBarcode? {
        val raw = barcode.rawValue ?: barcode.displayValue ?: return null
        val box = barcode.boundingBox
        val info = barcode.contactInfo
        val contact = if (info != null && barcode.valueType == Barcode.TYPE_CONTACT_INFO) {
            ScannedContact(
                name = info.name?.formattedName.orEmpty(),
                title = info.title.orEmpty(),
                org = info.organization.orEmpty(),
                phones = info.phones.mapNotNull { p ->
                    p.number?.let { TypedPhone(it, barcodePhoneType(p.type)) }
                },
                emails = info.emails.mapNotNull { it.address },
                urls = info.urls.orEmpty(),
                address = info.addresses.flatMap { it.addressLines.toList() }.joinToString(", ")
            )
        } else null
        val url = if (barcode.valueType == Barcode.TYPE_URL) barcode.url?.url ?: raw else null
        return ScannedBarcode(
            rawValue = raw,
            url = url,
            contact = contact,
            left = box?.left ?: 0,
            top = box?.top ?: 0,
            right = box?.right ?: 0,
            bottom = box?.bottom ?: 0
        )
    }

    private fun barcodePhoneType(type: Int): PhoneType = when (type) {
        Barcode.Phone.TYPE_MOBILE -> PhoneType.MOBILE
        Barcode.Phone.TYPE_FAX -> PhoneType.FAX
        Barcode.Phone.TYPE_HOME -> PhoneType.HOME
        Barcode.Phone.TYPE_WORK -> PhoneType.WORK
        else -> PhoneType.OTHER
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnCamera.isEnabled = enabled
        binding.btnGallery.isEnabled = enabled
    }

    private fun refreshList() {
        val all = ContactRepository.getAll(this)
        val records = when (val filter = categoryFilter) {
            null -> all
            "" -> all.filter { it.category.isBlank() }
            else -> all.filter { it.category == filter }
        }
        adapter.submit(records)
        binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        supportActionBar?.subtitle = when (val filter = categoryFilter) {
            null -> null
            "" -> getString(R.string.filter_uncategorized)
            else -> filter
        }
    }

    private fun showCategoryFilterDialog() {
        val categories = ContactRepository.getAll(this)
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val labels = mutableListOf(getString(R.string.filter_all), getString(R.string.filter_uncategorized))
        labels.addAll(categories)
        val checked = when (val filter = categoryFilter) {
            null -> 0
            "" -> 1
            else -> (categories.indexOf(filter) + 2).coerceAtLeast(0)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_filter_category)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                categoryFilter = when (which) {
                    0 -> null
                    1 -> ""
                    else -> categories[which - 2]
                }
                refreshList()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareVcf() {
        if (ContactRepository.getAll(this).isEmpty()) {
            toast(getString(R.string.no_records_yet))
            return
        }
        lifecycleScope.launch {
            val chooser = withContext(Dispatchers.IO) {
                ExportManager.buildVcfShareIntent(this@MainActivity)
            }
            startActivity(chooser)
        }
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
                        ExportManager.regenerateExcel(this@MainActivity)
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
                ExportManager.buildEmailIntent(this@MainActivity, recipientEmail())
            }
            startActivity(chooser)
        }
    }

    private fun saveExcelToDownloads() {
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching { ExportManager.saveToDownloads(this@MainActivity) }.getOrNull()
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

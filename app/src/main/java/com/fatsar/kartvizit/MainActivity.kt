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
import com.fatsar.kartvizit.data.ProfileStore
import com.fatsar.kartvizit.databinding.ActivityMainBinding
import com.fatsar.kartvizit.export.CloudBackup
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
import com.google.android.material.chip.Chip
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ContactsAdapter

    private var cameraImageUri: Uri? = null
    private var pendingContactAdd: ContactRecord? = null

    /** Seçili profil (üstteki sekme). null = henüz seçilmedi → ilk profil. */
    private var categoryFilter: String? = null

    /** Profil çipi görünüm kimliği → profil adı eşlemesi. */
    private val chipIdToProfile = mutableMapOf<Int, String>()

    /** Sekmeler yeniden kurulurken seçim geri çağırmalarını yok say. */
    private var updatingTabs = false

    /** Klasör seçildikten sonra otomatik yedeklemeyi açmayı bekliyor mu? */
    private var pendingEnableAuto = false

    /** Arşiv görünümü: ana listede yalnızca son taramalar tutulur. */
    private var showingArchive = false

    private val archiveBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitArchive()
    }

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

    /** Yedekleme klasörü (Google Drive vb.) seçimi; SAF ağaç izni kalıcılaştırılır. */
    private val pickBackupFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                pendingEnableAuto = false
                return@registerForActivityResult
            }
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            CloudBackup.setBackupFolder(this, uri)
            if (pendingEnableAuto) {
                pendingEnableAuto = false
                CloudBackup.setAutoBackup(this, true)
                invalidateOptionsMenu()
                toast(getString(R.string.auto_backup_on))
            }
            runBackup()
        }

    /** Geri yüklenecek yedek dosyası (.zip / .json) seçimi. */
    private val pickBackupToRestore =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmRestore(uri)
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
        binding.btnArchive.setOnClickListener {
            showingArchive = true
            refreshList()
        }

        // Profil butonu seçimi: listeyi o profile göre grupla
        binding.profileChips.setOnCheckedStateChangeListener { _, checkedIds ->
            if (updatingTabs) return@setOnCheckedStateChangeListener
            val profile = checkedIds.firstOrNull()?.let { chipIdToProfile[it] }
                ?: return@setOnCheckedStateChangeListener
            categoryFilter = profile
            // Butonlar değişmedi; yeniden kurmadan yalnızca listeyi tazele
            if (showingArchive) exitArchive() else refreshList(syncTabs = false)
        }

        onBackPressedDispatcher.addCallback(this, archiveBackCallback)

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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_auto_backup)?.isChecked = CloudBackup.isAutoBackup(this)
        return super.onPrepareOptionsMenu(menu)
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
        R.id.action_backup -> {
            onBackupClicked(); true
        }
        R.id.action_auto_backup -> {
            toggleAutoBackup(item); true
        }
        R.id.action_restore -> {
            pickBackupToRestore.launch(arrayOf("*/*")); true
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
            // Görüntüyü TEK kez dik bitmap olarak çöz; aynı bitmap hem OCR'a
            // hem bölge tespitine verilir (ikinci çözme/URI yeniden açma yok).
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = withContext(Dispatchers.IO) {
                    runCatching { CardRegionDetector.decodeUpright(this@MainActivity, uri) }.getOrNull()
                }
                val image = withContext(Dispatchers.IO) {
                    val bmp = bitmap
                    if (bmp != null) InputImage.fromBitmap(bmp, 0)
                    else InputImage.fromFilePath(this@MainActivity, uri)
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
                    detectClusters(bitmap, lines, image.width, image.height)
                }
                val records = buildRecords(clusters, scannedBarcodes)
                // Taranan kartlar seçili profile (sekmeye) atanır
                val profile = activeProfile()
                records.forEach { if (it.category.isBlank()) it.category = profile }
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
                bitmap?.recycle()
                binding.progress.visibility = View.GONE
                setButtonsEnabled(true)
            }
        }
    }

    /**
     * Kart kümelerini belirler. Önce paylaşılan bitmap'ten kart dikdörtgenlerini
     * bulmayı dener (açık kart / koyu zemin kontrastı); bu, kartın içindeki beyaz
     * boşlukların ya da döndürülmüş kartın metin sütunlarının kartı bölmesini
     * önler ve her kartı tüm satırlarıyla korur. Kontrast yetersizse ya da
     * görüntü çözülemediyse metin-kutusu tabanlı ayırmaya geri düşer.
     */
    private fun detectClusters(
        bitmap: android.graphics.Bitmap?,
        lines: List<OcrLine>,
        imageWidth: Int,
        imageHeight: Int
    ): List<List<OcrLine>> {
        if (lines.isEmpty()) return listOf(emptyList())
        if (bitmap != null) {
            val groups = runCatching {
                CardRegionDetector.groupFromBitmap(bitmap, lines)
            }.getOrNull()
            if (!groups.isNullOrEmpty()) return groups
        }
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
                CloudBackup.maybeAutoBackup(this@MainActivity)
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

    /**
     * @param syncTabs sekme çubuğunu yeniden kurar. Sekmeye dokunma sırasında
     * (zaten sekme geri çağırması içindeyken) false verilir: sekmeler
     * değişmediğinden yeniden kurmak gereksizdir ve yeniden giriş sorunlarını
     * önler; yalnızca liste tazelenir.
     */
    private fun refreshList(syncTabs: Boolean = true) {
        val all = ContactRepository.getAll(this)
        // Profil butonları önce kurulur (seçili profili de doğrular)
        if (syncTabs) rebuildProfileChips(all)

        val profiles = profileList(all)
        val active = categoryFilter?.takeIf { sel -> profiles.any { it.equals(sel, ignoreCase = true) } }
            ?: profiles.first()
        categoryFilter = active
        // İlk profil, hiçbir profile atanmamış (boş kategorili) kartları da toplar;
        // böylece hiçbir kart görünmez kalmaz.
        val isFirst = active.equals(profiles.first(), ignoreCase = true)
        val filtered = all.filter { rec ->
            if (isFirst) rec.category.isBlank() || rec.category.equals(active, ignoreCase = true)
            else rec.category.equals(active, ignoreCase = true)
        }

        // Ana ekranda yalnızca son taramalar; eskiler "Önceki taramalar"da
        val records: List<ContactRecord>
        val archivedCount: Int
        if (showingArchive) {
            records = filtered.drop(MAIN_LIST_LIMIT)
            archivedCount = 0
            if (records.isEmpty()) {
                exitArchive()
                return
            }
        } else {
            records = filtered.take(MAIN_LIST_LIMIT)
            archivedCount = filtered.size - records.size
        }

        adapter.submit(records)
        binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        binding.btnArchive.visibility =
            if (!showingArchive && archivedCount > 0) View.VISIBLE else View.GONE
        if (archivedCount > 0) {
            binding.btnArchive.text = getString(R.string.btn_archive, archivedCount)
        }
        binding.scanBar.visibility = if (showingArchive) View.GONE else View.VISIBLE

        supportActionBar?.title =
            if (showingArchive) getString(R.string.archive_title) else getString(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(showingArchive)
        archiveBackCallback.isEnabled = showingArchive
        supportActionBar?.subtitle = when (val filter = categoryFilter) {
            null -> null
            "" -> getString(R.string.filter_uncategorized)
            else -> filter
        }
    }

    private fun exitArchive() {
        showingArchive = false
        refreshList()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (showingArchive) {
            exitArchive()
            return true
        }
        return super.onSupportNavigateUp()
    }

    /**
     * Gösterilecek profiller: kalıcı profil listesi (varsayılan İş/Özel +
     * kullanıcının eklediği) ile kartlarda geçen ama listede olmayan
     * kategorilerin birleşimi. Böylece hiçbir kart gruplanamadan kalmaz.
     */
    private fun profileList(all: List<ContactRecord>): List<String> {
        val stored = ProfileStore.profiles(this)
        val extras = all.map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { c -> stored.none { it.equals(c, ignoreCase = true) } }
            .sorted()
        return stored + extras
    }

    /**
     * Üstteki profil butonlarını (chip'leri) yeniden kurar: her profil için bir
     * buton + sonda "+" (yeni profil). Seçili profil işaretlenir; profile uzun
     * basınca yeniden adlandır/sil seçenekleri açılır. Arşivdeyken gizlenir.
     */
    private fun rebuildProfileChips(all: List<ContactRecord>) {
        if (showingArchive) {
            binding.profileScroll.visibility = View.GONE
            return
        }
        binding.profileScroll.visibility = View.VISIBLE

        val profiles = profileList(all)
        val active = categoryFilter?.takeIf { sel -> profiles.any { it.equals(sel, ignoreCase = true) } }
            ?: profiles.first()
        categoryFilter = active

        updatingTabs = true
        binding.profileChips.removeAllViews()
        chipIdToProfile.clear()

        var activeChipId = View.NO_ID
        profiles.forEach { profile ->
            val chip = layoutInflater
                .inflate(R.layout.view_profile_chip, binding.profileChips, false) as Chip
            chip.text = profile
            chip.id = View.generateViewId()
            chip.setOnLongClickListener { showProfileOptions(profile); true }
            chipIdToProfile[chip.id] = profile
            binding.profileChips.addView(chip)
            if (profile.equals(active, ignoreCase = true)) activeChipId = chip.id
        }

        val addChip = layoutInflater
            .inflate(R.layout.view_add_chip, binding.profileChips, false) as Chip
        addChip.setOnClickListener { showAddProfileDialog() }
        binding.profileChips.addView(addChip)

        if (activeChipId != View.NO_ID) binding.profileChips.check(activeChipId)
        updatingTabs = false
    }

    /** "+" butonu: yeni profil (sekme) oluşturur. */
    private fun showAddProfileDialog() {
        val input = TextInputEditText(this).apply {
            hint = getString(R.string.add_profile_hint)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_profile_title)
            .setMessage(R.string.add_profile_message)
            .setView(input)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) return@setPositiveButton
                if (ProfileStore.addProfile(this, name)) {
                    categoryFilter = name
                    refreshList()
                    toast(getString(R.string.profile_added, name))
                } else {
                    toast(getString(R.string.profile_exists))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Profile uzun basınca: yeniden adlandır / sil. */
    private fun showProfileOptions(profile: String) {
        val options = arrayOf(getString(R.string.profile_rename), getString(R.string.profile_delete))
        MaterialAlertDialogBuilder(this)
            .setTitle(profile)
            .setItems(options) { _, which ->
                if (which == 0) showRenameProfileDialog(profile) else confirmDeleteProfile(profile)
            }
            .show()
    }

    private fun showRenameProfileDialog(old: String) {
        val input = TextInputEditText(this).apply {
            setText(old)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_rename_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isBlank() || newName.equals(old, ignoreCase = true)) return@setPositiveButton
                if (!ProfileStore.rename(this, old, newName)) {
                    toast(getString(R.string.profile_exists))
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        ContactRepository.reassignCategory(this@MainActivity, old, newName)
                        ExportManager.regenerateExcel(this@MainActivity)
                        CloudBackup.maybeAutoBackup(this@MainActivity)
                    }
                    if (categoryFilter?.equals(old, ignoreCase = true) == true) categoryFilter = newName
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteProfile(profile: String) {
        val profiles = ProfileStore.profiles(this)
        if (profiles.size <= 1) {
            toast(getString(R.string.profile_delete_last))
            return
        }
        val fallback = profiles.first { !it.equals(profile, ignoreCase = true) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_delete)
            .setMessage(getString(R.string.profile_delete_confirm, profile, fallback))
            .setPositiveButton(R.string.profile_delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        ContactRepository.reassignCategory(this@MainActivity, profile, fallback)
                        ProfileStore.remove(this@MainActivity, profile)
                        ExportManager.regenerateExcel(this@MainActivity)
                        CloudBackup.maybeAutoBackup(this@MainActivity)
                    }
                    if (categoryFilter?.equals(profile, ignoreCase = true) == true) categoryFilter = fallback
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Yeni taranan kartların gireceği profil (seçili profil, yoksa ilki). */
    private fun activeProfile(): String =
        categoryFilter ?: ProfileStore.profiles(this).first()

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

    /**
     * Rehbere ekleme. Kişi daha önce gönderildiyse, ikinci gönderimde rehberde
     * kopya kişi oluşabileceği için önce onay istenir ("daha önce hatırlat").
     */
    private fun requestAddToContacts(record: ContactRecord) {
        if (record.addedToContacts) {
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
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            launchContactPermission(record)
        }
    }

    private fun launchContactPermission(record: ContactRecord) {
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
                record.lastSentAt = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    ContactRepository.upsert(this@MainActivity, record)
                    CloudBackup.maybeAutoBackup(this@MainActivity)
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
                        CloudBackup.maybeAutoBackup(this@MainActivity)
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

    // ---- Buluta yedekleme ----

    /** Yedekle: klasör seçilmemişse önce seçtirir, sonra yedek yazar. */
    private fun onBackupClicked() {
        if (ContactRepository.getAll(this).isEmpty()) {
            toast(getString(R.string.backup_no_records))
            return
        }
        if (CloudBackup.backupFolder(this) == null) {
            toast(getString(R.string.backup_choose_folder))
            pickBackupFolder.launch(null)
        } else {
            runBackup()
        }
    }

    private fun runBackup() {
        toast(getString(R.string.backup_running))
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching { CloudBackup.writeBackup(this@MainActivity) }.getOrNull()
            }
            toast(
                if (name != null) getString(R.string.backup_success, name)
                else getString(R.string.backup_failed)
            )
        }
    }

    private fun toggleAutoBackup(item: MenuItem) {
        val enable = !CloudBackup.isAutoBackup(this)
        if (enable && CloudBackup.backupFolder(this) == null) {
            // Klasör yoksa önce seçtir; seçilince otomatik yedekleme açılır
            pendingEnableAuto = true
            toast(getString(R.string.backup_choose_folder))
            pickBackupFolder.launch(null)
            return
        }
        CloudBackup.setAutoBackup(this, enable)
        item.isChecked = enable
        toast(getString(if (enable) R.string.auto_backup_on else R.string.auto_backup_off))
    }

    private fun confirmRestore(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_confirm_title)
            .setMessage(R.string.restore_confirm_message)
            .setPositiveButton(R.string.restore) { _, _ -> runRestore(uri) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runRestore(uri: Uri) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                val restored = CloudBackup.restore(this@MainActivity, uri)
                if (restored >= 0) ExportManager.regenerateExcel(this@MainActivity)
                restored
            }
            if (count >= 0) {
                refreshList()
                toast(getString(R.string.restore_success, count))
            } else {
                toast(getString(R.string.restore_failed))
            }
        }
    }

    private fun dateStr(timestamp: Long): String =
        SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).format(Date(timestamp))

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

        /** Ana listede tutulacak en yeni tarama sayısı; fazlası arşive düşer. */
        private const val MAIN_LIST_LIMIT = 5
    }
}

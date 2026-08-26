package com.fatsar.notlar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.fatsar.notlar.data.NoteStore
import com.fatsar.notlar.databinding.ActivityMainBinding
import com.fatsar.notlar.markdown.MarkdownEditing
import com.fatsar.notlar.markdown.MarkdownRenderer
import com.fatsar.notlar.model.Note
import com.fatsar.notlar.search.NoteSearch
import com.fatsar.notlar.ui.NoteListAdapter
import com.fatsar.notlar.ui.SketchActivity
import com.fatsar.notlar.util.TimeText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Notlar: solda liste + arama, sağda markdown düzenleyici/önizleme.
 *
 * Tüm veriler cihazda kalır (bkz. [NoteStore]); yazarken 600 ms'lik sessizlikten
 * sonra otomatik kaydedilir, uygulama arka plana alındığında da hemen yazılır.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: NoteStore
    private lateinit var adapter: NoteListAdapter
    private lateinit var renderer: MarkdownRenderer

    private val notes = ArrayList<Note>()
    private var selectedId: String? = null
    private var query = ""
    private var previewing = false
    private var updatingEditor = false
    private var pendingNewlineAt = -1

    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { persist() }

    private val sketchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val name = result.data?.getStringExtra(SketchActivity.EXTRA_SKETCH_NAME)
        val note = selectedNote() ?: return@registerForActivityResult
        updateNote(note.copy(sketchName = name?.takeIf { it.isNotBlank() }))
        showPreview(true)
        Snackbar.make(
            binding.root,
            if (name.isNullOrBlank()) R.string.pen_removed else R.string.pen_saved,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyInsets()

        store = NoteStore(this)
        renderer = MarkdownRenderer(this)
        notes.addAll(store.load())
        seedIfFirstRun()

        setUpList()
        setUpSearch()
        setUpEditor()
        setUpToolbars()
        setUpPanes()
        setUpStylus()

        handleSendIntent(intent)

        val restored = savedInstanceState?.getString(STATE_SELECTED)
            ?: prefs().getString(PREF_SELECTED, null)
        previewing = savedInstanceState?.getBoolean(STATE_PREVIEW)
            ?: prefs().getBoolean(PREF_PREVIEW, false)
        val start = notes.firstOrNull { it.id == restored }
        if (start != null) openNote(start, focusEditor = false, slide = false) else showEmptyDetail()
        refreshList()
    }

    // ---------------------------------------------------------------- kurulum

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun setUpList() {
        adapter = NoteListAdapter { note -> openNote(note, focusEditor = true, slide = true) }
        binding.noteList.layoutManager = LinearLayoutManager(this)
        binding.noteList.adapter = adapter
        binding.noteList.itemAnimator = null
        binding.newNoteButton.setOnClickListener { createNote() }
    }

    private fun setUpSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                binding.searchClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                refreshList()
            }
        })
        binding.searchClear.setOnClickListener {
            binding.searchInput.setText("")
            binding.searchInput.requestFocus()
        }
        // Aramadan aşağı ok ile listeye geç: klavyeyle gezinme
        binding.searchInput.setOnKeyListener { _, keyCode, event ->
            when {
                event.action != KeyEvent.ACTION_DOWN -> false
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                    focusListItem(0); true
                }
                keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                    binding.searchInput.setText(""); true
                }
                keyCode == KeyEvent.KEYCODE_ENTER -> {
                    adapter.currentList.firstOrNull()?.let { openNote(it, true, slide = true) }
                    true
                }
                else -> false
            }
        }
    }

    private fun setUpEditor() {
        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!updatingEditor && before == 0 && count == 1 && s != null && s[start] == '\n') {
                    pendingNewlineAt = start + 1
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (updatingEditor || s == null) return
                val newline = pendingNewlineAt
                pendingNewlineAt = -1
                if (newline >= 0) continueListAfterNewline(newline)
                onBodyChanged(binding.editor.text.toString())
            }
        })

        binding.formatHeading.setOnClickListener { applyLinePrefix("# ") }
        binding.formatBold.setOnClickListener { applyWrap("**") }
        binding.formatItalic.setOnClickListener { applyWrap("*") }
        binding.formatCode.setOnClickListener { applyWrap("`") }
        binding.formatBullet.setOnClickListener { applyLinePrefix("- ") }
        binding.formatQuote.setOnClickListener { applyLinePrefix("> ") }
        binding.formatTask.setOnClickListener { toggleTask() }
        binding.formatPen.setOnClickListener { openSketch() }
        binding.previewText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setUpToolbars() {
        binding.listToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.actionShortcuts) {
                showShortcuts(); true
            } else false
        }
        binding.editorToolbar.setNavigationOnClickListener { binding.slidingPane.closePane() }
        binding.editorToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.actionPreview -> {
                    showPreview(!previewing); true
                }
                R.id.actionPen -> {
                    openSketch(); true
                }
                R.id.actionPin -> {
                    togglePin(); true
                }
                R.id.actionShare -> {
                    shareNote(); true
                }
                R.id.actionDelete -> {
                    confirmDelete(); true
                }
                else -> false
            }
        }
    }

    private fun setUpPanes() {
        val pane = binding.slidingPane
        pane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                pane.closePane()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        pane.addPanelSlideListener(object : SlidingPaneLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) = Unit
            override fun onPanelOpened(panel: View) {
                callback.isEnabled = pane.isSlideable
            }

            override fun onPanelClosed(panel: View) {
                callback.isEnabled = false
            }
        })
        pane.doOnLayout {
            callback.isEnabled = pane.isSlideable && pane.isOpen
            // Geri oku yalnızca tek bölmeli (dar) düzende anlamlı
            binding.editorToolbar.navigationIcon =
                if (pane.isSlideable) androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)
                else null
        }
    }

    /**
     * Kalem desteği:
     * - Düzenleyiciye doğrudan el yazısıyla yazma (Android 14+ stylus handwriting).
     * - Önizlemedeyken kalemle dokunmak düzenleyiciyi açar; Android 14+'ta
     *   "handwriting delegation" ile yazı doğrudan metne akar.
     */
    private fun setUpStylus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            binding.editor.isAutoHandwritingEnabled = true
            binding.editor.setIsHandwritingDelegate(true)
            binding.previewText.setHandwritingDelegatorCallback { switchToEditorForPen() }
            binding.previewScroll.setHandwritingDelegatorCallback { switchToEditorForPen() }
        }
        // Sürüm fark etmeksizin: önizlemeye kalemle dokunmak düzenlemeye geçirir
        binding.previewScroll.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS &&
                previewing
            ) {
                switchToEditorForPen()
            }
            false
        }
    }

    private fun switchToEditorForPen() {
        if (selectedId == null) return
        showPreview(false)
        binding.editor.requestFocus()
        binding.editor.setSelection(binding.editor.text?.length ?: 0)
        Snackbar.make(binding.root, R.string.handwriting_ready, Snackbar.LENGTH_SHORT).show()
    }

    private fun seedIfFirstRun() {
        if (notes.isNotEmpty() || prefs().getBoolean(PREF_SEEDED, false)) return
        val now = System.currentTimeMillis()
        notes.add(store.newNote(now, getString(R.string.welcome_note)))
        prefs().edit().putBoolean(PREF_SEEDED, true).apply()
        persist()
    }

    // ------------------------------------------------------------------ liste

    private fun refreshList() {
        val visible = NoteSearch.filter(notes, query)
        adapter.query = query
        adapter.selectedId = selectedId
        adapter.submitList(visible)

        val hasQuery = query.isNotBlank()
        binding.noteCount.text = if (hasQuery) {
            getString(R.string.note_count_filtered, visible.size, notes.size)
        } else {
            getString(R.string.note_count, notes.size)
        }
        val empty = visible.isEmpty()
        binding.emptyList.visibility = if (empty) View.VISIBLE else View.GONE
        binding.noteList.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            binding.emptyTitle.text =
                if (hasQuery) getString(R.string.no_results, query) else getString(R.string.empty_list)
            binding.emptyHint.text =
                if (hasQuery) getString(R.string.no_results_hint) else getString(R.string.empty_list_hint)
        }
    }

    private fun focusListItem(position: Int) {
        binding.noteList.post {
            val holder = binding.noteList.findViewHolderForAdapterPosition(position)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else if (adapter.itemCount > position) {
                binding.noteList.scrollToPosition(position)
            }
        }
    }

    // -------------------------------------------------------------- düzenleme

    private fun selectedNote(): Note? = notes.firstOrNull { it.id == selectedId }

    private fun createNote(body: String = "") {
        val note = store.newNote(body = body)
        notes.add(note)
        persist()
        binding.searchInput.setText("")
        refreshList()
        openNote(note, focusEditor = true, slide = true)
    }

    private fun openNote(note: Note, focusEditor: Boolean, slide: Boolean) {
        selectedId = note.id
        adapter.selectedId = note.id
        prefs().edit().putString(PREF_SELECTED, note.id).apply()

        updatingEditor = true
        binding.editor.setText(note.body)
        binding.editor.setSelection(note.body.length)
        updatingEditor = false

        binding.emptyDetail.visibility = View.GONE
        binding.editorToolbar.visibility = View.VISIBLE
        binding.editorToolbar.menu.setGroupVisible(0, true)
        showPreview(previewing)
        updateToolbarForNote(note)
        refreshList()

        if (slide) binding.slidingPane.open()
        if (focusEditor && !previewing) {
            binding.editor.requestFocus()
            if (binding.slidingPane.isSlideable) showKeyboard(binding.editor)
        }
    }

    private fun showEmptyDetail() {
        selectedId = null
        adapter.selectedId = null
        binding.emptyDetail.visibility = View.VISIBLE
        binding.editor.visibility = View.GONE
        binding.previewScroll.visibility = View.GONE
        binding.formatBar.visibility = View.GONE
        binding.editorToolbar.title = ""
        binding.editorToolbar.subtitle = ""
        binding.editorToolbar.menu.setGroupVisible(0, false)
    }

    private fun onBodyChanged(body: String) {
        val note = selectedNote() ?: return
        if (note.body == body) return
        val updated = note.copy(body = body, updatedAt = System.currentTimeMillis())
        replaceNote(updated)
        updateToolbarForNote(updated)
        refreshList()
        scheduleSave()
    }

    private fun updateNote(note: Note) {
        replaceNote(note)
        updateToolbarForNote(note)
        refreshList()
        persist()
    }

    private fun replaceNote(note: Note) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) notes[index] = note else notes.add(note)
    }

    private fun updateToolbarForNote(note: Note) {
        binding.editorToolbar.title = note.title.ifBlank { getString(R.string.untitled) }
        binding.editorToolbar.subtitle =
            getString(R.string.edited_at, TimeText.format(this, note.updatedAt))
        binding.editorToolbar.menu.findItem(R.id.actionPin)?.setTitle(
            if (note.pinned) R.string.action_unpin else R.string.action_pin
        )
        binding.editorToolbar.menu.findItem(R.id.actionPreview)?.setTitle(
            if (previewing) R.string.action_edit else R.string.action_preview
        )
        binding.editorToolbar.menu.findItem(R.id.actionPreview)?.setIcon(
            if (previewing) R.drawable.ic_edit else R.drawable.ic_preview
        )
    }

    private fun showPreview(preview: Boolean) {
        previewing = preview
        prefs().edit().putBoolean(PREF_PREVIEW, preview).apply()
        val note = selectedNote()
        if (note == null) {
            showEmptyDetail()
            return
        }
        binding.editor.visibility = if (preview) View.GONE else View.VISIBLE
        binding.formatBar.visibility = if (preview) View.GONE else View.VISIBLE
        binding.previewScroll.visibility = if (preview) View.VISIBLE else View.GONE
        if (preview) {
            binding.previewText.text = renderer.render(note.body)
            showSketch(note)
            hideKeyboard()
        }
        updateToolbarForNote(note)
    }

    private fun showSketch(note: Note) {
        val name = note.sketchName
        val file = if (name == null) null else store.sketchImageFile(name)
        val bitmap = if (file != null && file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (bitmap == null) {
            binding.previewSketch.visibility = View.GONE
            binding.previewSketch.setImageDrawable(null)
        } else {
            binding.previewSketch.setImageBitmap(bitmap)
            binding.previewSketch.visibility = View.VISIBLE
        }
    }

    private fun openSketch() {
        val note = selectedNote() ?: return
        sketchLauncher.launch(SketchActivity.intent(this, note.sketchName, note.title))
    }

    private fun togglePin() {
        val note = selectedNote() ?: return
        updateNote(note.copy(pinned = !note.pinned))
    }

    private fun shareNote() {
        val note = selectedNote() ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { getString(R.string.untitled) })
            .putExtra(Intent.EXTRA_TEXT, note.body)
        startActivity(Intent.createChooser(intent, getString(R.string.share_note)))
    }

    private fun confirmDelete() {
        val note = selectedNote() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(
                getString(R.string.delete_message, note.title.ifBlank { getString(R.string.untitled) })
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_confirm) { _, _ -> deleteNote(note) }
            .show()
    }

    private fun deleteNote(note: Note) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index < 0) return
        notes.removeAt(index)
        persist()
        showEmptyDetail()
        refreshList()
        Snackbar.make(binding.root, R.string.note_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                notes.add(note)
                persist()
                refreshList()
                openNote(note, focusEditor = false, slide = false)
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    // Geri alınmadıysa çizim dosyaları da temizlenir
                    if (event != DISMISS_EVENT_ACTION && notes.none { it.id == note.id }) {
                        store.deleteSketch(note.sketchName)
                    }
                }
            })
            .show()
    }

    // --------------------------------------------------------- markdown yardımı

    private fun applyWrap(marker: String) {
        val editor = binding.editor
        val edit = MarkdownEditing.toggleWrap(
            editor.text.toString(),
            editor.selectionStart,
            editor.selectionEnd,
            marker
        )
        applyEdit(edit)
    }

    private fun applyLinePrefix(prefix: String) {
        val editor = binding.editor
        val edit = MarkdownEditing.togglePrefix(
            editor.text.toString(),
            editor.selectionStart,
            editor.selectionEnd,
            prefix
        )
        applyEdit(edit)
    }

    private fun toggleTask() {
        val editor = binding.editor
        val edit = MarkdownEditing.toggleTask(editor.text.toString(), editor.selectionStart) ?: return
        applyEdit(edit)
    }

    private fun insertLink() {
        val editor = binding.editor
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val text = editor.text.toString()
        val label = text.substring(start, end)
        val replacement = "[$label](https://)"
        applyEdit(
            MarkdownEditing.Edit(
                text.substring(0, start) + replacement + text.substring(end),
                start + label.length + 3,
                start + replacement.length - 1
            )
        )
    }

    /**
     * Enter'dan sonra listeyi sürdürür. Metin, TextWatcher'ın içinden değil
     * hemen sonrasında değiştirilir; böylece yazma sırasında yeniden giriş
     * (reentrancy) sorunları oluşmaz.
     */
    private fun continueListAfterNewline(cursor: Int) {
        binding.editor.post {
            val edit = MarkdownEditing.afterNewline(binding.editor.text.toString(), cursor) ?: return@post
            applyEdit(edit)
        }
    }

    private fun applyEdit(edit: MarkdownEditing.Edit) {
        val editor = binding.editor
        updatingEditor = true
        editor.setText(edit.text)
        editor.setSelection(
            edit.selectionStart.coerceIn(0, edit.text.length),
            edit.selectionEnd.coerceIn(0, edit.text.length)
        )
        updatingEditor = false
        onBodyChanged(edit.text)
    }

    // ------------------------------------------------------------ kalıcılık

    private fun scheduleSave() {
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, AUTOSAVE_DELAY_MS)
    }

    private fun persist() {
        saveHandler.removeCallbacks(saveRunnable)
        store.save(notes)
    }

    override fun onPause() {
        super.onPause()
        persist()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selectedId)
        outState.putBoolean(STATE_PREVIEW, previewing)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    /** Başka uygulamalardan "Paylaş → Notlar" ile gelen metni yeni nota çevirir. */
    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared.isEmpty()) return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val body = if (subject.isEmpty()) shared else "# $subject\n\n$shared"
        intent.action = null
        createNote(body)
    }

    // -------------------------------------------------------------- klavye

    /**
     * Klavye kısayolları. EditText odaktayken de çalışsın diye tuşlar
     * gönderilmeden yakalanır (Ctrl kombinasyonları metne yazı eklemez).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_N -> {
                    createNote(); return true
                }
                KeyEvent.KEYCODE_F -> {
                    binding.slidingPane.closePane()
                    binding.searchInput.requestFocus()
                    showKeyboard(binding.searchInput)
                    return true
                }
                KeyEvent.KEYCODE_P -> {
                    if (selectedId != null) showPreview(!previewing)
                    return true
                }
                KeyEvent.KEYCODE_E -> {
                    if (selectedId != null) {
                        showPreview(false)
                        binding.editor.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_B -> {
                    if (binding.editor.hasFocus()) applyWrap("**")
                    return true
                }
                KeyEvent.KEYCODE_I -> {
                    if (binding.editor.hasFocus()) applyWrap("*")
                    return true
                }
                KeyEvent.KEYCODE_K -> {
                    if (binding.editor.hasFocus()) {
                        if (event.isShiftPressed) toggleTask() else insertLink()
                    }
                    return true
                }
                KeyEvent.KEYCODE_L -> {
                    if (binding.editor.hasFocus()) applyLinePrefix("- ")
                    return true
                }
                KeyEvent.KEYCODE_D -> {
                    confirmDelete(); return true
                }
                KeyEvent.KEYCODE_S -> {
                    persist()
                    Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
                    return true
                }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            when {
                binding.searchInput.hasFocus() && query.isNotEmpty() -> binding.searchInput.setText("")
                binding.slidingPane.isSlideable && binding.slidingPane.isOpen ->
                    binding.slidingPane.closePane()
                else -> {
                    hideKeyboard()
                    focusListItem(0)
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showShortcuts() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.shortcuts_title)
            .setMessage(
                androidx.core.text.HtmlCompat.fromHtml(
                    getString(R.string.shortcuts_body),
                    androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
                )
            )
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showKeyboard(view: View) {
        view.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private companion object {
        const val AUTOSAVE_DELAY_MS = 600L
        const val PREFS = "notlar"
        const val PREF_SELECTED = "secili_not"
        const val PREF_PREVIEW = "onizleme"
        const val PREF_SEEDED = "ilk_not_olusturuldu"
        const val STATE_SELECTED = "state_selected"
        const val STATE_PREVIEW = "state_preview"
    }
}

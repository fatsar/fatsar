package com.fatsar.notlar.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fatsar.notlar.R
import com.fatsar.notlar.data.NoteStore
import com.fatsar.notlar.data.StrokeSerializer
import com.fatsar.notlar.databinding.ActivitySketchBinding
import com.fatsar.notlar.pen.PenTool
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.FileOutputStream

/**
 * Notun kalem katmanı: S Pen (ve diğer stylus'lar) ile basınç duyarlı yazı/çizim.
 * Çizgiler hem vektör (JSON) hem de önizlemede gösterilen PNG olarak saklanır.
 */
class SketchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySketchBinding
    private lateinit var store: NoteStore
    private var sketchName: String = ""
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySketchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        store = NoteStore(this)
        sketchName = intent.getStringExtra(EXTRA_SKETCH_NAME).takeUnless { it.isNullOrBlank() }
            ?: store.newSketchName()
        intent.getStringExtra(EXTRA_NOTE_TITLE)?.takeIf { it.isNotBlank() }?.let {
            binding.sketchToolbar.subtitle = it
        }

        binding.canvas.penColor = ContextCompat.getColor(this, R.color.pen_ink)
        binding.canvas.onStrokesChanged = {
            dirty = true
            invalidateOptionsMenu()
        }
        binding.canvas.onStylusDetected = {
            Snackbar.make(binding.root, R.string.pen_stylus_detected, Snackbar.LENGTH_SHORT).show()
        }

        setUpTools()
        loadExisting()

        binding.sketchToolbar.setNavigationOnClickListener { confirmDiscard() }
        binding.sketchToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.actionUndo -> {
                    binding.canvas.undo(); true
                }
                R.id.actionRedo -> {
                    binding.canvas.redo(); true
                }
                R.id.actionClearSketch -> {
                    binding.canvas.clear(); true
                }
                R.id.actionSaveSketch -> {
                    saveAndFinish(); true
                }
                else -> false
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmDiscard()
        })
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setUpTools() {
        binding.toolGroup.check(R.id.toolPen)
        binding.toolGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.canvas.tool = when (checkedId) {
                R.id.toolHighlighter -> PenTool.HIGHLIGHTER
                R.id.toolEraser -> PenTool.ERASER
                else -> PenTool.PEN
            }
        }
        binding.widthSlider.addOnChangeListener { _, value, _ ->
            binding.canvas.penWidthDp = value
        }
        binding.stylusOnlySwitch.setOnCheckedChangeListener { _, checked ->
            binding.canvas.stylusOnly = checked
        }

        val swatches = listOf(
            binding.colorInk to R.color.pen_ink,
            binding.colorRed to R.color.pen_red,
            binding.colorBlue to R.color.pen_blue,
            binding.colorGreen to R.color.pen_green,
            binding.colorYellow to R.color.pen_yellow
        )
        for ((view, colorRes) in swatches) {
            view.setOnClickListener {
                binding.canvas.penColor = ContextCompat.getColor(this, colorRes)
                swatches.forEach { (other, _) -> other.isSelected = other === view }
                // Sarı seçilince fosforlu kaleme geçmek beklenen davranış
                if (colorRes == R.color.pen_yellow && binding.canvas.tool == PenTool.PEN) {
                    binding.toolGroup.check(R.id.toolHighlighter)
                }
            }
        }
        binding.colorInk.isSelected = true
    }

    private fun loadExisting() {
        val file = store.sketchStrokeFile(sketchName)
        if (!file.exists()) return
        val sketch = StrokeSerializer.fromJson(runCatching { file.readText() }.getOrDefault(""))
        if (sketch.strokes.isEmpty()) return
        // Tuval ölçüsü ilk yerleşimden sonra bilinir
        binding.canvas.post {
            binding.canvas.load(sketch.strokes, sketch.width, sketch.height)
            dirty = false
        }
    }

    private fun saveAndFinish() {
        val canvas = binding.canvas
        val result = Intent()
        if (canvas.isEmpty) {
            store.deleteSketch(sketchName)
            result.putExtra(EXTRA_SKETCH_NAME, "")
        } else {
            runCatching {
                store.sketchStrokeFile(sketchName).writeText(
                    StrokeSerializer.toJson(canvas.strokesSnapshot(), canvas.width, canvas.height)
                )
                canvas.exportBitmap()?.let { bitmap ->
                    FileOutputStream(store.sketchImageFile(sketchName)).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                }
            }.onFailure {
                Snackbar.make(binding.root, it.message ?: "", Snackbar.LENGTH_LONG).show()
            }
            result.putExtra(EXTRA_SKETCH_NAME, sketchName)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun confirmDiscard() {
        if (!dirty) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pen_discard)
            .setPositiveButton(R.string.pen_save) { _, _ -> saveAndFinish() }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.pen_discard) { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()
    }

    /** Klavye/DeX kullanıcıları için: Ctrl+Z geri al, Ctrl+Shift+Z ileri al, Ctrl+S kaydet. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) binding.canvas.redo() else binding.canvas.undo()
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    saveAndFinish()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_SKETCH_NAME = "sketch_name"
        private const val EXTRA_NOTE_TITLE = "note_title"

        fun intent(context: Context, sketchName: String?, noteTitle: String?): Intent =
            Intent(context, SketchActivity::class.java)
                .putExtra(EXTRA_SKETCH_NAME, sketchName)
                .putExtra(EXTRA_NOTE_TITLE, noteTitle)
    }
}

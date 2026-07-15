package com.fatsar.toplanti.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleCoroutineScope
import com.fatsar.toplanti.R
import com.fatsar.toplanti.asr.VoskModelManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Türkçe tanıma modeli kuruluysa doğrudan devam eder; değilse kullanıcı
 * onayıyla indirir (tek seferlik, ~35 MB). İndirme başarısız olursa kullanıcı
 * modeli tarayıcısıyla indirip "ZIP seç" ile elle kurabilir. İndirme dışında
 * hiçbir ağ erişimi yapılmaz.
 */
object ModelDownloadHelper {

    fun ensureModel(activity: ComponentActivity, scope: LifecycleCoroutineScope, onReady: () -> Unit) {
        val manager = VoskModelManager(activity)
        if (manager.isInstalled()) {
            onReady()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.model_download_title)
            .setMessage(activity.getString(R.string.model_download_message, VoskModelManager.MODEL_SIZE_MB))
            .setPositiveButton(R.string.model_download_start) { _, _ ->
                runDownload(activity, scope, manager, onReady)
            }
            .setNeutralButton(R.string.model_pick_zip) { _, _ ->
                pickLocalZip(activity, scope, manager, onReady)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runDownload(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        onReady: () -> Unit
    ) {
        runWithProgress(activity, scope, onReady,
            work = { onProgress -> manager.download(onProgress) },
            onFailure = { e -> showFailure(activity, scope, manager, onReady, e) })
    }

    /** Kullanıcının tarayıcıyla indirdiği model ZIP'ini seçtirip kurar. */
    private fun pickLocalZip(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        onReady: () -> Unit
    ) {
        var launcher: ActivityResultLauncher<Array<String>>? = null
        launcher = activity.activityResultRegistry.register(
            "model_zip_picker", ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            launcher?.unregister()
            if (uri == null) return@register
            runWithProgress(activity, scope, onReady,
                work = { onProgress ->
                    activity.contentResolver.openInputStream(uri)?.use { ins ->
                        manager.installFromStream(ins, onProgress)
                    } ?: throw IOException(activity.getString(R.string.model_zip_open_failed))
                },
                onFailure = { e -> showFailure(activity, scope, manager, onReady, e) })
        }
        launcher.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    private fun runWithProgress(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        onReady: () -> Unit,
        work: (onProgress: (Int) -> Unit) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = false
        }
        val label = TextView(activity).apply { text = activity.getString(R.string.model_downloading, 0) }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(label)
            addView(
                progressBar,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.model_download_title)
            .setView(box)
            .setCancelable(false)
            .show()

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    work { p ->
                        activity.runOnUiThread {
                            progressBar.progress = p
                            label.text = activity.getString(R.string.model_downloading, p)
                        }
                    }
                }
            }
            dialog.dismiss()
            result.fold(onSuccess = { onReady() }, onFailure = onFailure)
        }
    }

    private fun showFailure(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        onReady: () -> Unit,
        e: Throwable
    ) {
        val detail = e.message ?: e.javaClass.simpleName
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.model_download_failed)
            .setMessage(
                detail + "\n\n" +
                    activity.getString(R.string.model_manual_hint, VoskModelManager.MODEL_URL)
            )
            .setPositiveButton(R.string.retry) { _, _ ->
                runDownload(activity, scope, manager, onReady)
            }
            .setNeutralButton(R.string.model_pick_zip) { _, _ ->
                pickLocalZip(activity, scope, manager, onReady)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

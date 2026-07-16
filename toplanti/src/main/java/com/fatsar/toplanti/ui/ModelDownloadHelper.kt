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
 * Gerekli tanıma modellerinin kullanılabilir olmasını sağlar. Modeller
 * normalde APK içinde paketlenmiş gelir; bu durumda hiçbir şey sorulmaz
 * (kopyalama, işleme servisinde yapılır). Paket yoksa (ör. geliştirici
 * derlemesi) indirme veya elle ZIP kurulumu önerilir. İndirme dışında
 * hiçbir ağ erişimi yapılmaz.
 */
object ModelDownloadHelper {

    /** [languages]: "tr"/"en" listesi. Hepsi hazırsa doğrudan [onReady]. */
    fun ensureModels(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        languages: List<String>,
        onReady: () -> Unit
    ) {
        val manager = VoskModelManager(activity)
        val missing = languages.filter { !manager.isReady(it) }
        if (missing.isEmpty()) {
            onReady()
            return
        }
        askAndInstall(activity, scope, manager, missing, onReady)
    }

    private fun askAndInstall(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        missing: List<String>,
        onReady: () -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.model_download_title)
            .setMessage(
                activity.getString(
                    R.string.model_download_message,
                    missing.size * VoskModelManager.MODEL_SIZE_MB
                )
            )
            .setPositiveButton(R.string.model_download_start) { _, _ ->
                runDownload(activity, scope, manager, missing, onReady)
            }
            .setNeutralButton(R.string.model_pick_zip) { _, _ ->
                pickLocalZip(activity, scope, manager, missing, onReady)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runDownload(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        missing: List<String>,
        onReady: () -> Unit
    ) {
        runWithProgress(activity, scope,
            work = { onProgress ->
                missing.forEachIndexed { idx, lang ->
                    manager.download(lang) { p ->
                        onProgress((idx * 100 + p) / missing.size)
                    }
                }
            },
            onSuccess = onReady,
            onFailure = { e -> showFailure(activity, scope, manager, missing, onReady, e) })
    }

    /** Kullanıcının tarayıcıyla indirdiği model ZIP'ini seçtirip kurar. */
    private fun pickLocalZip(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        missing: List<String>,
        onReady: () -> Unit
    ) {
        var launcher: ActivityResultLauncher<Array<String>>? = null
        launcher = activity.activityResultRegistry.register(
            "model_zip_picker", ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            launcher?.unregister()
            if (uri == null) return@register
            runWithProgress(activity, scope,
                work = { onProgress ->
                    activity.contentResolver.openInputStream(uri)?.use { ins ->
                        manager.installFromStream(ins, onProgress)
                    } ?: throw IOException(activity.getString(R.string.model_zip_open_failed))
                },
                onSuccess = {
                    // Kurulan ZIP tek dil içindir; hâlâ eksik dil varsa devam et
                    val stillMissing = missing.filter { !manager.isReady(it) }
                    if (stillMissing.isEmpty()) onReady()
                    else askAndInstall(activity, scope, manager, stillMissing, onReady)
                },
                onFailure = { e -> showFailure(activity, scope, manager, missing, onReady, e) })
        }
        launcher.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    private fun runWithProgress(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        work: (onProgress: (Int) -> Unit) -> Unit,
        onSuccess: () -> Unit,
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
            result.fold(onSuccess = { onSuccess() }, onFailure = onFailure)
        }
    }

    private fun showFailure(
        activity: ComponentActivity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        missing: List<String>,
        onReady: () -> Unit,
        e: Throwable
    ) {
        val detail = e.message ?: e.javaClass.simpleName
        val urls = missing.joinToString("\n") {
            "${VoskModelManager.MODEL_BASE_URL}/${manager.modelName(it)}.zip"
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.model_download_failed)
            .setMessage(detail + "\n\n" + activity.getString(R.string.model_manual_hint, urls))
            .setPositiveButton(R.string.retry) { _, _ ->
                runDownload(activity, scope, manager, missing, onReady)
            }
            .setNeutralButton(R.string.model_pick_zip) { _, _ ->
                pickLocalZip(activity, scope, manager, missing, onReady)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

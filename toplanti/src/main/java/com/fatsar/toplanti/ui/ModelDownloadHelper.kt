package com.fatsar.toplanti.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.fatsar.toplanti.R
import com.fatsar.toplanti.asr.VoskModelManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Türkçe tanıma modeli kuruluysa doğrudan devam eder; değilse kullanıcı
 * onayıyla indirir (tek seferlik, ~45 MB). İndirme dışında hiçbir ağ
 * erişimi yapılmaz.
 */
object ModelDownloadHelper {

    fun ensureModel(activity: Activity, scope: LifecycleCoroutineScope, onReady: () -> Unit) {
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runDownload(
        activity: Activity,
        scope: LifecycleCoroutineScope,
        manager: VoskModelManager,
        onReady: () -> Unit
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
                    manager.download { p ->
                        activity.runOnUiThread {
                            progressBar.progress = p
                            label.text = activity.getString(R.string.model_downloading, p)
                        }
                    }
                }
            }
            dialog.dismiss()
            result.fold(
                onSuccess = { onReady() },
                onFailure = { e ->
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.model_download_failed)
                        .setMessage(e.message ?: e.javaClass.simpleName)
                        .setPositiveButton(R.string.retry) { _, _ ->
                            runDownload(activity, scope, manager, onReady)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            )
        }
    }
}

package com.fatsar.kartvizit.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Android 15 (API 35) ile birlikte uygulamalar varsayılan olarak **kenardan
 * kenara** çizer: içerik durum ve gezinme çubuklarının arkasına taşar. Bu
 * yardımcı, sistem çubuklarının kapladığı alanı iç boşluğa çevirerek başlığın
 * saatin/simgelerin altında kalmasını ve alttaki içeriğin gezinme çubuğuna
 * girmesini önler.
 */
object SystemBars {

    /**
     * @param root boşlukların dinleneceği kök görünüm
     * @param header üstten durum çubuğu kadar iç boşluk alacak görünüm
     *        (arka planı çubuğun arkasını da boyamaya devam eder)
     * @param bottomPadded alttan gezinme çubuğu kadar iç boşluk alacak görünüm
     * @param bottomMargin alt kenar boşluğu gezinme çubuğu kadar artacak görünüm
     */
    fun apply(
        root: View,
        header: View? = null,
        bottomPadded: View? = null,
        bottomMargin: View? = null
    ) {
        // Taban değerler bir kez saklanır: dinleyici birden çok kez çağrıldığında
        // boşluklar üst üste eklenmesin.
        val baseTop = header?.paddingTop ?: 0
        val basePaddingBottom = bottomPadded?.paddingBottom ?: 0
        val baseMarginBottom =
            (bottomMargin?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header?.updatePadding(top = baseTop + bars.top)
            bottomPadded?.updatePadding(bottom = basePaddingBottom + bars.bottom)
            (bottomMargin?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.bottomMargin = baseMarginBottom + bars.bottom
                bottomMargin.layoutParams = params
            }
            insets
        }
    }
}

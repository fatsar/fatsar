package com.fatsar.kartvizit.data

import android.content.Context
import com.fatsar.kartvizit.R

/**
 * Kullanıcının seçtiği renk temasını saklar. Üç hazır tema vardır ve her biri
 * kendi açık/koyu varyantıyla gelir (sistem karanlık moduna göre otomatik
 * değişir). Seçim cihazda tutulur; internet gerekmez.
 */
object ThemeStore {

    private const val PREFS = "settings"
    private const val KEY = "app_theme"

    /** Canlı: fuşya · mor · turuncu (varsayılan). */
    const val VIVID = 0

    /** Turkuaz: turkuaz · deniz · zümrüt. */
    const val AQUA = 1

    /** Profesyonel: lacivert · arduvaz · çelik. */
    const val PRO = 2

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Seçili tema kimliği (VIVID / AQUA / PRO). */
    fun current(context: Context): Int =
        prefs(context).getInt(KEY, VIVID).coerceIn(VIVID, PRO)

    fun set(context: Context, theme: Int) {
        prefs(context).edit().putInt(KEY, theme.coerceIn(VIVID, PRO)).apply()
    }

    /** Seçili temanın stil kaynağı; Activity'de setTheme ile uygulanır. */
    fun themeRes(context: Context): Int = when (current(context)) {
        AQUA -> R.style.Theme_Kartvizit_Aqua
        PRO -> R.style.Theme_Kartvizit_Pro
        else -> R.style.Theme_Kartvizit
    }

    /** Tema seçim listesinde gösterilecek adlar (sırası kimliklerle aynı). */
    fun labels(context: Context): Array<String> = arrayOf(
        context.getString(R.string.theme_vivid),
        context.getString(R.string.theme_aqua),
        context.getString(R.string.theme_pro)
    )
}

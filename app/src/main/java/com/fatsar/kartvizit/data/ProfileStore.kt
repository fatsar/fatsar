package com.fatsar.kartvizit.data

import android.content.Context
import com.fatsar.kartvizit.R
import org.json.JSONArray

/**
 * Kullanıcının profillerini (üstteki sekme butonları) sıralı biçimde saklar.
 * Varsayılan olarak "İş" ve "Özel" gelir; kullanıcı "+" ile Excel sayfası gibi
 * yenilerini ekleyebilir, yeniden adlandırabilir ya da silebilir. Tümüyle
 * cihazda tutulur; internet gerekmez.
 */
object ProfileStore {

    private const val PREFS = "settings"
    private const val KEY = "profiles"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun defaults(context: Context): MutableList<String> = mutableListOf(
        context.getString(R.string.profile_work),
        context.getString(R.string.profile_personal)
    )

    /** Sıralı profil listesi; ilk kez çağrılırsa varsayılanlar kurulup döner. */
    fun profiles(context: Context): MutableList<String> {
        val raw = prefs(context).getString(KEY, null)
            ?: return defaults(context).also { save(context, it) }
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
                .filter { it.isNotBlank() }
                .toMutableList()
        }.getOrElse { defaults(context) }.ifEmpty { defaults(context) }
    }

    /** Yeni profil ekler. Boşsa ya da (harf duyarsız) zaten varsa false döner. */
    fun addProfile(context: Context, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val list = profiles(context)
        if (list.any { it.equals(trimmed, ignoreCase = true) }) return false
        list.add(trimmed)
        save(context, list)
        return true
    }

    /** Var olan profili yeniden adlandırır; çakışma/boşlukta false. */
    fun rename(context: Context, old: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return false
        val list = profiles(context)
        val index = list.indexOfFirst { it.equals(old, ignoreCase = true) }
        if (index < 0) return false
        if (list.any { it.equals(trimmed, ignoreCase = true) && !it.equals(old, ignoreCase = true) }) {
            return false
        }
        list[index] = trimmed
        save(context, list)
        return true
    }

    /** Profili siler. Tek profil kaldıysa (en az biri kalmalı) false döner. */
    fun remove(context: Context, name: String): Boolean {
        val list = profiles(context)
        if (list.size <= 1) return false
        val removed = list.removeAll { it.equals(name, ignoreCase = true) }
        if (removed) save(context, list)
        return removed
    }

    private fun save(context: Context, list: List<String>) {
        prefs(context).edit().putString(KEY, JSONArray(list).toString()).apply()
    }
}

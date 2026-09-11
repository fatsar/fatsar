package com.fatsar.hermes.core.logic

import kotlin.random.Random

object Ids {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** "bot_m3k1x9a2" gibi kısa, çakışma olasılığı düşük kimlikler. */
    fun newId(prefix: String, now: Long = System.currentTimeMillis(), random: Random = Random.Default): String {
        val time = now.toString(36)
        val suffix = (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "${prefix}_$time$suffix"
    }

    /** Token/anahtar gösterirken maskeleme: "sk-1234...cdef". */
    fun mask(secret: String): String {
        val s = secret.trim()
        if (s.isEmpty()) return ""
        if (s.length <= 8) return "•".repeat(s.length)
        return s.take(4) + "…" + s.takeLast(4)
    }
}

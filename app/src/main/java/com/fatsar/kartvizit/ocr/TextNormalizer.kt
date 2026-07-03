package com.fatsar.kartvizit.ocr

import java.util.Locale

/**
 * OCR çıktısındaki metinleri düzeltir: fazla boşlukları temizler ve
 * TAMAMEN BÜYÜK ya da tamamen küçük yazılmış metinleri Türkçe kurallarına
 * göre düzgün büyük/küçük harfe çevirir (İ/ı dahil).
 */
object TextNormalizer {

    private val TR = Locale("tr", "TR")
    private val WS = Regex("""\s+""")

    /** Hep büyük kalması gereken kısaltmalar (noktaları atılmış halleriyle). */
    private val KEEP_UPPER = setOf(
        "AŞ", "LTD", "ŞTİ", "STİ", "LLC", "GMBH", "INC", "PLC", "CO",
        "TC", "OSB", "AVM", "CEO", "CTO", "CFO", "COO", "GM", "ARGE"
    )

    /** Küçük yazılan bağlaçlar. */
    private val KEEP_LOWER = setOf("ve", "ile", "and", "of", "for", "the")

    /** Fazla boşlukları tek boşluğa indirir, baş/son boşlukları atar. */
    fun tidy(s: String): String = s.replace(WS, " ").trim()

    /**
     * "AHMET YILMAZ" -> "Ahmet Yılmaz", "yıldız tekstil" -> "Yıldız Tekstil".
     * Karışık yazılmış (ör. "McDonald") kelimelere dokunmaz; rakam veya @
     * içeren parçaları olduğu gibi bırakır.
     */
    fun smartTitleCase(s: String): String {
        val text = tidy(s)
        if (text.isEmpty()) return text
        return text.split(' ').joinToString(" ") { fixToken(it) }
    }

    private fun fixToken(token: String): String {
        if (token.any { it.isDigit() } || token.contains('@')) return token
        val letters = token.filter { it.isLetter() }
        if (letters.isEmpty()) return token

        val allUpper = letters.all { it.isUpperCase() }
        val allLower = letters.all { it.isLowerCase() }
        if (!allUpper && !allLower) return token // karışık yazımı koru

        val bare = letters.uppercase(TR)
        if (bare in KEEP_UPPER) return token.uppercase(TR)
        if (bare.lowercase(TR) in KEEP_LOWER) return token.lowercase(TR)

        // Baş harf büyük; nokta, tire ve kesme sonrası da yeni kelime sayılır
        // ("SAN." -> "San.", "ALİ-VELİ" -> "Ali-Veli").
        val sb = StringBuilder(token.length)
        var newWord = true
        for (c in token) {
            if (c.isLetter()) {
                sb.append(
                    if (newWord) c.toString().uppercase(TR)
                    else c.toString().lowercase(TR)
                )
                newWord = false
            } else {
                sb.append(c)
                newWord = c in ".-'’/"
            }
        }
        return sb.toString()
    }

    /** Türkçe karakterleri sadeleştirir; e-posta ile isim eşleştirmede kullanılır. */
    fun foldTr(s: String): String = buildString(s.length) {
        for (c in s.lowercase(TR)) {
            append(
                when (c) {
                    'ı' -> 'i'; 'ş' -> 's'; 'ğ' -> 'g'; 'ç' -> 'c'
                    'ö' -> 'o'; 'ü' -> 'u'; 'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                    else -> c
                }
            )
        }
    }

    /**
     * Tam adı (ad, soyad) olarak ayırır: son kelime soyad, kalanı ad.
     * "Ahmet Can Yılmaz" -> ("Ahmet Can", "Yılmaz").
     */
    fun splitName(fullName: String): Pair<String, String> {
        val tokens = tidy(fullName).split(' ').filter { it.isNotBlank() }
        return when {
            tokens.isEmpty() -> "" to ""
            tokens.size == 1 -> tokens[0] to ""
            else -> tokens.dropLast(1).joinToString(" ") to tokens.last()
        }
    }

    /** Numara Türkiye cep telefonu kalıbına uyuyor mu? */
    fun isTurkishMobile(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.startsWith("905") || digits.startsWith("05") ||
            (digits.length == 10 && digits.startsWith("5"))
    }
}

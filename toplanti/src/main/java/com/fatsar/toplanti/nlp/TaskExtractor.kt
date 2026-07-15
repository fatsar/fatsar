package com.fatsar.toplanti.nlp

import com.fatsar.toplanti.model.TaskItem
import com.fatsar.toplanti.model.TaskStatus
import java.util.Calendar

/**
 * Cümlelerden görev adayı, sahip ve termin çıkarır (FR-030..FR-032).
 * Kaynakta olmayan bilgi üretilmez; sahip/termin bulunamazsa alan boş bırakılır
 * ve görev "Onay gerekli" (NEEDS_REVIEW) olarak işaretlenir.
 */
object TaskExtractor {

    data class Sentence(val text: String, val segmentIds: List<String>)

    // ASR çıktısı küçük harfli olduğundan sahip tespiti yaygın Türkçe adlarla desteklenir
    private val COMMON_NAMES = setOf(
        "ahmet", "ali", "aliye", "arda", "aslı", "aylin", "ayşe", "aziz", "banu", "barış", "berk",
        "berna", "beyza", "burak", "burcu", "büşra", "can", "canan", "cem", "cemal", "cemre",
        "ceren", "damla", "deniz", "derya", "dilek", "duygu", "ebru", "ece", "eda", "elif", "emel",
        "emine", "emre", "ender", "engin", "enes", "erdem", "eren", "erkan", "esra", "fatih",
        "fatma", "ferhat", "fikret", "funda", "gamze", "gizem", "gökhan", "gül", "gülay", "hakan",
        "halil", "hande", "hasan", "hatice", "hilal", "hüseyin", "ibrahim", "ilayda", "ilker",
        "irem", "kaan", "kadir", "kemal", "kerem", "koray", "kübra", "levent", "leyla", "mehmet",
        "melek", "melis", "merve", "mert", "meryem", "metin", "murat", "mustafa", "nazlı", "nur",
        "okan", "onur", "orhan", "osman", "oğuz", "ozan", "ömer", "özge", "özlem", "pelin",
        "pınar", "ramazan", "recep", "salih", "seda", "selin", "selim", "sema", "semih", "serap",
        "serkan", "sevgi", "sibel", "sinan", "suat", "şeyma", "tolga", "tuğba", "tuna", "ufuk",
        "umut", "veli", "yasemin", "yasin", "yavuz", "yusuf", "zehra", "zeynep"
    )

    private val WEEKDAYS = mapOf(
        "pazartesi" to Calendar.MONDAY, "salı" to Calendar.TUESDAY, "çarşamba" to Calendar.WEDNESDAY,
        "perşembe" to Calendar.THURSDAY, "cuma" to Calendar.FRIDAY, "cumartesi" to Calendar.SATURDAY,
        "pazar" to Calendar.SUNDAY
    )

    private val MONTHS = listOf(
        "ocak", "şubat", "mart", "nisan", "mayıs", "haziran",
        "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık"
    )

    // Görev bildiren eylem kalıpları
    private val ACTION_SUFFIXES = listOf(
        "acak", "ecek", "acağım", "eceğim", "acağız", "eceğiz", "acaksın", "eceksin",
        "acaklar", "ecekler", "malı", "meli", "malıyız", "meliyiz", "malısın", "melisin",
        "alım", "elim", "sın", "sin"
    )
    private val ACTION_WORDS = setOf("gerekiyor", "gerek", "lazım", "gerekli", "halleder", "hallet", "takip")

    fun extract(sentences: List<Sentence>, baseDateMillis: Long): List<TaskItem> {
        val tasks = mutableListOf<TaskItem>()
        for (s in sentences) {
            val tokens = TurkishText.tokenize(s.text)
            if (tokens.size < 3) continue
            if (!hasActionVerb(tokens)) continue

            val owner = findOwner(tokens)
            val (dueText, dueAt) = findDue(s.text, tokens, baseDateMillis)

            var confidence = 0.55
            if (owner.isNotBlank()) confidence += 0.2
            if (dueText.isNotBlank()) confidence += 0.15

            val cleaned = TranscriptCleaner.clean(s.text)
            tasks.add(
                TaskItem(
                    title = cleaned.take(120).ifBlank { s.text.take(120) },
                    description = s.text,
                    ownerText = owner,
                    dueTextOriginal = dueText,
                    dueAtMillis = dueAt,
                    status = TaskStatus.NEEDS_REVIEW,
                    confidence = confidence,
                    sourceSegmentIds = s.segmentIds
                )
            )
        }
        return tasks
    }

    private fun hasActionVerb(tokens: List<String>): Boolean {
        for ((i, raw) in tokens.withIndex()) {
            val t = TurkishText.lowercaseTr(raw)
            if (t in ACTION_WORDS) return true
            // "gelecek hafta/ay/yıl" bir zaman ifadesidir, eylem değil
            if (t == "gelecek" && i + 1 < tokens.size &&
                TurkishText.lowercaseTr(tokens[i + 1]) in setOf("hafta", "ay", "yıl", "sene", "toplantı")
            ) continue
            if (t.length >= 6 && ACTION_SUFFIXES.any { t.endsWith(it) && t.length > it.length + 1 }) {
                // -sın/-sin ekleri isimlerde de olur; yalnızca diğer güçlü eklerle birlikte kabul et
                if (t.endsWith("sın") || t.endsWith("sin")) {
                    if (!t.endsWith("malısın") && !t.endsWith("melisin")) continue
                }
                return true
            }
        }
        return false
    }

    private fun findOwner(tokens: List<String>): String {
        for (raw in tokens) {
            val t = TurkishText.lowercaseTr(raw)
            if (t in COMMON_NAMES) return TurkishText.capitalizeTr(t)
            // Kullanıcı düzeltmesi yapılmış metinlerde büyük harfle yazılmış adları da yakala
            if (raw.length >= 3 && raw[0].isUpperCase() && raw.drop(1).all { it.isLowerCase() } &&
                TurkishText.lowercaseTr(raw) !in TurkishText.STOPWORDS && t !in WEEKDAYS.keys && t !in MONTHS
            ) {
                return raw
            }
        }
        val lower = tokens.map { TurkishText.lowercaseTr(it) }
        if ("ben" in lower) return "Ben"
        if ("biz" in lower) return "Biz"
        return ""
    }

    /** @return (orijinal termin ifadesi, hesaplanan zaman; bulunamazsa 0) */
    private fun findDue(text: String, tokens: List<String>, baseMillis: Long): Pair<String, Long> {
        val lower = tokens.map { TurkishText.lowercaseTr(it) }
        val base = Calendar.getInstance().apply {
            timeInMillis = baseMillis
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for ((i, t) in lower.withIndex()) {
            WEEKDAYS[stripSuffix(t)]?.let { dow ->
                val c = base.clone() as Calendar
                do c.add(Calendar.DAY_OF_MONTH, 1) while (c.get(Calendar.DAY_OF_WEEK) != dow)
                return phraseAround(tokens, i) to c.timeInMillis
            }
            when (stripSuffix(t)) {
                "yarın", "yarına" -> {
                    val c = base.clone() as Calendar
                    c.add(Calendar.DAY_OF_MONTH, 1)
                    return tokens[i] to c.timeInMillis
                }
                "bugün" -> return tokens[i] to base.timeInMillis
                "haftaya" -> {
                    val c = base.clone() as Calendar
                    c.add(Calendar.DAY_OF_MONTH, 7)
                    return tokens[i] to c.timeInMillis
                }
            }
            if ((t == "gelecek" || t == "önümüzdeki") && i + 1 < lower.size) {
                val unit = stripSuffix(lower[i + 1])
                val c = base.clone() as Calendar
                when (unit) {
                    "hafta" -> c.add(Calendar.DAY_OF_MONTH, 7)
                    "ay" -> c.add(Calendar.MONTH, 1)
                    else -> continue
                }
                return "${tokens[i]} ${tokens[i + 1]}" to c.timeInMillis
            }
            if (t == "ay" && i + 1 < lower.size && stripSuffix(lower[i + 1]).startsWith("sonu")) {
                val c = base.clone() as Calendar
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
                return "${tokens[i]} ${tokens[i + 1]}" to c.timeInMillis
            }
            // "15 temmuz" gibi gün + ay adı
            if (t.all { it.isDigit() } && i + 1 < lower.size) {
                val monthIdx = MONTHS.indexOf(stripSuffix(lower[i + 1]))
                if (monthIdx >= 0) {
                    val day = t.toIntOrNull() ?: continue
                    if (day in 1..31) {
                        val c = base.clone() as Calendar
                        c.set(Calendar.MONTH, monthIdx)
                        c.set(Calendar.DAY_OF_MONTH, day)
                        if (c.timeInMillis < base.timeInMillis) c.add(Calendar.YEAR, 1)
                        return "${tokens[i]} ${tokens[i + 1]}" to c.timeInMillis
                    }
                }
            }
        }

        // 15.08 / 15/08/2026 biçimleri
        Regex("\\b(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?\\b").find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            if (day in 1..31 && month in 1..12) {
                val c = base.clone() as Calendar
                c.set(Calendar.MONTH, month - 1)
                c.set(Calendar.DAY_OF_MONTH, day)
                m.groupValues[3].toIntOrNull()?.let { y -> c.set(Calendar.YEAR, if (y < 100) 2000 + y else y) }
                if (c.timeInMillis < base.timeInMillis) c.add(Calendar.YEAR, 1)
                return m.value to c.timeInMillis
            }
        }
        return "" to 0L
    }

    /** "cumaya", "cumartesiye kadar" gibi ekli halleri kök güne indirger. */
    private fun stripSuffix(token: String): String {
        var t = token
        for (suf in listOf("sına", "sine", "sini", "sını", "ya", "ye", "na", "ne", "yı", "yi", "yu", "yü", "a", "e")) {
            val cand = t.removeSuffix(suf)
            if (cand != t && (cand in WEEKDAYS.keys || cand in MONTHS ||
                    cand in setOf("yarın", "bugün", "hafta", "ay", "sonu", "haftaya"))
            ) return cand
        }
        return t
    }

    private fun phraseAround(tokens: List<String>, i: Int): String {
        val next = tokens.getOrNull(i + 1)?.let { TurkishText.lowercaseTr(it) }
        return if (next == "gününe" || next == "günü" || next == "kadar") {
            val third = tokens.getOrNull(i + 2)?.let { TurkishText.lowercaseTr(it) }
            if (next == "gününe" && third == "kadar") "${tokens[i]} ${tokens[i + 1]} ${tokens[i + 2]}"
            else "${tokens[i]} ${tokens[i + 1]}"
        } else tokens[i]
    }
}

package com.fatsar.kartvizit.ocr

import java.util.Locale

/** Kartvizitten okunan alanlar. */
data class ParsedCard(
    val name: String = "",
    val title: String = "",
    val company: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val website: String = "",
    val address: String = "",
    val rawText: String = ""
)

/**
 * OCR ile okunan ham kartvizit metnini alanlara (isim, unvan, firma, telefon,
 * e-posta, web, adres) ayıran sezgisel çözümleyici. Tamamen cihaz üzerinde
 * çalışır, internet gerektirmez. Türkçe ve İngilizce kartvizitlere göre
 * ayarlanmıştır.
 */
object CardTextParser {

    private val TR = Locale("tr", "TR")

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val PHONE = Regex("""[+(]?\d[\d\s().\-/]{7,}\d""")
    private val URL = Regex(
        """(?i)(?:https?://|www\.)[^\s,;|]+""" +
            """|[a-z0-9\-]+(?:\.[a-z0-9\-]+)*\.(?:com\.tr|net\.tr|org\.tr|gen\.tr|web\.tr|edu\.tr|gov\.tr|com|net|org|info|biz|io|co)(?:/[^\s,;|]*)?"""
    )
    private val POSTAL_LINE = Regex("""^\d{5}\b.*""")
    private val CONTACT_LABELS = Regex(
        """(?i)\b(tel|telefon|phone|gsm|cep|mobile|mob|fax|faks|office|ofis|e-?posta|e-?mail|mail|web|www|adres|address)\b\s*[:.]?"""
    )

    private val TITLE_KEYWORDS = listOf(
        "müdür", "koordinatör", "uzman", "mühendis", "direktör", "danışman",
        "yönetici", "temsilci", "sorumlu", "şef", "başkan", "kurucu", "ortak",
        "avukat", "mimar", "tekniker", "teknisyen", "operatör", "satış",
        "pazarlama", "muhasebe", "finans", "insan kaynakları",
        "ceo", "cto", "cfo", "coo", "founder", "partner", "manager",
        "director", "engineer", "consultant", "specialist", "executive",
        "president", "sales", "marketing", "developer", "designer",
        "architect", "analyst", "coordinator", "supervisor", "representative"
    )

    private val COMPANY_KEYWORDS = listOf(
        "a.ş", "a.s.", "ltd", "şti", "sti.", "san.", "tic.", "sanayi",
        "ticaret", "holding", "grup", "group", "şirketi", "inc", "llc",
        "gmbh", "corp", "company", "danışmanlık", "mühendislik", "inşaat",
        "teknoloji", "yazılım", "bilişim", "otomotiv", "tekstil", "gıda",
        "turizm", "sigorta", "lojistik", "medikal", "kozmetik", "mobilya",
        "enerji", "elektrik", "elektronik", "makina", "makine", "metal",
        "plastik", "ambalaj", "matbaa", "reklam", "ajans", "hukuk bürosu",
        "eczane", "klinik", "hastane", "emlak", "gayrimenkul"
    )

    private val ADDRESS_KEYWORDS = listOf(
        "mah.", "mahalle", "cad.", "cadde", "sok.", "sokak", "sokağı",
        "bulvar", "blv", "no:", "no :", "kat:", "kat :", "daire", "blok",
        "plaza", "apt", "apartman", "sitesi", "iş merkezi", "işhanı", "osb",
        "sanayi sitesi", "residence", "kampüs", "mevkii", "pk:", "p.k."
    )

    private val FREE_EMAIL_DOMAINS = setOf(
        "gmail", "hotmail", "outlook", "yahoo", "yandex", "icloud", "mynet",
        "live", "msn", "protonmail", "mail", "windowslive"
    )

    /** Test ve basit kullanım için: düz metni satırlara bölerek çözümler. */
    fun parse(text: String): ParsedCard =
        parse(text.lines().map { OcrLine(it) })

    fun parse(lines: List<OcrLine>): ParsedCard {
        val cleaned = lines
            .map { OcrLine(it.text.trim(), it.height) }
            .filter { it.text.isNotBlank() }
        if (cleaned.isEmpty()) return ParsedCard()

        val rawText = cleaned.joinToString("\n") { it.text }

        val emails = LinkedHashSet<String>()
        val phonesByDigits = LinkedHashMap<String, String>() // rakamlar -> orijinal
        var website = ""
        val addressLines = mutableListOf<String>()
        val remaining = mutableListOf<OcrLine>()
        var prevWasAddress = false

        for (line in cleaned) {
            val lower = line.text.lowercase(TR)

            // 1) Adres satırları (anahtar kelime ya da önceki adresi izleyen posta kodu)
            if (ADDRESS_KEYWORDS.any { lower.contains(it) } ||
                (prevWasAddress && POSTAL_LINE.matches(line.text))
            ) {
                addressLines.add(line.text.trim().trimEnd(','))
                prevWasAddress = true
                continue
            }

            // 2) E-posta / web / telefon çıkarımı
            var work = line.text
            var extracted = false

            EMAIL.findAll(work).forEach {
                emails.add(it.value.lowercase(Locale.ROOT))
                extracted = true
            }
            if (extracted) work = work.replace(EMAIL, " ")

            if (!work.contains('@')) {
                val urlMatch = URL.find(work)
                if (urlMatch != null) {
                    if (website.isEmpty()) website = cleanUrl(urlMatch.value)
                    work = work.removeRange(urlMatch.range)
                    extracted = true
                }
            }

            for (m in PHONE.findAll(work)) {
                val digits = m.value.filter { it.isDigit() }
                if (digits.length >= 9) {
                    phonesByDigits.putIfAbsent(digits, normalizePhone(m.value))
                    extracted = true
                }
            }
            if (extracted) work = work.replace(PHONE, " ")

            // 3) Etiketler ("Tel:", "GSM:" vb.) atıldıktan sonra kalan metin
            val residual = work
                .replace(CONTACT_LABELS, " ")
                .replace(Regex("""[|•·]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', '-', ':', ',', ';', '/')

            if (extracted && residual.count { it.isLetter() } < 4) continue

            remaining.add(OcrLine(if (extracted) residual else line.text, line.height))
            prevWasAddress = false
        }

        // 4) Firma ve unvan satırları
        var company = ""
        val companyIndex = remaining.indexOfFirst { l ->
            val lower = l.text.lowercase(TR)
            COMPANY_KEYWORDS.any { lower.contains(it) }
        }
        if (companyIndex >= 0) company = remaining.removeAt(companyIndex).text

        var title = ""
        val titleIndex = remaining.indexOfFirst { l ->
            val lower = l.text.lowercase(TR)
            TITLE_KEYWORDS.any { lower.contains(it) }
        }
        if (titleIndex >= 0) title = remaining.removeAt(titleIndex).text

        // 5) İsim: kalan satırlar arasından en "isim gibi" olanı seç.
        //    Kişi adları genellikle 2-3 kelimedir; e-posta adresiyle örtüşen
        //    satır güçlü bir işarettir (firma adının isim sanılmasını önler).
        var name = ""
        val candidates = remaining.withIndex().filter { (_, l) -> looksLikeName(l.text) }
        if (candidates.isNotEmpty()) {
            val maxHeight = candidates.maxOf { it.value.height }.coerceAtLeast(1f)
            val emailTokens = emails.firstOrNull()
                ?.substringBefore('@')
                ?.split('.', '_', '-')
                ?.map { TextNormalizer.foldTr(it) }
                ?.filter { it.length >= 2 }
                .orEmpty()

            fun score(line: OcrLine, index: Int): Double {
                val tokens = line.text.split(Regex("""\s+""")).filter { it.isNotBlank() }
                var s = 0.0
                if (tokens.size in 2..3) s += 2.0
                s += tokens.count { TextNormalizer.foldTr(it.trim('.', ',')) in emailTokens } * 3.0
                s += (line.height / maxHeight) * 1.5
                s -= index * 0.01 // eşitlikte üstteki satır kazanır
                return s
            }

            val best = candidates.maxByOrNull { (i, l) -> score(l, i) }!!
            name = best.value.text
            remaining.removeAt(best.index)
        }

        // 6) Yedek çıkarımlar
        if (name.isBlank() && emails.isNotEmpty()) name = nameFromEmail(emails.first())
        if (company.isBlank()) {
            val fallback = remaining
                .filter { it.text.count { c -> c.isLetter() } >= 3 }
                .maxByOrNull { it.height }
            if (fallback != null) {
                company = fallback.text
            } else if (emails.isNotEmpty()) {
                company = companyFromEmail(emails.first())
            }
        }

        return ParsedCard(
            name = TextNormalizer.smartTitleCase(name),
            title = TextNormalizer.smartTitleCase(title),
            company = TextNormalizer.smartTitleCase(company),
            phones = phonesByDigits.values.toList(),
            emails = emails.toList(),
            website = website,
            address = addressLines.joinToString(", ") { TextNormalizer.smartTitleCase(it) },
            rawText = rawText
        )
    }

    private fun looksLikeName(text: String): Boolean {
        if (text.length < 3 || text.any { it.isDigit() }) return false
        val tokens = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > 5) return false
        if (tokens.any { t -> t.none { it.isLetter() } }) return false
        val letters = text.count { it.isLetter() }
        return letters.toFloat() / text.replace(" ", "").length >= 0.7f
    }

    private fun normalizePhone(raw: String): String =
        raw.replace(Regex("""\s+"""), " ").trim()

    private fun cleanUrl(raw: String): String =
        raw.trim().trimEnd('.', ',', ';', ')', '|')

    private fun nameFromEmail(email: String): String {
        val local = email.substringBefore('@')
        val parts = local.split('.', '_', '-').filter { it.isNotBlank() && it.none(Char::isDigit) }
        if (parts.isEmpty()) return ""
        return parts.joinToString(" ") { p ->
            p.replaceFirstChar { if (it.isLowerCase()) it.titlecase(TR) else it.toString() }
        }
    }

    private fun companyFromEmail(email: String): String {
        val domain = email.substringAfter('@').substringBefore('.')
        if (domain.lowercase(Locale.ROOT) in FREE_EMAIL_DOMAINS) return ""
        return domain.replaceFirstChar { if (it.isLowerCase()) it.titlecase(TR) else it.toString() }
    }
}

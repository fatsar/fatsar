package com.fatsar.kartvizit.ocr

import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.model.TypedPhone
import java.util.Locale

/** Kartvizitten okunan alanlar. */
data class ParsedCard(
    val name: String = "",
    val title: String = "",
    val company: String = "",
    val phones: List<TypedPhone> = emptyList(),
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

    // İsim adayı OLAMAYACAK alan adı / e-posta / web kalıpları. Boşluklar
    // atıldıktan sonra bakılır; böylece OCR'ın "univarsolutions. com" gibi
    // araya boşluk koyduğu web adresleri de isim sanılmaz.
    private val DOMAINISH = Regex(
        """@|\bwww\.|\.(?:com|net|org|info|biz|io|co|gov|edu)(?:\.tr)?\b"""
    )

    /** İsim, kartın en büyük yazısıdır: bu orandan büyük bitişik satırlar birleşir. */
    private const val NAME_BIG_RATIO = 0.72f
    private val CONTACT_LABELS = Regex(
        """(?i)\b(tel|telefon|phone|gsm|cep|mobile|mob|fax|faks|office|ofis|e-?posta|e-?mail|mail|web|www|adres|address)\b\s*[:.]?"""
    )

    // Telefon türünü belirleyen etiketler (satırdaki ilk eşleşme kazanır)
    private val FAX_LABEL = Regex("""(?i)\b(faks?|fax|f)\s*[:.]""")
    private val MOBILE_LABEL = Regex("""(?i)\b(gsm|cep|mobil|mobile|mob|cell|m)\s*[:.]|\bgsm\b""")
    private val HOME_LABEL = Regex("""(?i)\b(ev|home|h)\s*[:.]""")
    private val WORK_LABEL = Regex("""(?i)\b(tel|telefon|phone|ofis|office|iş|is|t)\s*[:.]""")

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


    /**
     * Anahtar kelimeyi KELİME SINIRIYLA arar. Düz `contains` kullanmak
     * "geliŞTİrme" içindeki "şti"yi firma eki sanmak gibi hatalara yol açar.
     * Java'nın \b sınırı Türkçe harfleri kelime dışı saydığı için sınır
     * kontrolü Unicode harf lookaround'larıyla yapılır.
     */
    private val keywordCache = HashMap<String, Regex>()

    private fun containsKeyword(lower: String, keyword: String): Boolean {
        val re = keywordCache.getOrPut(keyword) {
            Regex("""(?<!\p{L})""" + Regex.escape(keyword) + """(?!\p{L})""")
        }
        return re.containsMatchIn(lower)
    }

    /** Test ve basit kullanım için: düz metni satırlara bölerek çözümler. */
    fun parse(text: String): ParsedCard =
        parse(text.lines().map { OcrLine(it) })

    fun parse(lines: List<OcrLine>): ParsedCard {
        val cleaned = lines
            .map { OcrLine(it.text.trim(), it.height) }
            .filter { it.text.isNotBlank() }
        if (cleaned.isEmpty()) return ParsedCard()

        val rawText = cleaned.joinToString("\n") { it.text }
        // Büyük/küçük harf kuralı KARTIN TAMAMINDAN belirlenir: tek satıra
        // bakmak yanıltıcıdır ("OCI UNID" tek başına dilsizdir, Türkçe kuralla
        // "Ocı Unıd" olurdu). Kartta İngilizce sözcükler varsa İngilizce.
        val cardLocale = TextNormalizer.localeFor(rawText)

        val emails = LinkedHashSet<String>()
        val phonesByDigits = LinkedHashMap<String, TypedPhone>() // rakamlar -> tür+numara
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

            val lineLabelType = detectPhoneType(line.text)
            for (m in PHONE.findAll(work)) {
                val digits = m.value.filter { it.isDigit() }
                if (digits.length >= 9) {
                    val type = lineLabelType ?: defaultPhoneType(digits)
                    val existing = phonesByDigits[digits]
                    // Aynı numara için açık etiket, tahmine tercih edilir
                    if (existing == null) {
                        phonesByDigits[digits] = TypedPhone(normalizePhone(m.value), type)
                    } else if (existing.type == PhoneType.OTHER && type != PhoneType.OTHER) {
                        phonesByDigits[digits] = existing.copy(type = type)
                    }
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
            COMPANY_KEYWORDS.any { containsKeyword(lower, it) }
        }
        if (companyIndex >= 0) company = remaining.removeAt(companyIndex).text

        var title = ""
        val titleIndex = remaining.indexOfFirst { l ->
            val lower = l.text.lowercase(TR)
            TITLE_KEYWORDS.any { containsKeyword(lower, it) }
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

            // Marka sözcükleri: e-posta alan adı, web sitesi ve firma adı.
            // Kartın EN BÜYÜK yazısı çoğu zaman LOGO'dur (SODİTAŞ, ÇAKIRLAR,
            // CESTEL); bunlar kişi adı değildir ve elenmelidir.
            val brands = brandWords(company, website, emails)

            fun score(line: OcrLine, index: Int): Double {
                val tokens = line.text.split(Regex("""\s+""")).filter { it.isNotBlank() }
                var s = 0.0
                if (tokens.size in 2..3) s += 2.0
                // E-posta kullanıcı adıyla örtüşme en güçlü kanıttır
                s += tokens.count { TextNormalizer.foldTr(it.trim('.', ',')) in emailTokens } * 5.0
                // Punto yalnızca DESTEKLEYİCİ ipucudur; tek başına logoyu
                // kazandırmaması için ağırlığı düşük ve doğrusal tutulur.
                s += (line.height / maxHeight) * 1.5
                // Logo/firma ile eşleşen satır kişi adı olamaz
                if (isBrandish(line.text, brands)) s -= 6.0
                // "Full L" gibi tek harflik parçalar (arka plan gürültüsü)
                if (tokens.any { t -> t.trim('.', ',').length == 1 }) s -= 1.5
                // Kısa ve tamamen BÜYÜK yazılmış satırlar logo/kısaltma olma
                // eğilimindedir ("OCI UNID", "SODİTAŞ"). Kişi adları genelde
                // daha uzundur ya da düzgün büyük/küçük yazılır.
                val letters = line.text.filter { it.isLetter() }
                if (letters.length in 1..8 && letters.all { it.isUpperCase() }) s -= 2.0
                s -= index * 0.01 // eşitlikte üstteki satır kazanır
                return s
            }

            val best = candidates.maxByOrNull { (i, l) -> score(l, i) }!!
            // En iyi aday kartın en büyük yazısıysa, ona bitişik ve benzer
            // büyüklükteki isim satırlarını da kata: "ANIL" + "NİZAM" gibi iki
            // satıra bölünmüş adlar tek isimde toplanır. Markayla eşleşen
            // komşu satır ("Erdi Coşkun" + "CESTEL") KATILMAZ.
            val bestIsBig = best.value.height >= NAME_BIG_RATIO * maxHeight
            val indices = if (bestIsBig) {
                nameRunIndices(remaining, best.index, maxHeight)
                    .filter { it == best.index || !isBrandish(remaining[it].text, brands) }
            } else listOf(best.index)

            name = indices.joinToString(" ") { remaining[it].text.trim() }
                .replace(Regex("""\s+"""), " ")
                .trim()
            indices.sortedDescending().forEach { remaining.removeAt(it) }

            // Kartın KENDİ e-postası kişi adı gibi bölünüyorsa ("baris.gunes")
            // ve seçilen satır bununla hiç örtüşmüyorsa, o satır büyük
            // olasılıkla komşu karttan sızmış ya da adres parçasıdır. Bu
            // durumda kartın kendi verisi olan e-postadaki ad yeğlenir.
            if (emailTokens.size >= 2) {
                val picked = name.split(Regex("""\s+"""))
                    .map { TextNormalizer.foldTr(it.trim('.', ',')) }
                if (picked.none { it in emailTokens }) {
                    name = nameFromEmail(emails.first())
                }
            }
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
            name = TextNormalizer.smartTitleCase(name, cardLocale),
            title = TextNormalizer.smartTitleCase(title, cardLocale),
            company = TextNormalizer.smartTitleCase(company, cardLocale),
            phones = phonesByDigits.values.toList().sortedBy { it.type.ordinal },
            emails = emails.toList(),
            website = website,
            address = addressLines.joinToString(", ") { TextNormalizer.smartTitleCase(it, cardLocale) },
            rawText = rawText
        )
    }

    /**
     * Kartın "marka" sözcükleri: e-posta alan adı, web adresi ve firma
     * adındaki anlamlı kelimeler. Logo satırını kişi adından ayırmak için
     * kullanılır (SODİTAŞ ↔ soditas.com.tr, CESTEL ↔ cestelkimya.com).
     */
    private fun brandWords(company: String, website: String, emails: Collection<String>): Set<String> {
        val out = mutableSetOf<String>()
        // Yalnızca ASIL alan adı (uzantıdan önceki son parça) marka sayılır.
        // "anil.nizamdunivarsolutions.com" gibi adreslerde baştaki parça kişi
        // adı olabilir; onu marka sayarsak kişinin adını eleriz.
        fun addHost(host: String) {
            val parts = host.substringBefore('/').split('.').filter { it.isNotBlank() }
            var i = parts.size - 1
            while (i >= 0 && (parts[i].length <= 3 || parts[i].lowercase(Locale.ROOT) in DOMAIN_SUFFIXES)) i--
            if (i >= 0 && parts[i].length >= 4) out.add(TextNormalizer.foldTr(parts[i]))
        }
        emails.firstOrNull()?.substringAfter('@')?.let(::addHost)
        if (website.isNotBlank()) addHost(website.removePrefix("www.").lowercase(Locale.ROOT))
        company.split(Regex("""[^\p{L}]+""")).forEach { word ->
            if (word.length >= 4) out.add(TextNormalizer.foldTr(word))
        }
        return out
    }

    /** Satır, kartın markasıyla (logo/firma/alan adı) örtüşüyor mu? */
    private fun isBrandish(text: String, brands: Set<String>): Boolean {
        if (brands.isEmpty()) return false
        val folded = TextNormalizer.foldTr(text).filter { it.isLetter() }
        if (folded.length < 4) return false
        return brands.any { brand ->
            // Satır markayı içeriyor ("SODİTAŞ SOLVENT..." ⊃ "soditas"), ya da
            // marka satırı içeriyor AMA satır markanın en az yarısı kadar
            // uzun. Oran şartı şart: "cestelkimya" ⊃ "cestel" (%55) marka
            // sayılır; bozuk okunan "nizamdunivarsolutions" ⊃ "nizam" (%24)
            // sayılmaz, yoksa kişinin soyadını elerdik.
            folded.contains(brand) ||
                (brand.contains(folded) && brand.length <= folded.length * 2)
        }
    }

    private val DOMAIN_SUFFIXES = setOf("com", "net", "org", "gov", "edu", "info", "biz")

    private fun looksLikeName(text: String): Boolean {
        if (text.length < 3 || text.any { it.isDigit() }) return false
        // Web adresi / e-posta gibi satırlar (boşluklu OCR dahil) isim değildir
        if (DOMAINISH.containsMatchIn(text.replace(" ", "").lowercase(TR))) return false
        val tokens = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > 5) return false
        if (tokens.any { t -> t.none { it.isLetter() } }) return false
        val letters = text.count { it.isLetter() }
        return letters.toFloat() / text.replace(" ", "").length >= 0.7f
    }

    /**
     * [center] satırının çevresindeki, bitişik ve benzer (büyük) puntolu isim
     * satırlarının indeksleri. Kartta isim iki satıra bölündüğünde ("ANIL" /
     * "NİZAM") bunları tek isimde toplamak için kullanılır.
     */
    private fun nameRunIndices(lines: List<OcrLine>, center: Int, maxHeight: Float): List<Int> {
        fun bigName(i: Int): Boolean =
            i in lines.indices &&
                lines[i].height >= NAME_BIG_RATIO * maxHeight &&
                looksLikeName(lines[i].text)
        val indices = sortedSetOf(center)
        var i = center - 1
        while (bigName(i)) { indices.add(i); i-- }
        var j = center + 1
        while (bigName(j)) { indices.add(j); j++ }
        return indices.toList()
    }

    private fun normalizePhone(raw: String): String =
        raw.replace(Regex("""\s+"""), " ").trim()

    /** Satırdaki etiketten telefon türünü belirler; etiket yoksa null döner. */
    private fun detectPhoneType(line: String): PhoneType? = when {
        FAX_LABEL.containsMatchIn(line) -> PhoneType.FAX
        MOBILE_LABEL.containsMatchIn(line) -> PhoneType.MOBILE
        HOME_LABEL.containsMatchIn(line) -> PhoneType.HOME
        WORK_LABEL.containsMatchIn(line) -> PhoneType.WORK
        else -> null
    }

    /** Etiket yoksa numaranın biçimine göre tahmin yürütür. */
    private fun defaultPhoneType(digits: String): PhoneType =
        if (TextNormalizer.isTurkishMobile(digits)) PhoneType.MOBILE else PhoneType.WORK

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

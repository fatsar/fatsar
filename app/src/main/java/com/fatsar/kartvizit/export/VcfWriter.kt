package com.fatsar.kartvizit.export

import com.fatsar.kartvizit.model.ContactRecord
import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.ocr.TextNormalizer
import java.io.OutputStream

/**
 * Kayıtları standart vCard 3.0 (.vcf) rehber dosyası olarak yazar.
 * Bu dosya telefon rehberleri, Google Kişiler ve Outlook tarafından
 * doğrudan içe aktarılabilir; WhatsApp/e-posta ile paylaşılabilir.
 */
object VcfWriter {

    fun write(records: List<ContactRecord>, out: OutputStream) {
        out.write(build(records).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    internal fun build(records: List<ContactRecord>): String {
        val sb = StringBuilder()
        records.forEach { sb.append(buildCard(it)) }
        return sb.toString()
    }

    private fun buildCard(r: ContactRecord): String {
        val sb = StringBuilder()
        fun line(s: String) = sb.append(s).append("\r\n")

        val displayName = r.name.ifBlank { r.company }
            .ifBlank { r.emails.firstOrNull() ?: r.phones.firstOrNull()?.number.orEmpty() }
        val (given, family) = TextNormalizer.splitName(r.name.ifBlank { r.company })

        line("BEGIN:VCARD")
        line("VERSION:3.0")
        line("N:${esc(family)};${esc(given)};;;")
        line("FN:${esc(displayName)}")
        if (r.company.isNotBlank()) line("ORG:${esc(r.company)}")
        if (r.title.isNotBlank()) line("TITLE:${esc(r.title)}")
        r.phones.forEach { phone ->
            line("TEL;TYPE=${vcardType(phone.type)}:${esc(phone.number)}")
        }
        r.emails.forEach { line("EMAIL;TYPE=WORK:${esc(it)}") }
        if (r.website.isNotBlank()) line("URL:${esc(r.website)}")
        if (r.address.isNotBlank()) line("ADR;TYPE=WORK:;;${esc(r.address)};;;;")
        if (r.category.isNotBlank()) line("CATEGORIES:${esc(r.category)}")
        if (r.notes.isNotBlank()) line("NOTE:${esc(r.notes)}")
        line("END:VCARD")
        return sb.toString()
    }

    private fun vcardType(type: PhoneType): String = when (type) {
        PhoneType.MOBILE -> "CELL"
        PhoneType.FAX -> "FAX"
        PhoneType.HOME -> "HOME"
        PhoneType.WORK -> "WORK"
        PhoneType.OTHER -> "VOICE"
    }

    /** vCard 3.0 kaçış kuralları: \ , ; ve satır sonları. */
    internal fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}

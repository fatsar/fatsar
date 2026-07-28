package com.fatsar.kartvizit.ocr

import com.fatsar.kartvizit.model.TypedPhone

/** [ParsedCard] ve ona eklenecek not metni. */
data class EnrichedCard(val card: ParsedCard, val extraNotes: String = "")

/**
 * OCR ile çözümlenmiş kartı, aynı görüntüdeki karekod/barkod içeriğiyle
 * zenginleştirir. Yapılandırılmış kişi bilgisi boş alanları doldurur ve
 * telefon/e-posta listelerine katılır; tek bağlantı içeren kodlar web
 * sitesi olur; diğer ham içerik nota eklenir.
 */
object ScanEnricher {

    fun enrich(card: ParsedCard, barcodes: List<ScannedBarcode>): EnrichedCard {
        if (barcodes.isEmpty()) return EnrichedCard(card)

        var name = card.name
        var title = card.title
        var company = card.company
        var website = card.website
        var address = card.address
        val phones = card.phones.toMutableList()
        val emails = card.emails.toMutableList()
        val noteLines = mutableListOf<String>()

        for (barcode in barcodes) {
            val contact = barcode.contact
            if (contact != null) {
                if (name.isBlank()) name = TextNormalizer.smartTitleCase(contact.name)
                if (title.isBlank()) title = TextNormalizer.smartTitleCase(contact.title)
                if (company.isBlank()) company = TextNormalizer.smartTitleCase(contact.org)
                if (address.isBlank()) address = TextNormalizer.smartTitleCase(contact.address)
                contact.phones.forEach { addPhone(phones, it) }
                contact.emails.forEach { addEmail(emails, it) }
                if (website.isBlank()) website = contact.urls.firstOrNull().orEmpty()
            } else if (barcode.url != null) {
                if (website.isBlank()) website = barcode.url
                else noteLines.add("Karekod bağlantısı: ${barcode.url}")
            } else if (barcode.rawValue.isNotBlank()) {
                noteLines.add("Karekod: ${barcode.rawValue.trim()}")
            }
        }

        val enriched = card.copy(
            name = name,
            title = title,
            company = company,
            website = website,
            address = address,
            phones = phones,
            emails = emails
        )
        return EnrichedCard(enriched, noteLines.joinToString("\n"))
    }

    private fun addPhone(list: MutableList<TypedPhone>, phone: TypedPhone) {
        val digits = phone.number.filter { it.isDigit() }
        if (digits.isBlank()) return
        if (list.none { it.number.filter { c -> c.isDigit() } == digits }) list.add(phone)
    }

    private fun addEmail(list: MutableList<String>, email: String) {
        val normalized = email.trim().lowercase()
        if (normalized.isNotBlank() && list.none { it.equals(normalized, ignoreCase = true) }) {
            list.add(normalized)
        }
    }
}

package com.fatsar.kartvizit.ocr

import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.model.TypedPhone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEnricherTest {

    @Test
    fun `karekoddaki kisi bilgisi bos alanlari doldurur`() {
        val card = ParsedCard(phones = listOf(TypedPhone("0212 555 44 33", PhoneType.WORK)))
        val barcode = ScannedBarcode(
            rawValue = "BEGIN:VCARD...",
            contact = ScannedContact(
                name = "AHMET YILMAZ",
                org = "Yıldız A.Ş.",
                phones = listOf(TypedPhone("0532 111 22 33", PhoneType.MOBILE)),
                emails = listOf("ahmet@yildiz.com"),
                urls = listOf("www.yildiz.com")
            )
        )

        val result = ScanEnricher.enrich(card, listOf(barcode))

        assertEquals("Ahmet Yılmaz", result.card.name)
        assertEquals("Yıldız A.Ş.", result.card.company)
        assertEquals("www.yildiz.com", result.card.website)
        assertEquals(2, result.card.phones.size)
        assertEquals(listOf("ahmet@yildiz.com"), result.card.emails)
    }

    @Test
    fun `ayni numara karekoddan tekrar eklenmez`() {
        val card = ParsedCard(phones = listOf(TypedPhone("0532 111 22 33", PhoneType.MOBILE)))
        val barcode = ScannedBarcode(
            rawValue = "x",
            contact = ScannedContact(phones = listOf(TypedPhone("0532 111 22 33", PhoneType.WORK)))
        )
        val result = ScanEnricher.enrich(card, listOf(barcode))
        assertEquals(1, result.card.phones.size)
    }

    @Test
    fun `tek baglanti iceren karekod web sitesi olur`() {
        val card = ParsedCard(name = "Ali Veli")
        val barcode = ScannedBarcode(rawValue = "https://ornek.com", url = "https://ornek.com")
        val result = ScanEnricher.enrich(card, listOf(barcode))
        assertEquals("https://ornek.com", result.card.website)
        assertEquals("", result.extraNotes)
    }

    @Test
    fun `duz metin karekodu nota eklenir`() {
        val card = ParsedCard(name = "Ali Veli")
        val barcode = ScannedBarcode(rawValue = "Stant No: 42")
        val result = ScanEnricher.enrich(card, listOf(barcode))
        assertTrue(result.extraNotes.contains("Stant No: 42"))
    }

    @Test
    fun `karekod yoksa kart degismez`() {
        val card = ParsedCard(name = "Ali Veli")
        val result = ScanEnricher.enrich(card, emptyList())
        assertEquals(card, result.card)
        assertEquals("", result.extraNotes)
    }
}

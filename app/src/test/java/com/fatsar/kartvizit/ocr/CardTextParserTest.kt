package com.fatsar.kartvizit.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextParserTest {

    @Test
    fun `tipik turkce kartvizit dogru cozumlenir`() {
        val lines = listOf(
            OcrLine("AHMET YILMAZ", height = 40f),
            OcrLine("Satış Müdürü", height = 22f),
            OcrLine("YILDIZ TEKSTİL SAN. VE TİC. A.Ş.", height = 26f),
            OcrLine("Tel: +90 212 555 44 33", height = 18f),
            OcrLine("GSM: +90 532 123 45 67", height = 18f),
            OcrLine("ahmet.yilmaz@yildiztekstil.com.tr", height = 18f),
            OcrLine("www.yildiztekstil.com.tr", height = 18f),
            OcrLine("Atatürk Mah. Cumhuriyet Cad. No: 12 Kat: 3", height = 16f),
            OcrLine("34550 Avcılar / İstanbul", height = 16f)
        )

        val card = CardTextParser.parse(lines)

        assertEquals("AHMET YILMAZ", card.name)
        assertEquals("Satış Müdürü", card.title)
        assertEquals("YILDIZ TEKSTİL SAN. VE TİC. A.Ş.", card.company)
        assertEquals(2, card.phones.size)
        assertEquals("+90 212 555 44 33", card.phones[0])
        assertEquals("+90 532 123 45 67", card.phones[1])
        assertEquals(listOf("ahmet.yilmaz@yildiztekstil.com.tr"), card.emails)
        assertEquals("www.yildiztekstil.com.tr", card.website)
        assertTrue(card.address.contains("Atatürk Mah."))
        assertTrue(card.address.contains("34550 Avcılar / İstanbul"))
        assertTrue(card.rawText.contains("AHMET YILMAZ"))
    }

    @Test
    fun `ingilizce kartvizit dogru cozumlenir`() {
        val text = """
            John Smith
            Software Engineer
            Acme Technology Inc.
            Phone: +1 (555) 010-9999
            john.smith@acme.com
            www.acme.com
        """.trimIndent()

        val card = CardTextParser.parse(text)

        assertEquals("John Smith", card.name)
        assertEquals("Software Engineer", card.title)
        assertEquals("Acme Technology Inc.", card.company)
        assertEquals(listOf("+1 (555) 010-9999"), card.phones)
        assertEquals(listOf("john.smith@acme.com"), card.emails)
        assertEquals("www.acme.com", card.website)
    }

    @Test
    fun `isim yoksa e-postadan turetilir`() {
        val card = CardTextParser.parse("mehmet.kaya@firmaadi.com.tr")
        assertEquals("Mehmet Kaya", card.name)
        assertEquals("Firmaadi", card.company)
    }

    @Test
    fun `ayni numara iki kez eklenmez`() {
        val card = CardTextParser.parse(
            """
            Ali Veli
            Tel: 0212 555 44 33
            Telefon: 0212 555 44 33
            """.trimIndent()
        )
        assertEquals(1, card.phones.size)
    }

    @Test
    fun `bos metin bos sonuc dondurur`() {
        val card = CardTextParser.parse("")
        assertEquals("", card.name)
        assertTrue(card.phones.isEmpty())
        assertTrue(card.emails.isEmpty())
    }

    @Test
    fun `serbest e-posta saglayicisi firma sayilmaz`() {
        val card = CardTextParser.parse("ayse.demir@gmail.com")
        assertEquals("Ayse Demir", card.name)
        assertEquals("", card.company)
    }
}

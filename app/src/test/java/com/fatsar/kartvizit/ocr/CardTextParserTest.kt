package com.fatsar.kartvizit.ocr

import com.fatsar.kartvizit.model.PhoneType
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

        // BÜYÜK HARFLE yazılmış alanlar düzgün büyük/küçük harfe çevrilir
        assertEquals("Ahmet Yılmaz", card.name)
        assertEquals("Satış Müdürü", card.title)
        assertEquals("Yıldız Tekstil San. ve Tic. A.Ş.", card.company)
        assertEquals(2, card.phones.size)
        // "Tel:" iş telefonu, "GSM:" cep telefonu olarak ayrılır
        val work = card.phones.first { it.type == PhoneType.WORK }
        val mobile = card.phones.first { it.type == PhoneType.MOBILE }
        assertEquals("+90 212 555 44 33", work.number)
        assertEquals("+90 532 123 45 67", mobile.number)
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
        assertEquals(1, card.phones.size)
        assertEquals("+1 (555) 010-9999", card.phones[0].number)
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

    @Test
    fun `firma adi daha buyuk yazilsa bile kisi adi isim olarak secilir`() {
        val lines = listOf(
            OcrLine("PANDORA", height = 40f), // firma adı en büyük puntoyla
            OcrLine("ALİ VELİ", height = 20f),
            OcrLine("ali.veli@pandora.com.tr", height = 16f)
        )

        val card = CardTextParser.parse(lines)

        assertEquals("Ali Veli", card.name)
        assertEquals("Pandora", card.company)
    }

    @Test
    fun `fazla bosluklar temizlenir`() {
        val lines = listOf(
            OcrLine("  MEHMET   ÖZ  ", height = 30f),
            OcrLine("Tel: 0532 111 22 33", height = 16f)
        )

        val card = CardTextParser.parse(lines)

        assertEquals("Mehmet Öz", card.name)
    }

    @Test
    fun `faks ve cep numaralari ayri turlerle etiketlenir`() {
        val card = CardTextParser.parse(
            """
            Ali Veli
            Tel: 0212 555 44 33
            Faks: 0212 555 44 34
            GSM: 0532 111 22 33
            """.trimIndent()
        )

        assertEquals(3, card.phones.size)
        assertEquals("0212 555 44 33", card.phones.first { it.type == PhoneType.WORK }.number)
        assertEquals("0212 555 44 34", card.phones.first { it.type == PhoneType.FAX }.number)
        assertEquals("0532 111 22 33", card.phones.first { it.type == PhoneType.MOBILE }.number)
    }

    @Test
    fun `etiketsiz numara bicimine gore tahmin edilir`() {
        val card = CardTextParser.parse(
            """
            Ali Veli
            0532 111 22 33
            """.trimIndent()
        )
        assertEquals(1, card.phones.size)
        assertEquals(PhoneType.MOBILE, card.phones[0].type)
    }
}

package com.fatsar.kartvizit.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kullanıcının tek fotoğrafta tarattığı 6 gerçek kartvizitin OCR çıktısına
 * dayanan regresyon testleri. Bu kartlarda ortak hata, kartın EN BÜYÜK yazısı
 * olan LOGO'nun (SODİTAŞ, ÇAKIRLAR, CESTEL, MAYSTA) kişi adı sanılmasıydı.
 * Kişi adı; e-posta kullanıcı adıyla örtüşme ve marka eşleşmesinin elenmesi
 * sayesinde doğru seçilmelidir.
 */
class RealCardsParseTest {

    private fun line(text: String, height: Float) = OcrLine(text, height = height)

    @Test
    fun `soditas karti - logo degil kisi adi secilir`() {
        val card = CardTextParser.parse(
            listOf(
                line("SODİTAŞ", 40f), // logo: kartın en büyük yazısı
                line("Barış GÜNEŞ", 20f),
                line("İş Geliştirme Uzmanı", 12f),
                line("BUSINESS DEVELOPMENT SPECIALIST", 10f),
                line("SODİTAŞ SOLVENT DİSTRİBÜTÖRLÜĞÜ A.Ş.", 13f),
                line("MECLİS-İ MEBUSAN CADDESİ", 11f),
                line("NO: 85 TÜTÜN HAN KAT: 1", 11f),
                line("34427 KABATAŞ - İSTANBUL", 11f),
                line("PHONE : +90 212 334 49 29", 11f),
                line("GSM : +90 532 469 03 81", 11f),
                line("E-MAIL : baris.gunes@soditas.com.tr", 11f)
            )
        )

        assertEquals("Barış Güneş", card.name)
        assertTrue("firma soditas içermeli: ${card.company}",
            TextNormalizer.foldTr(card.company).contains("soditas"))
    }

    @Test
    fun `cakirlar karti - logo degil kisi adi secilir`() {
        val card = CardTextParser.parse(
            listOf(
                line("ÇAKIRLAR", 38f), // logo
                line("Nuh Yüksel", 20f),
                line("Satış Müdürü", 12f),
                line("ÇAKIRLAR Matbaacılık Amb. San. ve Tic. Ltd. Şti.", 12f),
                line("İ.O.S.B Süleyman Demirel Bulvarı, Aykosan San.Sit.", 10f),
                line("Cep:+90 532 346 41 71", 10f),
                line("nyuksel@cakirlar.com / www.cakirlar.com", 10f)
            )
        )

        assertEquals("Nuh Yüksel", card.name)
    }

    @Test
    fun `cestel karti - unvanli ad korunur logo elenir`() {
        val card = CardTextParser.parse(
            listOf(
                line("CESTEL", 36f), // logo
                line("CHEMICAL", 12f),
                line("Dr. Hasan SAYIN, Ph.D", 19f),
                line("Sales Director", 13f),
                line("Mobile : +90 506 158 23 62", 10f),
                line("hasan.sayin@cestelkimya.com", 10f),
                line("www.cestelkimya.com", 10f)
            )
        )

        assertTrue("ad Hasan Sayın içermeli: ${card.name}",
            TextNormalizer.foldTr(card.name).contains("hasan") &&
                TextNormalizer.foldTr(card.name).contains("sayin"))
        assertEquals(listOf("hasan.sayin@cestelkimya.com"), card.emails)
    }

    @Test
    fun `cestel ikinci kart - marka adi isme yapismaz`() {
        // Hata: "Erdi Coşkun" + "CESTEL" birleşip "Erdi Coşkun Cestel" oluyordu
        val card = CardTextParser.parse(
            listOf(
                line("CESTEL", 36f),
                line("CHEMICAL", 12f),
                line("Erdi Coşkun", 20f),
                line("Satış Yöneticisi", 13f),
                line("Mobile : +90 552 677 26 44", 10f),
                line("erdi.coskun@cestelkimya.com", 10f),
                line("www.cestelkimya.com", 10f)
            )
        )

        assertEquals("Erdi Coşkun", card.name)
    }

    @Test
    fun `maysta karti - ingilizce firma adi turkce kurala bozulmaz`() {
        val card = CardTextParser.parse(
            listOf(
                line("MAYSTA", 34f), // logo
                line("Koda Yue", 18f),
                line("Commercial Executive", 12f),
                line("JIANGSU MAYSTA CHEMICAL CO., LTD.", 14f),
                line("Tel: +86 25 85560992-721", 10f),
                line("E-mail:lxxuzheng@maysta.com", 10f)
            )
        )

        assertEquals("Koda Yue", card.name)
        // Türkçe kuralla "Jıangsu"/"Chemıcal" olurdu; İngilizce metinde olmamalı
        assertTrue("firma bozulmamalı: ${card.company}", !card.company.contains("ı"))
    }

    @Test
    fun `oci unid karti - ad ve ingilizce firma dogru`() {
        val card = CardTextParser.parse(
            listOf(
                line("OCI UNID", 30f),
                line("Ahmet Genc (Mr.)", 18f),
                line("Sales Manager", 12f),
                line("OCI UNID EUROPE B.V. Istanbul Office", 13f),
                line("Mobile: +90 537 274 55 22", 10f),
                line("E-mail:ahmet@unidcorp.co.kr", 10f)
            )
        )

        assertEquals("Ahmet Genc (Mr.)", card.name)
        assertTrue("firma bozulmamalı: ${card.company}", !card.company.contains("ı"))
    }

    @Test
    fun `arka plan gurultusu isim olarak secilmez`() {
        // Mouse pad üzerindeki "Full L" yazısı kart kümesine karışmıştı
        val card = CardTextParser.parse(
            listOf(
                line("Full L", 30f), // arka plan gürültüsü, büyük punto
                line("Koda Yue", 18f),
                line("Commercial Executive", 12f),
                line("E-mail:lxxuzheng@maysta.com", 10f)
            )
        )

        assertEquals("Koda Yue", card.name)
    }
}

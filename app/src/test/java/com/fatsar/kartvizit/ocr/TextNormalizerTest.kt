package com.fatsar.kartvizit.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `buyuk harfli isim turkce kurallara gore duzeltilir`() {
        assertEquals("Ahmet Yılmaz", TextNormalizer.smartTitleCase("AHMET YILMAZ"))
        assertEquals("İbrahim Şişli", TextNormalizer.smartTitleCase("İBRAHİM ŞİŞLİ"))
        assertEquals("Ayşe Çelik", TextNormalizer.smartTitleCase("ayşe çelik"))
    }

    @Test
    fun `firma kisaltmalari korunur baglaclar kucuk kalir`() {
        assertEquals(
            "Yıldız Tekstil San. ve Tic. A.Ş.",
            TextNormalizer.smartTitleCase("YILDIZ TEKSTİL SAN. VE TİC. A.Ş.")
        )
        assertEquals("Acme LTD", TextNormalizer.smartTitleCase("ACME LTD"))
    }

    @Test
    fun `karisik yazim ve rakamli parcalar degistirilmez`() {
        assertEquals("McDonald Plaza", TextNormalizer.smartTitleCase("McDonald Plaza"))
        assertEquals("No: 12 Kat: 3", TextNormalizer.smartTitleCase("No: 12 Kat: 3"))
    }

    @Test
    fun `fazla bosluklar temizlenir`() {
        assertEquals("Ali Veli", TextNormalizer.tidy("  Ali    Veli "))
    }

    @Test
    fun `ad soyad dogru ayrilir`() {
        assertEquals("Ahmet" to "Yılmaz", TextNormalizer.splitName("Ahmet Yılmaz"))
        assertEquals("Ahmet Can" to "Yılmaz", TextNormalizer.splitName("Ahmet Can Yılmaz"))
        assertEquals("Ahmet" to "", TextNormalizer.splitName("Ahmet"))
        assertEquals("" to "", TextNormalizer.splitName("   "))
    }

    @Test
    fun `turk cep telefonu ayirt edilir`() {
        assertTrue(TextNormalizer.isTurkishMobile("0532 123 45 67"))
        assertTrue(TextNormalizer.isTurkishMobile("+90 532 123 45 67"))
        assertFalse(TextNormalizer.isTurkishMobile("0212 555 44 33"))
    }
}

package com.fatsar.kartvizit.export

import com.fatsar.kartvizit.model.ContactRecord
import com.fatsar.kartvizit.model.PhoneType
import com.fatsar.kartvizit.model.TypedPhone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VcfWriterTest {

    private fun sampleRecord() = ContactRecord(
        name = "Ahmet Can Yılmaz",
        title = "Satış Müdürü",
        company = "Yıldız Tekstil A.Ş.",
        phones = listOf(
            TypedPhone("+90 532 123 45 67", PhoneType.MOBILE),
            TypedPhone("0212 555 44 33", PhoneType.FAX)
        ),
        emails = listOf("ahmet@yildiz.com.tr"),
        website = "www.yildiz.com.tr",
        address = "Atatürk Mah. No: 12, İstanbul",
        category = "Müşteriler",
        notes = "Fuarda tanışıldı"
    )

    @Test
    fun `gecerli vcard uretilir`() {
        val vcf = VcfWriter.build(listOf(sampleRecord()))

        assertTrue(vcf.startsWith("BEGIN:VCARD"))
        assertTrue(vcf.contains("VERSION:3.0"))
        // Soyad;Ad sırasıyla yapısal isim
        assertTrue(vcf.contains("N:Yılmaz;Ahmet Can;;;"))
        assertTrue(vcf.contains("FN:Ahmet Can Yılmaz"))
        assertTrue(vcf.contains("ORG:Yıldız Tekstil A.Ş."))
        assertTrue(vcf.contains("TITLE:Satış Müdürü"))
        assertTrue(vcf.contains("TEL;TYPE=CELL:+90 532 123 45 67"))
        assertTrue(vcf.contains("TEL;TYPE=FAX:0212 555 44 33"))
        assertTrue(vcf.contains("EMAIL;TYPE=WORK:ahmet@yildiz.com.tr"))
        assertTrue(vcf.contains("URL:www.yildiz.com.tr"))
        assertTrue(vcf.contains("CATEGORIES:Müşteriler"))
        assertTrue(vcf.trimEnd().endsWith("END:VCARD"))
    }

    @Test
    fun `birden fazla kayit tek dosyada yer alir`() {
        val vcf = VcfWriter.build(
            listOf(sampleRecord(), sampleRecord().copy(name = "Zeynep Kaya"))
        )
        val count = Regex("BEGIN:VCARD").findAll(vcf).count()
        assertTrue(count == 2)
        assertTrue(vcf.contains("N:Kaya;Zeynep;;;"))
    }

    @Test
    fun `ozel karakterler kacislanir`() {
        val record = sampleRecord().copy(
            name = "Ali; Veli",
            company = "Kaya, Oğulları\\Ortakları"
        )
        val vcf = VcfWriter.build(listOf(record))
        assertTrue(vcf.contains("FN:Ali\\; Veli"))
        assertTrue(vcf.contains("ORG:Kaya\\, Oğulları\\\\Ortakları"))
    }

    @Test
    fun `isim yoksa firma adi kullanilir`() {
        val record = sampleRecord().copy(name = "")
        val vcf = VcfWriter.build(listOf(record))
        assertTrue(vcf.contains("FN:Yıldız Tekstil A.Ş."))
        assertFalse(vcf.contains("FN:\r\n"))
    }

    @Test
    fun `adres calisma adresi olarak yazilir`() {
        val vcf = VcfWriter.build(listOf(sampleRecord()))
        assertTrue(vcf.contains("ADR;TYPE=WORK:;;Atatürk Mah. No: 12\\, İstanbul;;;;"))
    }
}

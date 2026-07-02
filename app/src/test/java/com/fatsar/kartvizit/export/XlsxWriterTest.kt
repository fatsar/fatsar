package com.fatsar.kartvizit.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private fun writeAndReadEntries(
        headers: List<String>,
        rows: List<List<String>>
    ): Map<String, String> {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(headers, rows, "Kartvizitler", out)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }

    @Test
    fun `gecerli xlsx paketi olusturulur`() {
        val entries = writeAndReadEntries(
            headers = listOf("Ad Soyad", "Telefon"),
            rows = listOf(listOf("Ahmet Yılmaz", "+90 532 123 45 67"))
        )

        assertTrue("[Content_Types].xml" in entries)
        assertTrue("_rels/.rels" in entries)
        assertTrue("xl/workbook.xml" in entries)
        assertTrue("xl/_rels/workbook.xml.rels" in entries)
        assertTrue("xl/styles.xml" in entries)
        assertTrue("xl/worksheets/sheet1.xml" in entries)

        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Ad Soyad"))
        assertTrue(sheet.contains("Ahmet Yılmaz"))
        assertTrue(sheet.contains("+90 532 123 45 67"))
        assertTrue(sheet.contains("""<row r="2">"""))
        assertTrue(entries.getValue("xl/workbook.xml").contains("Kartvizitler"))
    }

    @Test
    fun `ozel karakterler xml icinde kacislanir`() {
        val entries = writeAndReadEntries(
            headers = listOf("Şirket"),
            rows = listOf(listOf("""Kaya & Oğulları <Ltd> "Şti"""))
        )
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Kaya &amp; Oğulları &lt;Ltd&gt; &quot;Şti"))
    }

    @Test
    fun `sutun adlari dogru uretilir`() {
        assertEquals("A", XlsxWriter.columnRef(0))
        assertEquals("Z", XlsxWriter.columnRef(25))
        assertEquals("AA", XlsxWriter.columnRef(26))
        assertEquals("AB", XlsxWriter.columnRef(27))
    }

    @Test
    fun `bos hucreler yazilmaz satir numaralari dogrudur`() {
        val entries = writeAndReadEntries(
            headers = listOf("A", "B"),
            rows = listOf(listOf("", "değer"))
        )
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""<c r="B2" t="inlineStr">"""))
        assertTrue(!sheet.contains("""<c r="A2""""))
    }
}

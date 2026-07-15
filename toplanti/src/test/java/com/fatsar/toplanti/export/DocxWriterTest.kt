package com.fatsar.toplanti.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class DocxWriterTest {

    @Test
    fun `gecerli docx paketi uretilir`() {
        val out = ByteArrayOutputStream()
        DocxWriter.write(
            out,
            listOf(
                "h1" to "Sprint Planlama Toplantısı",
                "h2" to "Kararlar",
                null to "Bütçe & takvim onaylandı <tamamı>"
            )
        )
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), entries.keys)
        val doc = entries["word/document.xml"]!!
        assertTrue(doc.contains("Sprint Planlama Toplantısı"))
        // XML kaçışları uygulanmış olmalı
        assertTrue(doc.contains("Bütçe &amp; takvim onaylandı &lt;tamamı&gt;"))
    }

    @Test
    fun `xml kacislari dogru`() {
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&apos;f", DocxWriter.escape("a&b<c>d\"e'f"))
    }
}

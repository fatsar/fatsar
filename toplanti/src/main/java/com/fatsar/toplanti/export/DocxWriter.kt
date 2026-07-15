package com.fatsar.toplanti.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Harici kütüphane olmadan basit .docx (Office Open XML) üretir; Word,
 * Google Dokümanlar ve LibreOffice ile açılır. Depodaki XlsxWriter ile
 * aynı sıfır-bağımlılık yaklaşımı izlenir.
 */
object DocxWriter {

    /** [sections]: ("h1"/"h2"/null, metin) çiftleri. */
    fun write(out: OutputStream, sections: List<Pair<String?, String>>) {
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", CONTENT_TYPES)
            put(zip, "_rels/.rels", RELS)
            put(zip, "word/document.xml", document(sections))
        }
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun document(sections: List<Pair<String?, String>>): String {
        val body = StringBuilder()
        for ((style, text) in sections) {
            val (size, bold) = when (style) {
                "h1" -> 32 to true   // yarım punto cinsinden (16pt)
                "h2" -> 26 to true   // 13pt
                else -> 22 to false  // 11pt
            }
            body.append("<w:p><w:r><w:rPr>")
            if (bold) body.append("<w:b/>")
            body.append("<w:sz w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">")
            body.append(escape(text))
            body.append("</w:t></w:r></w:p>")
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
    }

    internal fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
}

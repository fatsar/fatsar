package com.fatsar.notlar.markdown

/**
 * Markdown'un saf Kotlin (Android'den bağımsız) ayrıştırıcısı.
 * Çıktısı bir blok listesidir; Android tarafında [MarkdownRenderer] bunu
 * Spannable'a çevirir. Böylece ayrıştırma mantığı birim testlerle sınanabilir.
 */
enum class MdStyle { BOLD, ITALIC, CODE, STRIKE, LINK, HIGHLIGHT }

data class MdSpan(val style: MdStyle, val start: Int, val end: Int, val href: String? = null)

/** Düz metin + üzerine uygulanacak biçim aralıkları. */
data class MdText(val text: String, val spans: List<MdSpan> = emptyList())

sealed class MdBlock {
    data class Heading(val level: Int, val text: MdText) : MdBlock()
    data class Paragraph(val text: MdText) : MdBlock()
    data class Bullet(val indent: Int, val text: MdText) : MdBlock()
    data class Numbered(val indent: Int, val number: Int, val text: MdText) : MdBlock()
    data class Task(val indent: Int, val done: Boolean, val text: MdText) : MdBlock()
    data class Quote(val text: MdText) : MdBlock()
    data class Code(val language: String?, val code: String) : MdBlock()
    object Rule : MdBlock()
}

object MarkdownParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val RULE = Regex("^\\s{0,3}([-*_])\\s*(\\1\\s*){2,}$")
    private val FENCE = Regex("^\\s{0,3}```\\s*([A-Za-z0-9+#._-]*)\\s*$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val TASK = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s*(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")

    fun parse(source: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks.add(MdBlock.Paragraph(InlineParser.parse(paragraph.toString())))
                paragraph.setLength(0)
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fence = FENCE.matchEntire(line)
            if (fence != null) {
                flushParagraph()
                val language = fence.groupValues[1].ifBlank { null }
                val code = StringBuilder()
                i++
                while (i < lines.size && FENCE.matchEntire(lines[i]) == null) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                if (i < lines.size) i++ // kapanış çitini atla
                blocks.add(MdBlock.Code(language, code.toString()))
                continue
            }
            if (line.isBlank()) {
                flushParagraph()
                i++
                continue
            }
            if (RULE.matchEntire(line) != null) {
                flushParagraph()
                blocks.add(MdBlock.Rule)
                i++
                continue
            }
            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                flushParagraph()
                blocks.add(
                    MdBlock.Heading(
                        heading.groupValues[1].length,
                        InlineParser.parse(heading.groupValues[2].trimEnd().trimEnd('#').trimEnd())
                    )
                )
                i++
                continue
            }
            val task = TASK.matchEntire(line)
            if (task != null) {
                flushParagraph()
                blocks.add(
                    MdBlock.Task(
                        indentOf(task.groupValues[1]),
                        task.groupValues[2].lowercase() == "x",
                        InlineParser.parse(task.groupValues[3])
                    )
                )
                i++
                continue
            }
            val bullet = BULLET.matchEntire(line)
            if (bullet != null) {
                flushParagraph()
                blocks.add(
                    MdBlock.Bullet(
                        indentOf(bullet.groupValues[1]),
                        InlineParser.parse(bullet.groupValues[2])
                    )
                )
                i++
                continue
            }
            val numbered = NUMBERED.matchEntire(line)
            if (numbered != null) {
                flushParagraph()
                blocks.add(
                    MdBlock.Numbered(
                        indentOf(numbered.groupValues[1]),
                        numbered.groupValues[2].toInt(),
                        InlineParser.parse(numbered.groupValues[3])
                    )
                )
                i++
                continue
            }
            val quote = QUOTE.matchEntire(line)
            if (quote != null) {
                flushParagraph()
                val quoted = StringBuilder(quote.groupValues[1])
                i++
                while (i < lines.size) {
                    val more = QUOTE.matchEntire(lines[i]) ?: break
                    quoted.append('\n').append(more.groupValues[1])
                    i++
                }
                blocks.add(MdBlock.Quote(InlineParser.parse(quoted.toString())))
                continue
            }
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line.trim())
            i++
        }
        flushParagraph()
        return blocks
    }

    /** İç içe listelerde her 2 boşluk (sekme = 4) bir seviye sayılır. */
    private fun indentOf(prefix: String): Int {
        var width = 0
        for (ch in prefix) width += if (ch == '\t') 4 else 1
        return (width / 2).coerceAtMost(4)
    }
}

/** Satır içi biçimler: **kalın**, *eğik*, `kod`, ~~üstü çizili~~, ==vurgu==, [bağlantı](adres). */
object InlineParser {

    fun parse(source: String): MdText {
        val out = StringBuilder()
        val spans = ArrayList<MdSpan>()
        var i = 0
        while (i < source.length) {
            val ch = source[i]
            if (ch == '\\' && i + 1 < source.length && isPunctuation(source[i + 1])) {
                out.append(source[i + 1])
                i += 2
                continue
            }
            if (ch == '`') {
                val end = source.indexOf('`', i + 1)
                if (end > i + 1) {
                    val start = out.length
                    out.append(source, i + 1, end)
                    spans.add(MdSpan(MdStyle.CODE, start, out.length))
                    i = end + 1
                    continue
                }
            }
            if (ch == '!' && i + 1 < source.length && source[i + 1] == '[') {
                val link = readLink(source, i + 1)
                if (link != null) {
                    val start = out.length
                    appendText(out, spans, parse(link.label))
                    if (out.length == start) out.append(link.href)
                    spans.add(MdSpan(MdStyle.LINK, start, out.length, link.href))
                    i = link.end
                    continue
                }
            }
            if (ch == '[') {
                val link = readLink(source, i)
                if (link != null) {
                    val start = out.length
                    appendText(out, spans, parse(link.label))
                    if (out.length == start) out.append(link.href)
                    spans.add(MdSpan(MdStyle.LINK, start, out.length, link.href))
                    i = link.end
                    continue
                }
            }
            val delimiter = delimiterAt(source, i)
            if (delimiter != null) {
                val close = findClosing(source, i + delimiter.marker.length, delimiter.marker)
                if (close > 0) {
                    val inner = parse(source.substring(i + delimiter.marker.length, close))
                    val start = out.length
                    appendText(out, spans, inner)
                    if (out.length > start) spans.add(MdSpan(delimiter.style, start, out.length))
                    i = close + delimiter.marker.length
                    continue
                }
            }
            out.append(ch)
            i++
        }
        return MdText(out.toString(), spans)
    }

    private fun appendText(out: StringBuilder, spans: MutableList<MdSpan>, text: MdText) {
        val offset = out.length
        out.append(text.text)
        for (span in text.spans) {
            spans.add(span.copy(start = span.start + offset, end = span.end + offset))
        }
    }

    private data class Delimiter(val marker: String, val style: MdStyle)

    private fun delimiterAt(source: String, index: Int): Delimiter? {
        val two = if (index + 1 < source.length) source.substring(index, index + 2) else ""
        return when {
            two == "**" -> Delimiter("**", MdStyle.BOLD)
            two == "__" -> Delimiter("__", MdStyle.BOLD)
            two == "~~" -> Delimiter("~~", MdStyle.STRIKE)
            two == "==" -> Delimiter("==", MdStyle.HIGHLIGHT)
            source[index] == '*' -> Delimiter("*", MdStyle.ITALIC)
            source[index] == '_' -> Delimiter("_", MdStyle.ITALIC)
            else -> null
        }
    }

    /** Aynı işaretin kapanışını bulur; kaçışlı işaretleri ve boş içeriği atlar. */
    private fun findClosing(source: String, from: Int, marker: String): Int {
        var i = from
        while (i < source.length) {
            if (source[i] == '\\') {
                i += 2
                continue
            }
            if (source.startsWith(marker, i)) {
                // Tek karakterli işaretin çift işaretle (ör. ** ) karışmasını önle
                if (marker.length == 1 && i + 1 < source.length && source[i + 1] == marker[0]) {
                    i += 2
                    continue
                }
                return if (i > from) i else -1
            }
            i++
        }
        return -1
    }

    private data class Link(val label: String, val href: String, val end: Int)

    private fun readLink(source: String, start: Int): Link? {
        if (start >= source.length || source[start] != '[') return null
        var depth = 0
        var i = start
        var labelEnd = -1
        loop@ while (i < source.length) {
            when (source[i]) {
                '\\' -> i++
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        labelEnd = i
                        break@loop
                    }
                }
            }
            i++
        }
        if (labelEnd < 0 || labelEnd + 1 >= source.length || source[labelEnd + 1] != '(') return null
        val hrefEnd = source.indexOf(')', labelEnd + 2)
        if (hrefEnd < 0) return null
        return Link(
            label = source.substring(start + 1, labelEnd),
            href = source.substring(labelEnd + 2, hrefEnd).trim(),
            end = hrefEnd + 1
        )
    }

    private fun isPunctuation(ch: Char): Boolean = ch in "\\`*_{}[]()#+-.!~=>|"
}

package com.fatsar.notlar.markdown

/**
 * Düzenleyicideki markdown yardımcıları (biçim kısayolları, liste devamı).
 * Saf metin üzerinde çalışır; Android'e bağlı olmadığı için testlenebilir.
 */
object MarkdownEditing {

    data class Edit(val text: String, val selectionStart: Int, val selectionEnd: Int)

    private val LIST_PREFIX = Regex("^(\\s*)([-*+]\\s+\\[[ xX]]\\s+|[-*+]\\s+|\\d{1,9}[.)]\\s+|>\\s+)")

    /**
     * Seçimi [marker] ile sarar; zaten sarılıysa işaretleri kaldırır.
     * Seçim yoksa imlecin bulunduğu sözcüğe uygulanır.
     */
    fun toggleWrap(text: String, selectionStart: Int, selectionEnd: Int, marker: String): Edit {
        var start = selectionStart.coerceIn(0, text.length)
        var end = selectionEnd.coerceIn(0, text.length)
        if (start > end) {
            val t = start; start = end; end = t
        }
        if (start == end) {
            val word = wordAt(text, start)
            start = word.first
            end = word.second
        }
        val len = marker.length
        val hasOuter = start >= len && end + len <= text.length &&
            text.regionMatches(start - len, marker, 0, len) &&
            text.regionMatches(end, marker, 0, len)
        if (hasOuter) {
            val stripped = text.substring(0, start - len) + text.substring(start, end) +
                text.substring(end + len)
            return Edit(stripped, start - len, end - len)
        }
        val inner = text.substring(start, end)
        if (inner.length >= 2 * len && inner.startsWith(marker) && inner.endsWith(marker)) {
            val stripped = text.substring(0, start) + inner.substring(len, inner.length - len) +
                text.substring(end)
            return Edit(stripped, start, end - 2 * len)
        }
        val wrapped = text.substring(0, start) + marker + inner + marker + text.substring(end)
        return Edit(wrapped, start + len, end + len)
    }

    /**
     * İmlecin bulunduğu satır(lar)ın başına [prefix] ekler; zaten varsa kaldırır
     * (madde işareti, alıntı, başlık düğmeleri).
     */
    fun togglePrefix(text: String, selectionStart: Int, selectionEnd: Int, prefix: String): Edit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (start == 0) 0 else it + 1 }
        var lineEnd = text.indexOf('\n', end)
        if (lineEnd < 0) lineEnd = text.length
        val region = text.substring(lineStart, lineEnd)
        val lines = region.split('\n')
        val allPrefixed = lines.all { it.trimStart().startsWith(prefix.trim()) && it.isNotBlank() }
        val updated = lines.joinToString("\n") { line ->
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val rest = line.substring(indent.length)
            if (allPrefixed) indent + rest.removePrefix(prefix.trim()).removePrefix(" ")
            else indent + prefix + rest
        }
        val newText = text.substring(0, lineStart) + updated + text.substring(lineEnd)
        val delta = updated.length - region.length
        return Edit(newText, (start + if (allPrefixed) -prefix.length else prefix.length).coerceIn(0, newText.length), (end + delta).coerceIn(0, newText.length))
    }

    /**
     * Enter'a basıldıktan **sonra** çağrılır ([cursor] eklenen satır sonunun
     * hemen sağı): listeyi sürdürür — "- ", "1. ", "> " ya da "- [ ] " öneki
     * yeni satıra taşınır. Madde boşsa (yalnızca önek varsa) liste biter ve
     * önek silinir. Liste satırında değilse null döner, Enter normal işler.
     */
    fun afterNewline(text: String, cursor: Int): Edit? {
        if (cursor <= 0 || cursor > text.length || text[cursor - 1] != '\n') return null
        val lineEnd = cursor - 1
        val lineStart = if (lineEnd == 0) 0 else text.lastIndexOf('\n', lineEnd - 1) + 1
        val line = text.substring(lineStart, lineEnd)
        val match = LIST_PREFIX.find(line) ?: return null
        val indent = match.groupValues[1]
        val marker = match.groupValues[2]
        val content = line.substring(match.value.length)
        if (content.isBlank()) {
            val newText = text.substring(0, lineStart) + text.substring(cursor)
            return Edit(newText, lineStart, lineStart)
        }
        val insertion = indent + nextMarker(marker)
        val newText = text.substring(0, cursor) + insertion + text.substring(cursor)
        val caret = cursor + insertion.length
        return Edit(newText, caret, caret)
    }

    /** İmlecin bulunduğu satırdaki "- [ ]" kutusunu işaretler / işareti kaldırır. */
    fun toggleTask(text: String, cursor: Int): Edit? {
        val position = cursor.coerceIn(0, text.length)
        val lineStart = if (position == 0) 0 else text.lastIndexOf('\n', position - 1) + 1
        var lineEnd = text.indexOf('\n', position)
        if (lineEnd < 0) lineEnd = text.length
        val line = text.substring(lineStart, lineEnd)
        val box = Regex("^(\\s*[-*+]\\s+)\\[([ xX])]").find(line)
        val updated = if (box != null) {
            val checked = box.groupValues[2].lowercase() == "x"
            line.replaceRange(box.range, box.groupValues[1] + if (checked) "[ ]" else "[x]")
        } else {
            val bullet = Regex("^(\\s*)([-*+]\\s+)?").find(line)!!
            bullet.groupValues[1] + "- [ ] " + line.substring(bullet.value.length)
        }
        val newText = text.substring(0, lineStart) + updated + text.substring(lineEnd)
        val caret = (position + updated.length - line.length).coerceIn(lineStart, lineStart + updated.length)
        return Edit(newText, caret, caret)
    }

    private fun nextMarker(marker: String): String {
        val numbered = Regex("^(\\d{1,9})([.)])(\\s+)$").find(marker)
        if (numbered != null) {
            val n = numbered.groupValues[1].toInt() + 1
            return "$n${numbered.groupValues[2]}${numbered.groupValues[3]}"
        }
        // İşaretli görev maddesinden sonra yeni madde boş kutuyla başlar
        val task = Regex("^([-*+]\\s+)\\[[ xX]](\\s+)$").find(marker)
        if (task != null) return task.groupValues[1] + "[ ]" + task.groupValues[2]
        return marker
    }

    private fun wordAt(text: String, index: Int): Pair<Int, Int> {
        var start = index
        var end = index
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++
        return start to end
    }
}

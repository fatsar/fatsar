package com.fatsar.notlar.model

/**
 * Tek bir not. Gövde ham markdown metnidir; başlık gövdeden türetilir
 * (ayrı bir başlık alanı tutmak, yazarken iki alan arasında gidip gelmeyi
 * gerektirdiği için bilinçli olarak tercih edilmedi).
 */
data class Note(
    val id: String,
    val body: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pinned: Boolean = false,
    /** Kalemle çizilen katmanın dosya adı (cizimler/ altında), yoksa null. */
    val sketchName: String? = null
) {
    /** Listede gösterilen başlık: ilk dolu satır, markdown işaretlerinden arındırılmış. */
    val title: String get() = NoteText.title(body)

    /** Başlık satırının altındaki ilk dolu satır (liste önizlemesi). */
    val snippet: String get() = NoteText.snippet(body)

    val isEmpty: Boolean get() = body.isBlank() && sketchName == null
}

object NoteText {

    private const val TITLE_LIMIT = 80
    private const val SNIPPET_LIMIT = 140

    fun title(body: String): String {
        val line = body.lineSequence().firstOrNull { it.isNotBlank() } ?: return ""
        return clip(clean(line), TITLE_LIMIT)
    }

    fun snippet(body: String): String {
        val lines = body.lines()
        val firstIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstIndex < 0) return ""
        val rest = lines.drop(firstIndex + 1)
            .firstOrNull { it.isNotBlank() && clean(it).isNotEmpty() }
            ?: return ""
        return clip(clean(rest), SNIPPET_LIMIT)
    }

    /** Satırdaki markdown işaretlerini (başlık, liste, vurgu…) temizler. */
    fun clean(line: String): String {
        var s = line.trim()
        s = s.removePrefix(">").trim()
        s = s.trimStart('#').trim()
        s = s.replace(Regex("^([-*+]|\\d+[.)])\\s+"), "")
        s = s.replace(Regex("^\\[[ xX]]\\s+"), "")
        s = s.replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("[*_`~=]{1,2}"), "")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun clip(s: String, limit: Int): String =
        if (s.length <= limit) s else s.take(limit).trimEnd() + "…"
}

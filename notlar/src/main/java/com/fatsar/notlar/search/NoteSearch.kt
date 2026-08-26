package com.fatsar.notlar.search

import com.fatsar.notlar.model.Note
import java.util.Locale

/**
 * Anında arama: her tuş vuruşunda tüm notların başlık ve gövdesinde
 * eşleşme aranır. Türkçe'ye özgü büyük/küçük harf kuralları (I/ı, İ/i) ve
 * aksan farkları (ş/s, ğ/g…) göz ardı edilir; "cizim" araması "çizim"i bulur.
 */
object NoteSearch {

    private val TURKISH = Locale("tr", "TR")

    private const val ACCENTED = "çğıöşüâîûÇĞİÖŞÜÂÎÛ"
    private const val PLAIN = "cgiosuaiucgiosuaiu"

    fun fold(text: String): String {
        val lower = text.lowercase(TURKISH)
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            val idx = ACCENTED.indexOf(ch)
            sb.append(if (idx >= 0) PLAIN[idx] else ch)
        }
        return sb.toString()
    }

    /** Boşlukla ayrılmış her sözcüğün ayrı ayrı bulunması gerekir (AND). */
    fun terms(query: String): List<String> =
        fold(query).split(' ', '\t', '\n').filter { it.isNotBlank() }

    fun matches(note: Note, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val haystack = fold(note.body)
        return terms.all { haystack.contains(it) }
    }

    /**
     * Sorguya uyan notlar; sabitlenenler üstte, sonra en son düzenlenen.
     * Sorgu varsa başlıkta geçenler gövdede geçenlerin üstüne çıkar.
     */
    fun filter(notes: List<Note>, query: String): List<Note> {
        val terms = terms(query)
        val hits = if (terms.isEmpty()) notes else notes.filter { matches(it, terms) }
        return hits.sortedWith(
            compareByDescending<Note> { it.pinned }
                .thenByDescending { titleHit(it, terms) }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id }
        )
    }

    private fun titleHit(note: Note, terms: List<String>): Boolean {
        if (terms.isEmpty()) return false
        val title = fold(note.title)
        return terms.all { title.contains(it) }
    }

    /**
     * [text] içinde sorgu sözcüklerinin geçtiği aralıklar — liste satırlarında
     * eşleşmeyi vurgulamak için. Çakışan aralıklar birleştirilir.
     */
    fun highlights(text: String, query: String): List<IntRange> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        val folded = fold(text)
        val spans = ArrayList<IntRange>()
        for (term in terms) {
            var from = folded.indexOf(term)
            while (from >= 0) {
                spans.add(from until (from + term.length))
                from = folded.indexOf(term, from + term.length)
            }
        }
        if (spans.isEmpty()) return spans
        spans.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        var current = spans[0]
        for (i in 1 until spans.size) {
            val next = spans[i]
            current = if (next.first <= current.last + 1) {
                current.first..maxOf(current.last, next.last)
            } else {
                merged.add(current); next
            }
        }
        merged.add(current)
        return merged
    }
}

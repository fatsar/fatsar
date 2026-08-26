package com.fatsar.notlar.search

import com.fatsar.notlar.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchTest {

    private fun note(id: String, body: String, updatedAt: Long = 0L, pinned: Boolean = false) =
        Note(id = id, body = body, createdAt = 0L, updatedAt = updatedAt, pinned = pinned)

    @Test
    fun `turkce buyuk kucuk harf ve aksan farki aramayi bozmaz`() {
        assertEquals("cizim isi", NoteSearch.fold("ÇİZİM İŞİ"))
        assertEquals("istanbul", NoteSearch.fold("İSTANBUL"))
        assertEquals("irmak", NoteSearch.fold("Irmak"))

        val notes = listOf(note("1", "Çizim işi yarın"))
        assertEquals(1, NoteSearch.filter(notes, "cizim").size)
        assertEquals(1, NoteSearch.filter(notes, "ÇİZİM").size)
        assertEquals(0, NoteSearch.filter(notes, "resim").size)
    }

    @Test
    fun `bosluklu sorguda tum sozcukler bulunmali`() {
        val notes = listOf(note("1", "alisveris listesi: sut ve ekmek"))
        assertEquals(1, NoteSearch.filter(notes, "sut ekmek").size)
        assertEquals(0, NoteSearch.filter(notes, "sut zeytin").size)
    }

    @Test
    fun `bos sorgu tum notlari verir`() {
        val notes = listOf(note("1", "bir"), note("2", "iki"))
        assertEquals(2, NoteSearch.filter(notes, "   ").size)
    }

    @Test
    fun `sabitlenen notlar ustte sonra en son duzenlenen gelir`() {
        val notes = listOf(
            note("eski", "eski not", updatedAt = 100),
            note("yeni", "yeni not", updatedAt = 900),
            note("sabit", "sabit not", updatedAt = 50, pinned = true)
        )
        assertEquals(listOf("sabit", "yeni", "eski"), NoteSearch.filter(notes, "").map { it.id })
    }

    @Test
    fun `baslikta gecen not govdede gecenin ustune cikar`() {
        val notes = listOf(
            note("govde", "Toplanti notu\n\nbutce konusuldu", updatedAt = 900),
            note("baslik", "Butce plani\n\nayrintilar", updatedAt = 100)
        )
        assertEquals(listOf("baslik", "govde"), NoteSearch.filter(notes, "butce").map { it.id })
    }

    @Test
    fun `eslesme araliklari vurgulanmak uzere dondurulur`() {
        val spans = NoteSearch.highlights("Çizim ve çizim", "cizim")
        assertEquals(2, spans.size)
        assertEquals(0, spans[0].first)
        assertEquals(4, spans[0].last)
        assertEquals(9, spans[1].first)
        assertEquals(13, spans[1].last)
    }

    @Test
    fun `eslesme yoksa vurgulama yapilmaz`() {
        assertTrue(NoteSearch.highlights("bir metin", "yok").isEmpty())
        assertTrue(NoteSearch.highlights("bir metin", "").isEmpty())
    }

    @Test
    fun `matches govdede arar`() {
        val note = note("1", "# Baslik\n\ngizli kelime burada")
        assertTrue(NoteSearch.matches(note, NoteSearch.terms("gizli")))
        assertFalse(NoteSearch.matches(note, NoteSearch.terms("bulunmaz")))
    }
}

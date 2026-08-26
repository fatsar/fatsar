package com.fatsar.notlar.data

import com.fatsar.notlar.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSerializerTest {

    @Test
    fun `notlar yazilip geri okunur`() {
        val notes = listOf(
            Note("a", "# Baslik\n\nicerik", createdAt = 10, updatedAt = 20),
            Note("b", "ikinci", createdAt = 30, updatedAt = 40, pinned = true, sketchName = "cizim-1")
        )
        val restored = NoteSerializer.fromJson(NoteSerializer.toJson(notes))
        assertEquals(notes, restored)
    }

    @Test
    fun `turkce karakterler ve satir sonlari korunur`() {
        val notes = listOf(Note("a", "Şu ğüzel İş\nikinci satır", createdAt = 1, updatedAt = 2))
        assertEquals(notes, NoteSerializer.fromJson(NoteSerializer.toJson(notes)))
    }

    @Test
    fun `bozuk dosya cokme yerine bos liste verir`() {
        assertTrue(NoteSerializer.fromJson("{bu json degil").isEmpty())
        assertTrue(NoteSerializer.fromJson("").isEmpty())
    }

    @Test
    fun `eksik alanlar makul varsayilanlarla doldurulur`() {
        val notes = NoteSerializer.fromJson("""{"notes":[{"id":"x","createdAt":5}]}""")
        assertEquals(1, notes.size)
        assertEquals("", notes[0].body)
        assertEquals(5L, notes[0].updatedAt)
        assertNull(notes[0].sketchName)
    }

    @Test
    fun `kimliksiz kayit atlanir digerleri kurtarilir`() {
        val notes = NoteSerializer.fromJson("""{"notes":[{"body":"kimliksiz"},{"id":"ok","body":"var"}]}""")
        assertEquals(listOf("ok"), notes.map { it.id })
    }

    @Test
    fun `duz dizi bicimi de okunur`() {
        val notes = NoteSerializer.fromJson("""[{"id":"a","body":"metin","updatedAt":7}]""")
        assertEquals(1, notes.size)
        assertEquals(7L, notes[0].updatedAt)
    }
}

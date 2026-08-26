package com.fatsar.notlar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextTest {

    @Test
    fun `baslik ilk dolu satirdan turetilir`() {
        assertEquals("Baslik", NoteText.title("# Baslik\n\nicerik"))
        assertEquals("Baslik", NoteText.title("\n\n## Baslik"))
        assertEquals("Onemli not", NoteText.title("**Onemli** not"))
        assertEquals("elma", NoteText.title("- elma"))
        assertEquals("is", NoteText.title("- [ ] is"))
    }

    @Test
    fun `bos govdede baslik bostur`() {
        assertEquals("", NoteText.title("   \n\n "))
        assertTrue(Note("1").isEmpty)
    }

    @Test
    fun `ozet baslik satirindan sonraki ilk dolu satirdir`() {
        assertEquals("ikinci satir", NoteText.snippet("# Baslik\n\nikinci satir\nucuncu"))
        assertEquals("", NoteText.snippet("# Yalniz baslik"))
    }

    @Test
    fun `uzun baslik kisaltilir`() {
        val long = "a".repeat(200)
        val title = NoteText.title(long)
        assertTrue(title.length <= 81)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `not son duzenlenme damgasini tasir`() {
        val note = Note("1", "metin", createdAt = 100, updatedAt = 250)
        assertEquals(250L, note.updatedAt)
        assertEquals("metin", note.title)
    }
}

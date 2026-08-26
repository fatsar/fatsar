package com.fatsar.notlar.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownEditingTest {

    @Test
    fun `secim kalin yapilir ve geri alinir`() {
        val bold = MarkdownEditing.toggleWrap("merhaba dunya", 0, 7, "**")
        assertEquals("**merhaba** dunya", bold.text)
        assertEquals(2, bold.selectionStart)
        assertEquals(9, bold.selectionEnd)

        val plain = MarkdownEditing.toggleWrap(bold.text, bold.selectionStart, bold.selectionEnd, "**")
        assertEquals("merhaba dunya", plain.text)
        assertEquals(0, plain.selectionStart)
        assertEquals(7, plain.selectionEnd)
    }

    @Test
    fun `secim yoksa imlecin uzerindeki sozcuge uygulanir`() {
        val edit = MarkdownEditing.toggleWrap("bir iki uc", 5, 5, "*")
        assertEquals("bir *iki* uc", edit.text)
    }

    @Test
    fun `satir oneki eklenir ve kaldirilir`() {
        val added = MarkdownEditing.togglePrefix("satir", 0, 0, "- ")
        assertEquals("- satir", added.text)

        val removed = MarkdownEditing.togglePrefix(added.text, 2, 2, "- ")
        assertEquals("satir", removed.text)
    }

    @Test
    fun `enter madde isaretini surdurur`() {
        val text = "- elma\n"
        val edit = MarkdownEditing.afterNewline(text, text.length)!!
        assertEquals("- elma\n- ", edit.text)
        assertEquals(9, edit.selectionStart)
    }

    @Test
    fun `enter numarayi bir artirir`() {
        val text = "1. bir\n"
        val edit = MarkdownEditing.afterNewline(text, text.length)!!
        assertEquals("1. bir\n2. ", edit.text)
    }

    @Test
    fun `isaretli gorevden sonra bos kutu acilir`() {
        val text = "- [x] is\n"
        val edit = MarkdownEditing.afterNewline(text, text.length)!!
        assertEquals("- [x] is\n- [ ] ", edit.text)
    }

    @Test
    fun `bos maddede enter listeyi bitirir`() {
        val text = "- elma\n- \n"
        val edit = MarkdownEditing.afterNewline(text, text.length)!!
        assertEquals("- elma\n", edit.text)
        assertEquals(7, edit.selectionStart)
    }

    @Test
    fun `liste disinda enter mudahale etmez`() {
        assertNull(MarkdownEditing.afterNewline("duz metin\n", 10))
        assertNull(MarkdownEditing.afterNewline("duz metin", 4))
    }

    @Test
    fun `gorev kutusu eklenir ve isaretlenir`() {
        val added = MarkdownEditing.toggleTask("sut al", 0)!!
        assertEquals("- [ ] sut al", added.text)

        val checked = MarkdownEditing.toggleTask(added.text, 0)!!
        assertEquals("- [x] sut al", checked.text)

        val unchecked = MarkdownEditing.toggleTask(checked.text, 0)!!
        assertEquals("- [ ] sut al", unchecked.text)
    }
}

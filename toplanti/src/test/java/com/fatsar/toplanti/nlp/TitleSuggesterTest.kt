package com.fatsar.toplanti.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSuggesterTest {

    @Test
    fun `tekrarlanan konu basliga donusur`() {
        val text = "mobil uygulama tasarımını konuştuk sonra mobil uygulama bütçesini inceledik " +
            "ve mobil uygulama lansman planını çıkardık"
        val title = TitleSuggester.suggest(text)
        assertEquals("Mobil Uygulama Toplantısı", title)
    }

    @Test
    fun `bos metin bos baslik doner`() {
        assertEquals("", TitleSuggester.suggest(""))
    }

    @Test
    fun `durak sozcukleri baslik olmaz`() {
        val title = TitleSuggester.suggest("yani şey ama çünkü belki bütçe planlaması bütçe planlaması")
        assertTrue(title.contains("Bütçe") || title.contains("Planlaması"))
        assertTrue(title.endsWith("Toplantısı"))
    }
}

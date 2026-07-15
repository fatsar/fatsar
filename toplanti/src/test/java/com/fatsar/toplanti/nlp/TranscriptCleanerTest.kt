package com.fatsar.toplanti.nlp

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptCleanerTest {

    @Test
    fun `dolgu sozcukleri kaldirilir`() {
        assertEquals(
            "Bugün toplantıya başlayalım",
            TranscriptCleaner.clean("ıı bugün eee toplantıya şey başlayalım")
        )
    }

    @Test
    fun `bir sey gibi anlamli kullanim korunur`() {
        assertEquals(
            "Sana bir şey söyleyeceğim",
            TranscriptCleaner.clean("sana bir şey söyleyeceğim")
        )
    }

    @Test
    fun `ardisik tekrarlar tekile iner`() {
        assertEquals(
            "Ben bunu yarın gönderirim",
            TranscriptCleaner.clean("ben ben bunu yarın yarın gönderirim")
        )
    }

    @Test
    fun `ikili tekrar tekile iner`() {
        assertEquals(
            "Çok güzel oldu",
            TranscriptCleaner.clean("çok güzel çok güzel oldu")
        )
    }

    @Test
    fun `cumle basindaki yani kaldirilir ama icerdeki korunur`() {
        assertEquals(
            "Proje bitti yani teslim edildi",
            TranscriptCleaner.clean("yani proje bitti yani teslim edildi")
        )
    }

    @Test
    fun `olumsuzluk ve sayilar degismez`() {
        assertEquals(
            "Bunu yapmayacağız 15 gün sürer",
            TranscriptCleaner.clean("bunu yapmayacağız 15 gün sürer")
        )
    }

    @Test
    fun `bos metin bos doner`() {
        assertEquals("", TranscriptCleaner.clean("ıı eee hmm"))
    }
}

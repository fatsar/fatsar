package com.fatsar.notlar.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `basliklar seviyesiyle ayristirilir`() {
        val blocks = MarkdownParser.parse("# Bir\n\n### Uc")
        assertEquals(2, blocks.size)
        val first = blocks[0] as MdBlock.Heading
        val second = blocks[1] as MdBlock.Heading
        assertEquals(1, first.level)
        assertEquals("Bir", first.text.text)
        assertEquals(3, second.level)
        assertEquals("Uc", second.text.text)
    }

    @Test
    fun `madde ve numarali listeler ayristirilir`() {
        val blocks = MarkdownParser.parse("- elma\n- armut\n1. bir\n2) iki")
        assertEquals(4, blocks.size)
        assertEquals("elma", (blocks[0] as MdBlock.Bullet).text.text)
        assertEquals("armut", (blocks[1] as MdBlock.Bullet).text.text)
        assertEquals(1, (blocks[2] as MdBlock.Numbered).number)
        assertEquals(2, (blocks[3] as MdBlock.Numbered).number)
    }

    @Test
    fun `gorev kutulari isaretli durumuyla okunur`() {
        val blocks = MarkdownParser.parse("- [x] bitti\n- [ ] duruyor")
        val done = blocks[0] as MdBlock.Task
        val open = blocks[1] as MdBlock.Task
        assertTrue(done.done)
        assertEquals("bitti", done.text.text)
        assertTrue(!open.done)
        assertEquals("duruyor", open.text.text)
    }

    @Test
    fun `ic ice listelerde girinti seviyesi hesaplanir`() {
        val blocks = MarkdownParser.parse("- ust\n  - alt\n    - daha alt")
        assertEquals(0, (blocks[0] as MdBlock.Bullet).indent)
        assertEquals(1, (blocks[1] as MdBlock.Bullet).indent)
        assertEquals(2, (blocks[2] as MdBlock.Bullet).indent)
    }

    @Test
    fun `kod blogu citler arasinda oldugu gibi korunur`() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n\n// yorum\n```\nsonra")
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\n\n// yorum", code.code)
        assertEquals("sonra", (blocks[1] as MdBlock.Paragraph).text.text)
    }

    @Test
    fun `alinti satirlari tek blokta birlesir`() {
        val blocks = MarkdownParser.parse("> bir\n> iki")
        assertEquals(1, blocks.size)
        assertEquals("bir\niki", (blocks[0] as MdBlock.Quote).text.text)
    }

    @Test
    fun `yatay cizgi taninir`() {
        assertEquals(listOf(MdBlock.Rule), MarkdownParser.parse("---"))
        assertEquals(listOf(MdBlock.Rule), MarkdownParser.parse("***"))
    }

    @Test
    fun `paragraf satirlari birlestirilir bos satir yeni paragraf acar`() {
        val blocks = MarkdownParser.parse("bir\niki\n\nuc")
        assertEquals(2, blocks.size)
        assertEquals("bir\niki", (blocks[0] as MdBlock.Paragraph).text.text)
        assertEquals("uc", (blocks[1] as MdBlock.Paragraph).text.text)
    }

    @Test
    fun `satir ici bicimler isaretleri metinden cikarir`() {
        val text = InlineParser.parse("**kalin** ve *egik* ve `kod`")
        assertEquals("kalin ve egik ve kod", text.text)
        assertEquals(listOf(MdStyle.BOLD, MdStyle.ITALIC, MdStyle.CODE), text.spans.map { it.style })
        val bold = text.spans.first { it.style == MdStyle.BOLD }
        assertEquals(0, bold.start)
        assertEquals(5, bold.end)
    }

    @Test
    fun `ustu cizili ve vurgu isaretleri`() {
        val strike = InlineParser.parse("~~yanlis~~")
        assertEquals("yanlis", strike.text)
        assertEquals(MdStyle.STRIKE, strike.spans.single().style)

        val highlight = InlineParser.parse("==onemli==")
        assertEquals("onemli", highlight.text)
        assertEquals(MdStyle.HIGHLIGHT, highlight.spans.single().style)
    }

    @Test
    fun `baglanti metni ve adresi ayrilir`() {
        val text = InlineParser.parse("[site](https://example.com) sonu")
        assertEquals("site sonu", text.text)
        val link = text.spans.single { it.style == MdStyle.LINK }
        assertEquals("https://example.com", link.href)
        assertEquals(0, link.start)
        assertEquals(4, link.end)
    }

    @Test
    fun `kacisli isaretler duz metin olarak kalir`() {
        val text = InlineParser.parse("\\*yildiz\\* ve \\`ters\\`")
        assertEquals("*yildiz* ve `ters`", text.text)
        assertTrue(text.spans.isEmpty())
    }

    @Test
    fun `kapanmayan isaret metni bozmaz`() {
        val text = InlineParser.parse("2 * 3 * 4 = 24 ama **acik")
        assertTrue(text.text.contains("**acik"))
    }
}

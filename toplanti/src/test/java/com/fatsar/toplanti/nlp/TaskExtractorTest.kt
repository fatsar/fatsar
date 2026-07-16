package com.fatsar.toplanti.nlp

import com.fatsar.toplanti.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskExtractorTest {

    private val base: Long = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 15, 10, 0, 0) // Çarşamba
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun sentence(text: String) = TaskExtractor.Sentence(text, listOf("seg1"))

    @Test
    fun `prd ornegi - sahip ve termin cikarilir`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("ayşe cuma gününe kadar teklif taslağını gönderecek")), base
        )
        assertEquals(1, tasks.size)
        val t = tasks[0]
        assertEquals("Ayşe", t.ownerText)
        assertEquals("cuma gününe kadar", t.dueTextOriginal)
        assertTrue("Termin gelecekte olmalı", t.dueAtMillis > base)
        // Cuma, baz tarihten (çarşamba) 2 gün sonra
        val cal = Calendar.getInstance().apply { timeInMillis = t.dueAtMillis }
        assertEquals(Calendar.FRIDAY, cal.get(Calendar.DAY_OF_WEEK))
        // Onay kullanıcıya bırakılır (FR-032)
        assertEquals(TaskStatus.NEEDS_REVIEW, t.status)
    }

    @Test
    fun `sahip yoksa uydurulmaz`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("raporu yarına kadar tamamlamalıyız")), base
        )
        assertEquals(1, tasks.size)
        assertEquals("Biz değil boş olmalı; cümlede ad yok", "", tasks[0].ownerText)
        assertEquals("yarına", tasks[0].dueTextOriginal)
    }

    @Test
    fun `eylem icermeyen cumleden gorev cikmaz`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("bugün hava çok güzeldi ve toplantı verimliydi")), base
        )
        assertEquals(0, tasks.size)
    }

    @Test
    fun `gelecek hafta zaman ifadesi termin olur`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("mehmet sunumu gelecek hafta hazırlayacak")), base
        )
        assertEquals(1, tasks.size)
        assertEquals("Mehmet", tasks[0].ownerText)
        assertEquals("gelecek hafta", tasks[0].dueTextOriginal)
        assertEquals(base + 7L * 24 * 3600 * 1000, dayOf(tasks[0].dueAtMillis, base))
    }

    @Test
    fun `tarih formati taninir`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("fatura 15.08 tarihine kadar kesilecek")), base
        )
        assertEquals(1, tasks.size)
        assertEquals("15.08", tasks[0].dueTextOriginal)
        val cal = Calendar.getInstance().apply { timeInMillis = tasks[0].dueAtMillis }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
    }

    @Test
    fun `ingilizce gorev - sahip ve termin cikarilir`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("john will send the proposal by friday")), base, "en"
        )
        assertEquals(1, tasks.size)
        assertEquals("John", tasks[0].ownerText)
        assertEquals("by friday", tasks[0].dueTextOriginal)
        val cal = Calendar.getInstance().apply { timeInMillis = tasks[0].dueAtMillis }
        assertEquals(Calendar.FRIDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(TaskStatus.NEEDS_REVIEW, tasks[0].status)
    }

    @Test
    fun `ingilizce eylemsiz cumleden gorev cikmaz`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("the weather was nice and the meeting went smoothly")), base, "en"
        )
        assertEquals(0, tasks.size)
    }

    @Test
    fun `ingilizce needs to kalibi taninir`() {
        val tasks = TaskExtractor.extract(
            listOf(sentence("sarah needs to update the roadmap tomorrow")), base, "en"
        )
        assertEquals(1, tasks.size)
        assertEquals("Sarah", tasks[0].ownerText)
        assertEquals("tomorrow", tasks[0].dueTextOriginal)
    }

    /** Gün kıyaslaması: saat farklarını yok sayarak gün bazında karşılaştır. */
    private fun dayOf(actual: Long, baseMillis: Long): Long {
        val a = Calendar.getInstance().apply { timeInMillis = actual }
        val b = Calendar.getInstance().apply { timeInMillis = baseMillis }
        b.set(Calendar.DAY_OF_YEAR, a.get(Calendar.DAY_OF_YEAR))
        b.set(Calendar.YEAR, a.get(Calendar.YEAR))
        return b.timeInMillis
    }
}

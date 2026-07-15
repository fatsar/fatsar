package com.fatsar.toplanti.nlp

/**
 * Toplantı içeriğinden başlık önerir (FR-023). Öneri, kullanıcı onaylamadan
 * nihai başlık olarak kaydedilmez (AC-014).
 */
object TitleSuggester {

    fun suggest(fullText: String): String {
        val tokens = TurkishText.tokenize(fullText)
            .filter { TurkishText.isContentWord(it) }
            .map { TurkishText.lowercaseTr(it) }
        if (tokens.isEmpty()) return ""

        // En sık geçen ikili (bigram) yeterince tekrarlıyorsa onu kullan
        val bigrams = HashMap<String, Int>()
        for (i in 0 until tokens.size - 1) {
            val bg = tokens[i] + " " + tokens[i + 1]
            bigrams[bg] = (bigrams[bg] ?: 0) + 1
        }
        val topBigram = bigrams.entries.maxByOrNull { it.value }
        if (topBigram != null && topBigram.value >= 3) {
            return titleCase(topBigram.key) + " Toplantısı"
        }

        val tf = HashMap<String, Int>()
        tokens.forEach { tf[it] = (tf[it] ?: 0) + 1 }
        val top = tf.entries.sortedByDescending { it.value }.take(2).map { it.key }
        return when (top.size) {
            0 -> ""
            1 -> titleCase(top[0]) + " Toplantısı"
            else -> titleCase(top[0] + " " + top[1]) + " Toplantısı"
        }
    }

    private fun titleCase(s: String): String =
        s.split(" ").joinToString(" ") { TurkishText.capitalizeTr(it) }
}

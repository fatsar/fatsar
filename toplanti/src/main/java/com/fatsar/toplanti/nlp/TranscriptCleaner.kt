package com.fatsar.toplanti.nlp

/**
 * Ham transkriptten dolgu sözcüklerini ve açık tekrarları azaltan temiz
 * transkript üretir (FR-012). Anlamı değiştirmemek için yalnızca bilinen
 * dolgu sözcükleri kaldırılır; olumsuzluk, sayı ve özel adlara dokunulmaz
 * (FR-013). Ham metin her zaman ayrıca korunur.
 */
object TranscriptCleaner {

    // Tek başına anlam taşımayan seslenmeler/dolgular
    private val ALWAYS_FILLERS = setOf(
        "ıı", "ııı", "ıh", "ıhh", "ee", "eee", "eem", "aa", "aaa", "hm", "hmm", "hımm",
        "hı", "hıı", "hıhı", "mm", "mmm", "şey", "yaa", "eh"
    )

    // Yalnızca cümle başında dolgu sayılanlar ("yani sonuç olarak..." → korunur denemez;
    // baştaki kullanım tipik dolgudur, cümle içindeki bağlaç kullanımına dokunulmaz)
    private val LEADING_FILLERS = setOf("yani", "işte", "hani", "valla", "ya")

    // "şey"in anlamlı olduğu bağlamlar: önündeki belirleyiciler
    private val SEY_KEEPERS = setOf("bir", "her", "hiçbir", "hiç", "o", "bu", "şu", "çok", "aynı", "başka")

    fun clean(raw: String): String {
        val tokens = TurkishText.tokenize(raw)
        if (tokens.isEmpty()) return raw.trim()

        val kept = mutableListOf<String>()
        for (tok in tokens) {
            val lower = TurkishText.lowercaseTr(tok)
            val prevLower = kept.lastOrNull()?.let { TurkishText.lowercaseTr(it) }

            if (lower == "şey") {
                // "bir şey", "her şey"... anlamlıdır; yalın "şey" dolgudur
                if (prevLower in SEY_KEEPERS) kept.add(tok)
                continue
            }
            if (lower in ALWAYS_FILLERS) continue
            if (kept.isEmpty() && lower in LEADING_FILLERS) continue

            // Ardışık aynı sözcüğü tekile indir ("ben ben" → "ben")
            if (prevLower == lower) continue

            kept.add(tok)
            // İkili tekrar kontrolü: son dört sözcük "x y x y" ise son ikisini at ("çok güzel çok güzel" → "çok güzel")
            if (kept.size >= 4) {
                val n = kept.size
                if (TurkishText.lowercaseTr(kept[n - 4]) == TurkishText.lowercaseTr(kept[n - 2]) &&
                    TurkishText.lowercaseTr(kept[n - 3]) == TurkishText.lowercaseTr(kept[n - 1])
                ) {
                    kept.removeAt(n - 1)
                    kept.removeAt(n - 2)
                }
            }
        }
        if (kept.isEmpty()) return ""
        return TurkishText.capitalizeTr(kept.joinToString(" "))
    }
}

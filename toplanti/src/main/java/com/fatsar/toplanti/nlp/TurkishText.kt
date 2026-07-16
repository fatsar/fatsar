package com.fatsar.toplanti.nlp

/** Türkçe/İngilizce metin yardımcıları. Saf Kotlin; birim testlerinde kullanılır. */
object TurkishText {

    val STOPWORDS = setOf(
        "acaba", "ama", "ancak", "artık", "aslında", "az", "bana", "bazı", "belki", "ben", "beni",
        "benim", "beri", "bile", "bir", "biraz", "birçok", "biri", "birkaç", "birşey", "biz", "bize",
        "bizim", "böyle", "böylece", "bu", "buna", "bunda", "bundan", "bunlar", "bunu", "bunun",
        "burada", "bütün", "çok", "çünkü", "da", "daha", "de", "değil", "demek", "diğer", "diye",
        "dolayı", "en", "eğer", "evet", "fakat", "falan", "filan", "gibi", "hem", "hep", "hepsi",
        "her", "herkes", "hiç", "için", "içinde", "ile", "ilgili", "ise", "işte", "kadar", "karşı",
        "kendi", "kez", "ki", "kim", "mi", "mu", "mü", "mı", "nasıl", "ne", "neden", "nerede",
        "niye", "o", "olan", "olarak", "oldu", "olduğu", "olur", "ona", "ondan", "onlar", "onu",
        "onun", "orada", "oysa", "öyle", "pek", "sadece", "sanki", "sen", "senin", "siz", "size",
        "sizin", "son", "sonra", "şey", "şimdi", "şu", "şuna", "şunu", "tabi", "tabii", "tamam",
        "tüm", "var", "ve", "veya", "ya", "yani", "yok", "zaten", "zaman"
    )

    /** Türkçe kurallarına uygun küçük harfe çevirme (I→ı, İ→i). */
    fun lowercaseTr(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    'I' -> 'ı'
                    'İ' -> 'i'
                    else -> c.lowercaseChar()
                }
            )
        }
    }

    /** Türkçe kurallarına uygun ilk harfi büyütme (i→İ, ı→I). */
    fun capitalizeTr(s: String): String {
        if (s.isEmpty()) return s
        val first = when (s[0]) {
            'i' -> 'İ'
            'ı' -> 'I'
            else -> s[0].uppercaseChar()
        }
        return first + s.substring(1)
    }

    /** Harf ve rakam dışı karakterlerden bölerek sözcüklere ayırır. */
    fun tokenize(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }

    val EN_STOPWORDS = setOf(
        "about", "after", "again", "also", "always", "anything", "anyway", "because", "been",
        "before", "being", "cannot", "could", "does", "doing", "dont", "each", "either", "else",
        "even", "ever", "every", "everything", "from", "getting", "goes", "going", "gonna", "gotta",
        "have", "having", "here", "just", "kind", "kinda", "know", "like", "likes", "little",
        "look", "looking", "make", "makes", "many", "maybe", "mean", "more", "most", "much",
        "need", "never", "okay", "only", "other", "ourselves", "over", "pretty", "quite", "really",
        "right", "said", "same", "says", "should", "some", "something", "sort", "still", "stuff",
        "sure", "take", "than", "that", "thats", "their", "them", "then", "there", "these",
        "they", "thing", "things", "think", "this", "those", "though", "thought", "through",
        "very", "want", "wanted", "well", "were", "what", "when", "where", "which", "while",
        "will", "with", "would", "yeah", "your", "youre"
    )

    fun stopwords(language: String): Set<String> =
        if (language == "en") EN_STOPWORDS else STOPWORDS

    /** İçerik sözcüğü: 4+ harfli ve durak sözcüğü olmayan. */
    fun isContentWord(token: String, language: String = "tr"): Boolean {
        val t = lowercaseTr(token)
        return t.length >= 4 && t !in stopwords(language) && !t.all { it.isDigit() }
    }
}

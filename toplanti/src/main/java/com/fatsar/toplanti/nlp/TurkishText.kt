package com.fatsar.toplanti.nlp

/** Türkçe metin yardımcıları. Saf Kotlin; birim testlerinde kullanılır. */
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

    /** İçerik sözcüğü: 4+ harfli ve durak sözcüğü olmayan. */
    fun isContentWord(token: String): Boolean {
        val t = lowercaseTr(token)
        return t.length >= 4 && t !in STOPWORDS && !t.all { it.isDigit() }
    }
}

package com.fatsar.hermes.core.logic

/** Adres düzenleme yardımcıları: kullanıcı ne yazarsa yazsın çalışan bir URL üretir. */
object Urls {

    /**
     * Kullanıcının yazdığı adresi normalleştirir:
     * "1.2.3.4:8713" -> "http://1.2.3.4:8713", sondaki "/" atılır.
     * Boş girdi için boş döner.
     */
    fun normalizeBase(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""
        s = s.replace(" ", "")
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            // Alan adı + 443 dışında port yoksa https, aksi halde http varsay.
            s = if (s.contains(":") || isProbablyIp(s.substringBefore('/'))) "http://$s" else "https://$s"
        }
        while (s.endsWith("/")) s = s.dropLast(1)
        return s
    }

    private fun isProbablyIp(host: String): Boolean =
        host.split(".").let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }

    /** Yerel ağ / localhost adresi mi (temiz metin HTTP burada kabul edilebilir). */
    fun isLocalAddress(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "10.0.2.2") return true
        val parts = host.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return when {
            parts[0] == 10 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            else -> false
        }
    }

    /** Adresin yalnızca sunucu adı kısmı ("http://1.2.3.4:8713/x" -> "1.2.3.4"). */
    fun hostOf(url: String): String? {
        val fromUri = runCatching { java.net.URI(url.trim()).host }.getOrNull()
        if (!fromUri.isNullOrBlank()) return fromUri
        val host = url.trim()
            .substringAfter("://", url.trim())
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
            .trim()
        return host.ifBlank { null }
    }

    /** HTTPS kullanılmıyor ve adres yerel değilse uyarı gösterilecek. */
    fun isInsecurePublic(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) && !isLocalAddress(url)

    fun hermesUrl(base: String, path: String): String = normalizeBase(base) + path

    /** Adres alanında gösterilecek örnek. */
    const val ADDRESS_PLACEHOLDER = "http://sunucu-ip:8713"

    /** Sorgu değerini güvenli biçimde kodlar. */
    fun encodeQuery(value: String): String =
        runCatching { java.net.URLEncoder.encode(value, "UTF-8") }.getOrDefault(value)
}

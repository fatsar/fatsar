package com.fatsar.hermes.core.net

/** Sunucudan gelen tek bir Server-Sent Event. */
data class SseEvent(
    val event: String,
    val data: String,
    val id: String? = null,
) {
    /** OpenAI uyumlu API'lerin akış sonu işareti. */
    val isDone: Boolean get() = data.trim() == "[DONE]"
}

/**
 * Satır satır beslenen, durum tutan SSE ayrıştırıcısı (W3C EventSource biçimi).
 *
 * Kullanım: her ham satır için [feed] çağrılır; olay tamamlandığında (boş satır)
 * [SseEvent] döner. Akış biterken kalan veri için [flush] çağrılır.
 */
class SseParser {
    private val data = StringBuilder()
    private var event: String = ""
    private var id: String? = null
    private var hasData = false

    fun feed(rawLine: String): SseEvent? {
        val line = rawLine.removeSuffix("\r")

        // Boş satır -> biriken olayı gönder.
        if (line.isEmpty()) return dispatch()

        // Yorum satırı (ör. ": keep-alive") yok sayılır.
        if (line.startsWith(":")) return null

        val colon = line.indexOf(':')
        val field: String
        var value: String
        if (colon < 0) {
            field = line
            value = ""
        } else {
            field = line.substring(0, colon)
            value = line.substring(colon + 1)
            if (value.startsWith(" ")) value = value.substring(1)
        }

        when (field) {
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "event" -> event = value
            "id" -> id = value
            "retry" -> Unit
            else -> Unit
        }
        return null
    }

    /** Akış sonunda yarım kalmış olayı döndürür. */
    fun flush(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        if (!hasData) {
            event = ""
            return null
        }
        val ev = SseEvent(event = event.ifEmpty { "message" }, data = data.toString(), id = id)
        data.setLength(0)
        event = ""
        hasData = false
        return ev
    }
}

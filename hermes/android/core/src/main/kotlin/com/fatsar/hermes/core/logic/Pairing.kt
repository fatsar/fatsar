package com.fatsar.hermes.core.logic

import com.fatsar.hermes.core.model.ServerKind
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HermesJson
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PairingPayload(
    @SerialName("u") val url: String = "",
    @SerialName("t") val token: String = "",
    @SerialName("n") val name: String = "",
)

/**
 * Agent'ın açılışta yazdırdığı "HERMES1:..." kodunu çözer.
 * Kullanıcı kodu kopyalayıp uygulamaya yapıştırınca sunucu tek dokunuşla eklenir.
 */
object Pairing {
    const val PREFIX = "HERMES1:"

    fun encode(url: String, token: String, name: String): String {
        val json = HermesJson.encodeToString(PairingPayload.serializer(), PairingPayload(url, token, name))
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
        return PREFIX + b64
    }

    /** Kod geçersizse null döner. Boşluk/satır sonu/"hermes1:" yazımına toleranslıdır. */
    fun decode(raw: String): PairingPayload? {
        val cleaned = raw.trim().replace(Regex("\\s+"), "")
        val idx = cleaned.indexOf(PREFIX, ignoreCase = true)
        if (idx < 0) return null
        var body = cleaned.substring(idx + PREFIX.length)
        if (body.isEmpty()) return null
        body = body.replace('+', '-').replace('/', '_')
        val padded = body + "=".repeat((4 - body.length % 4) % 4)
        val bytes = runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull() ?: return null
        val payload = runCatching {
            HermesJson.decodeFromString(PairingPayload.serializer(), String(bytes, Charsets.UTF_8))
        }.getOrNull() ?: return null
        if (payload.url.isBlank()) return null
        return payload
    }

    /** Koddan doğrudan sunucu profili üretir. */
    fun toProfile(payload: PairingPayload, id: String, now: Long): ServerProfile = ServerProfile(
        id = id,
        name = payload.name.ifBlank { Urls.hostOf(payload.url) ?: "Hermes Agent" },
        kind = ServerKind.HERMES,
        baseUrl = Urls.normalizeBase(payload.url),
        token = payload.token,
        createdAt = now,
    )
}

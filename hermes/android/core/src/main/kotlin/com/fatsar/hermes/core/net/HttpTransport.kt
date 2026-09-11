package com.fatsar.hermes.core.net

import com.fatsar.hermes.core.model.HermesException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * OkHttp üzerine ince bir sarmalayıcı. İstek kurma, hata çevirme ve satır satır
 * akış okuma burada toplanır; böylece arka uçlar (backend) HTTP ayrıntısı bilmez.
 */
class HttpTransport(
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Akış (SSE) yanıtları uzun sürebilir; okuma zaman aşımı yok.
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** Kullanıcıya gösterilebilir, Türkçe ve anlaşılır ağ hatası metni. */
        fun friendlyError(t: Throwable): String = when (t) {
            is HermesException -> t.message ?: "Bilinmeyen hata"
            is java.net.UnknownHostException -> "Sunucu adresi çözümlenemedi. Adresi ve internet bağlantısını kontrol edin."
            is java.net.ConnectException -> "Sunucuya bağlanılamadı. Adres/port doğru mu, agent çalışıyor mu?"
            is java.net.SocketTimeoutException -> "Sunucu zamanında yanıt vermedi (zaman aşımı)."
            is javax.net.ssl.SSLException -> "TLS/HTTPS hatası: ${t.message}"
            is IOException -> "Ağ hatası: ${t.message ?: t::class.java.simpleName}"
            else -> t.message ?: t::class.java.simpleName
        }
    }

    private fun build(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> if (v.isNotEmpty()) builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (body == null) builder.delete() else builder.delete(body.toRequestBody(JSON_MEDIA))
            "PUT" -> builder.put((body ?: "").toRequestBody(JSON_MEDIA))
            else -> builder.post((body ?: "").toRequestBody(JSON_MEDIA))
        }
        return builder.build()
    }

    /** Gövdeyi tek seferde okur; 2xx dışındaki yanıtlarda [HermesException] fırlatır. */
    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(build(url, method, headers, body)).execute()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw HermesException(friendlyError(e), cause = e)
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw HermesException(describeHttpError(res.code, text), statusCode = res.code)
            }
            text
        }
    }

    /**
     * Yanıt gövdesini satır satır akıtır. Akış, toplayıcı iptal edildiğinde
     * çağrıyı da iptal eder (bot "Durdur" düğmesi bunu kullanır).
     */
    fun streamLines(
        url: String,
        method: String = "POST",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): Flow<String> = callbackFlow {
        val call: Call = client.newCall(build(url, method, headers, body))
        var failure: Throwable? = null
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                response.close()
                throw HermesException(describeHttpError(response.code, text), statusCode = response.code)
            }
            val source = response.body?.source()
                ?: throw HermesException("Sunucu boş yanıt döndürdü.")
            response.use {
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    trySend(line)
                }
            }
        } catch (e: CancellationException) {
            // Akış bilinçli durduruldu.
        } catch (e: HermesException) {
            failure = e
        } catch (e: Exception) {
            failure = HermesException(friendlyError(e), cause = e)
        }
        val err = failure
        if (err != null) close(err) else close()
        awaitClose { if (!call.isCanceled()) call.cancel() }
    }.flowOn(Dispatchers.IO)

    /** SSE akışını ayrıştırılmış olaylara çevirir. */
    fun streamSse(
        url: String,
        method: String = "POST",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): Flow<SseEvent> = flow {
        val parser = SseParser()
        streamLines(url, method, headers + mapOf("Accept" to "text/event-stream"), body).collect { line ->
            parser.feed(line)?.let { emit(it) }
        }
        parser.flush()?.let { emit(it) }
    }
}

internal fun describeHttpError(code: Int, body: String): String {
    val detail = extractErrorMessage(body)
    val base = when (code) {
        401 -> "Yetkisiz (401): erişim anahtarı/token hatalı."
        403 -> "Erişim reddedildi (403): token bu işlem için yetkili değil."
        404 -> "Bulunamadı (404): adres ya da bot kimliği hatalı."
        429 -> "Çok fazla istek (429): bir süre bekleyip tekrar deneyin."
        500, 502, 503, 504 -> "Sunucu hatası ($code)."
        else -> "HTTP $code"
    }
    return if (detail.isNullOrBlank()) base else "$base $detail"
}

/** Hem Hermes hem OpenAI/Ollama hata gövdelerinden okunabilir mesaj çıkarır. */
internal fun extractErrorMessage(body: String): String? {
    if (body.isBlank()) return null
    return runCatching {
        val root = HermesJson.parseToJsonElement(body)
        val obj = (root as? kotlinx.serialization.json.JsonObject) ?: return@runCatching null
        val error = obj["error"]
        val fromError = when (error) {
            is kotlinx.serialization.json.JsonObject ->
                (error["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            is kotlinx.serialization.json.JsonPrimitive -> error.content
            else -> null
        }
        fromError
            ?: (obj["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: (obj["detail"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull() ?: body.take(200).replace('\n', ' ')
}

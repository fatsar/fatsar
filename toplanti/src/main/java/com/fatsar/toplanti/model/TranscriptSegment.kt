package com.fatsar.toplanti.model

import java.util.UUID

/**
 * Zaman damgalı transkript parçası. [rawText] değiştirilemez kaynak sürümdür;
 * [cleanText] otomatik temizlenmiş, [userText] kullanıcı düzeltmesidir (FR-011).
 */
data class TranscriptSegment(
    val id: String = UUID.randomUUID().toString(),
    var speakerId: String = "",
    val startMs: Long,
    val endMs: Long,
    val rawText: String,
    var cleanText: String = "",
    var userText: String? = null,
    val confidence: Double = 1.0,
    var revision: Int = 0
) {
    fun displayText(clean: Boolean): String =
        userText ?: if (clean && cleanText.isNotBlank()) cleanText else rawText

    val isLowConfidence: Boolean get() = confidence < 0.6
}

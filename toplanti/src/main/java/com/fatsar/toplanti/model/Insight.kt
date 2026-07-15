package com.fatsar.toplanti.model

import java.util.UUID

enum class InsightType { IMPORTANT_NOTE, DECISION, QUESTION, RISK }

/** AI tarafından çıkarılan, kaynak segmentlere geri izlenebilir madde (FR-021, FR-022). */
data class Insight(
    val id: String = UUID.randomUUID().toString(),
    val type: InsightType,
    var content: String,
    val confidence: Double = 0.0,
    val sourceSegmentIds: List<String> = emptyList()
)

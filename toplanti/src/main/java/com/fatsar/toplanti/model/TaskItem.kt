package com.fatsar.toplanti.model

import java.util.UUID

enum class TaskStatus { NEEDS_REVIEW, CONFIRMED, DONE }

/**
 * Toplantıdan çıkarılan veya elle eklenen görev (FR-030..FR-033).
 * Sahip/termin belirsizse [status] NEEDS_REVIEW olur; bilgi uydurulmaz (FR-032).
 */
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var description: String = "",
    var ownerText: String = "",
    var dueTextOriginal: String = "",
    var dueAtMillis: Long = 0L,
    var status: TaskStatus = TaskStatus.NEEDS_REVIEW,
    val confidence: Double = 0.0,
    val sourceSegmentIds: List<String> = emptyList()
) {
    val needsReview: Boolean get() = status == TaskStatus.NEEDS_REVIEW
}

package com.fatsar.toplanti.model

import java.util.UUID

enum class MeetingMode { LIVE, IMPORTED }

/** Toplantının yaşam döngüsü durumu. */
enum class MeetingStatus { RECORDING, PROCESSING, REVIEW, DONE, FAILED }

data class Meeting(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var suggestedTitle: String = "",
    var titleConfirmed: Boolean = false,
    var meetingDate: Long = System.currentTimeMillis(),
    var startedAt: Long = 0L,
    var endedAt: Long = 0L,
    var durationMs: Long = 0L,
    var mode: MeetingMode = MeetingMode.LIVE,
    var language: String = "tr",
    var status: MeetingStatus = MeetingStatus.RECORDING,
    var audioFileName: String = "",
    var errorMessage: String = "",
    val tags: MutableList<String> = mutableListOf(),
    val speakers: MutableList<Speaker> = mutableListOf(),
    val insights: MutableList<Insight> = mutableListOf(),
    val tasks: MutableList<TaskItem> = mutableListOf(),
    val attachments: MutableList<Attachment> = mutableListOf(),
    var shortSummary: String = "",
    var detailedSummary: String = "",
    var summaryConfidence: Double = 0.0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    /** Kullanıcıya gösterilecek başlık: onaylı başlık > öneri > tarihli varsayılan. */
    fun displayTitle(fallback: String): String = when {
        title.isNotBlank() -> title
        suggestedTitle.isNotBlank() -> suggestedTitle
        else -> fallback
    }
}

data class Speaker(
    val id: String = UUID.randomUUID().toString(),
    var label: String,
    var displayName: String = "",
    var confirmedByUser: Boolean = false
) {
    fun shownName(): String = displayName.ifBlank { label }
}

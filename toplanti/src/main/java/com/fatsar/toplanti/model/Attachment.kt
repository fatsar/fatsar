package com.fatsar.toplanti.model

import java.util.UUID

enum class AttachmentType { IMAGE, VIDEO }

/** Toplantıya bağlı görsel/video eki; toplantı içi zaman damgasıyla saklanır (FR-041). */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long = 0L,
    val capturedAt: Long = System.currentTimeMillis(),
    val meetingOffsetMs: Long = -1L,
    var caption: String = ""
)

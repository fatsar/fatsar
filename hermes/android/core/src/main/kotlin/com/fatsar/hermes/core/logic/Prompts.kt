package com.fatsar.hermes.core.logic

import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.Role

/** Modele gönderilecek tek bir mesaj (rol + içerik). */
data class WireMessage(val role: String, val content: String)

object Prompts {

    /**
     * Geçmişten yalnızca modele gönderilecek kısmı seçer:
     * son [BotSpec.memoryTurns] tur (kullanıcı+asistan çifti); araç mesajları,
     * hatalı ve hâlâ akan mesajlar hariç.
     */
    fun history(bot: BotSpec, history: List<ChatMessage>): List<WireMessage> {
        val usable = history.filter {
            (it.role == Role.USER || it.role == Role.ASSISTANT) &&
                !it.error &&
                !it.streaming &&
                it.content.isNotBlank()
        }
        val maxMessages = bot.memoryTurns.coerceAtLeast(0) * 2
        if (maxMessages <= 0) return emptyList()
        return usable.takeLast(maxMessages).map { WireMessage(it.role.wire, it.content) }
    }

    /** Sistem istemi + geçmiş + yeni kullanıcı girdisinden tam mesaj listesi. */
    fun build(bot: BotSpec, history: List<ChatMessage>, input: String): List<WireMessage> {
        val out = ArrayList<WireMessage>()
        if (bot.systemPrompt.isNotBlank()) out += WireMessage(Role.SYSTEM.wire, bot.systemPrompt.trim())
        out += history(bot, history)
        if (input.isNotBlank()) out += WireMessage(Role.USER.wire, input.trim())
        return out
    }

    /** Uzun metinleri liste/bildirim için kısaltır. */
    fun preview(text: String, max: Int = 80): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }
}

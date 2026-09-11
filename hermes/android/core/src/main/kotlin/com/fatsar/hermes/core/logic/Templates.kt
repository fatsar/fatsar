package com.fatsar.hermes.core.logic

import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.Schedule
import com.fatsar.hermes.core.model.ScheduleMode

/** Hermes Agent'ın sunduğu araçlar. */
data class ToolInfo(
    val id: String,
    val label: String,
    val description: String,
    val risky: Boolean = false,
)

object Tools {
    const val HTTP_GET = "http_get"
    const val WEB_SEARCH = "web_search"
    const val SHELL = "shell"
    const val READ_FILE = "read_file"
    const val WRITE_FILE = "write_file"
    const val NOW = "now"

    val all: List<ToolInfo> = listOf(
        ToolInfo(HTTP_GET, "Web sayfası oku", "Bir adresin içeriğini indirip metne çevirir."),
        ToolInfo(WEB_SEARCH, "Web araması", "Arama motorundan sonuç başlıkları getirir (agent ayarına bağlı)."),
        ToolInfo(NOW, "Saat/tarih", "Sunucunun güncel tarih ve saatini verir."),
        ToolInfo(READ_FILE, "Dosya oku", "Agent çalışma klasöründeki bir dosyayı okur."),
        ToolInfo(WRITE_FILE, "Dosya yaz", "Agent çalışma klasörüne dosya yazar.", risky = true),
        ToolInfo(
            SHELL,
            "Kabuk komutu",
            "Sunucuda komut çalıştırır. Agent'ta ayrıca açılmalıdır; dikkatli kullanın.",
            risky = true,
        ),
    )

    fun label(id: String): String = all.firstOrNull { it.id == id }?.label ?: id
}

/** Hazır bot şablonları — "yeni bot" ekranında tek dokunuşla doldurulur. */
data class BotTemplate(
    val key: String,
    val title: String,
    val avatar: String,
    val description: String,
    val systemPrompt: String,
    val suggestedModel: String = "",
    val tools: List<String> = emptyList(),
    val schedule: Schedule = Schedule(),
    val temperature: Double = 0.7,
)

object Templates {

    val all: List<BotTemplate> = listOf(
        BotTemplate(
            key = "sohbet",
            title = "Sohbet Botu",
            avatar = "🤖",
            description = "Esprili, doğrudan konuşan genel amaçlı asistan (Grok tarzı).",
            systemPrompt = """
                Sen Hermes adında, Türkçe konuşan, esprili ama işini bilen bir asistansın.
                Kısa ve doğrudan cevap ver, gereksiz uyarı ve tekrar yapma.
                Emin olmadığın şeyde emin olmadığını açıkça söyle.
            """.trimIndent(),
            temperature = 0.8,
        ),
        BotTemplate(
            key = "kod",
            title = "Kod Yardımcısı",
            avatar = "💻",
            description = "Kod yazar, hata ayıklar, komut satırı yardımı verir.",
            systemPrompt = """
                Sen kıdemli bir yazılım geliştiricisisin. Türkçe açıklama, İngilizce kod yaz.
                Önce en kısa çalışan çözümü ver, sonra gerekiyorsa alternatifi anlat.
                Kod bloklarını her zaman dil etiketiyle ver.
            """.trimIndent(),
            temperature = 0.3,
        ),
        BotTemplate(
            key = "bekci",
            title = "Sunucu Bekçisi",
            avatar = "🛡️",
            description = "VPS'i düzenli kontrol eder, sorun görürse bildirim gönderir.",
            systemPrompt = """
                Sen bir sistem yöneticisi asistanısın. Sana verilen araçlarla sunucunun
                disk, bellek, yük ve servis durumunu kontrol et.
                Sonucu en fazla 5 satırda özetle; sorun yoksa "Sorun yok" diye başla,
                sorun varsa satır başına bir bulgu yaz ve önerilen komutu ekle.
            """.trimIndent(),
            tools = listOf(Tools.SHELL, Tools.NOW),
            schedule = Schedule(
                mode = ScheduleMode.INTERVAL,
                everyMinutes = 60,
                prompt = "Sunucunun disk, bellek, yük ve çalışan servis durumunu kontrol et ve özetle.",
                notify = true,
            ),
            temperature = 0.2,
        ),
        BotTemplate(
            key = "haber",
            title = "Haber Özetleyici",
            avatar = "📰",
            description = "Verdiğiniz kaynakları okuyup her sabah özet çıkarır.",
            systemPrompt = """
                Sen bir haber editörüsün. Sana verilen adresleri oku ve en önemli
                5 başlığı madde madde, her biri tek cümleyle Türkçe özetle.
                Sonunda "Öne çıkan:" diye tek cümlelik bir değerlendirme ekle.
            """.trimIndent(),
            tools = listOf(Tools.HTTP_GET, Tools.NOW),
            schedule = Schedule(
                mode = ScheduleMode.DAILY,
                atHour = 8,
                atMinute = 30,
                prompt = "Bugünün önemli gelişmelerini kaynaklardan oku ve özetle.",
                notify = true,
            ),
            temperature = 0.4,
        ),
        BotTemplate(
            key = "cevirmen",
            title = "Çevirmen",
            avatar = "🌍",
            description = "Gönderdiğiniz metni akıcı biçimde çevirir.",
            systemPrompt = """
                Sen profesyonel bir çevirmensin. Gelen metin Türkçeyse İngilizceye,
                değilse Türkçeye çevir. Sadece çeviriyi yaz, açıklama ekleme.
                Anlamı koru, birebir değil akıcı çevir.
            """.trimIndent(),
            temperature = 0.3,
        ),
        BotTemplate(
            key = "yazar",
            title = "İçerik Yazarı",
            avatar = "✍️",
            description = "Sosyal medya ve blog metinleri üretir.",
            systemPrompt = """
                Sen bir içerik yazarısın. İstenen konuda akıcı, sade ve özgün Türkçe metin yaz.
                Klişe kalıplardan kaçın. İstenmedikçe 200 kelimeyi geçme.
            """.trimIndent(),
            temperature = 0.9,
        ),
        BotTemplate(
            key = "bos",
            title = "Boş Bot",
            avatar = "⚙️",
            description = "Her şeyi kendiniz ayarlayın.",
            systemPrompt = "",
        ),
    )

    fun byKey(key: String): BotTemplate? = all.firstOrNull { it.key == key }

    /** Şablondan çalıştırılabilir bot tanımı üretir. */
    fun toBot(
        template: BotTemplate,
        id: String,
        serverId: String,
        model: String,
        now: Long,
        name: String = template.title,
    ): BotSpec = BotSpec(
        id = id,
        name = name,
        avatar = template.avatar,
        serverId = serverId,
        model = model.ifBlank { template.suggestedModel },
        systemPrompt = template.systemPrompt,
        temperature = template.temperature,
        tools = template.tools,
        schedule = template.schedule,
        createdAt = now,
        updatedAt = now,
    )
}

# Hermes Agent Protokolü (v1)

Android uygulaması ile `hermes_agent.py` arasındaki sözleşme. Kendi istemcinizi yazmak
ya da agent'ı başka bir dille yeniden yazmak isterseniz bu belge yeterlidir.

- Taşıma: HTTP/1.1, gövdeler `application/json; charset=utf-8`
- Kimlik doğrulama: `Authorization: Bearer <token>` (akış uçlarında `?token=` de kabul edilir)
- Akış: `text/event-stream` (SSE). Olay adı `event:`, veri `data:` satırındadır.
- Hata gövdesi: `{"ok": false, "error": {"message": "..."}}`, uygun HTTP kodu ile.

---

## Uçlar

| Yöntem | Yol | Açıklama |
|---|---|---|
| GET | `/` | Token gerektirmeyen durum sayfası (HTML) |
| GET | `/v1/health` | Sunucu bilgisi |
| GET | `/v1/backends` | Agent'ta tanımlı LLM sağlayıcıları ve hazır olup olmadıkları |
| GET | `/v1/models?backend=xai` | Seçilen sağlayıcının model listesi |
| GET | `/v1/bots` | Kayıtlı botlar |
| GET/PUT/DELETE | `/v1/bots/{id}` | Bot oku / kaydet / sil |
| POST | `/v1/bots/{id}/runs` | Botu çalıştır (akışlı veya tek yanıt) |
| GET | `/v1/bots/{id}/runs?limit=20` | Çalışma geçmişi |
| GET/DELETE | `/v1/bots/{id}/messages?session=default` | Sunucu tarafı sohbet hafızası |
| GET | `/v1/runs/{run_id}` | Tek çalışmanın ayrıntısı (olaylar dâhil) |
| POST | `/v1/runs/{run_id}/cancel` | Çalışmayı durdur |
| GET | `/v1/runs?limit=20` | Tüm botların son çalışmaları |
| GET | `/v1/events` | Canlı olay akışı (SSE): `log`, `bot.status` |
| GET | `/v1/config` | Ayarlar (token ve anahtarlar maskelenir) |

### GET /v1/health

```json
{
  "ok": true, "name": "hermes-agent", "version": "1.0.0",
  "uptime_s": 3600, "host": "vps-1", "bots": 3,
  "backends": ["anthropic", "echo", "groq", "ollama", "openai", "openrouter", "xai"],
  "backends_ready": ["echo", "ollama", "xai"],
  "default_backend": "xai",
  "tools": ["http_get", "now", "read_file", "web_search", "write_file"],
  "shell_enabled": false, "scheduler": true
}
```

`tools` yalnızca **kullanılabilir** araçları listeler: `shell` kapalıysa listede görünmez.
`backends_ready`, anahtarı/adresi tanımlı olduğu için gerçekten kullanılabilecek sağlayıcılardır.

### GET /v1/backends

Uygulamanın LLM seçimi bu uca dayanır — **anahtarlar asla dönmez**, yalnızca hazır olup olmadığı bilgisi döner.

```json
{
  "default_backend": "xai",
  "backends": [
    {"name": "xai", "ready": true, "is_default": true, "default_model": "grok-3",
     "base_url": "https://api.x.ai/v1", "supports_tools": true, "note": ""},
    {"name": "openai", "ready": false, "is_default": false, "default_model": "gpt-4o-mini",
     "base_url": "https://api.openai.com/v1", "supports_tools": true,
     "note": "API anahtarı ayarlı değil"},
    {"name": "echo", "ready": true, "default_model": "echo",
     "note": "Anahtarsız deneme arka ucu"}
  ]
}
```

Hazırlık kuralı: `echo` her zaman hazır; `ollama` için `base_url` yeterli; diğerleri için
`base_url` **ve** `api_key` gerekir.

### Bot nesnesi

```json
{
  "id": "bot_m3x1a",
  "name": "Sunucu Bekçisi",
  "avatar": "🛡️",
  "backend": "xai",
  "model": "grok-3",
  "system_prompt": "Sen bir sistem yöneticisi asistanısın…",
  "temperature": 0.2,
  "max_tokens": 1024,
  "memory_turns": 12,
  "tools": ["shell", "now"],
  "enabled": true,
  "schedule": {
    "mode": "INTERVAL",
    "every_minutes": 60,
    "at_hour": 9, "at_minute": 0,
    "tz_offset_minutes": 180,
    "prompt": "Disk, bellek ve servis durumunu özetle",
    "notify": true
  },
  "created_at": 1789153549335,
  "updated_at": 1789153549335
}
```

- `backend` boşsa agent'ın `default_backend` ayarı kullanılır. Uygulama yalnızca
  `/v1/backends` içinde `ready: true` dönen sağlayıcıları seçtirir.
- `model` boşsa sağlayıcının `default_model` değeri kullanılır.
- `schedule.mode`: `OFF` | `INTERVAL` | `DAILY`. `prompt` boşken zamanlama çalışmaz.
- `tz_offset_minutes` telefonun saat dilimidir; `DAILY` bunun üzerinden hesaplanır
  (VPS UTC'de olsa bile "her gün 08:30" kullanıcının saatiyle çalışır).

### POST /v1/bots/{id}/runs

```json
{
  "input": "Disk durumu nasıl?",
  "stream": true,
  "session": "default",
  "history": [{"role": "user", "content": "…"}, {"role": "assistant", "content": "…"}],
  "bot": { ...bot nesnesi... },
  "tools": ["shell"]
}
```

- `bot` gönderilirse agent botu **otomatik kaydeder/günceller** — uygulamada yeni
  oluşturulan bir bot ayrıca kayıt isteği gerektirmeden çalışabilir.
- `history` verilirse sunucudaki hafıza yerine o kullanılır; verilmezse agent
  `session` hafızasından son `memory_turns` turu alır.
- `stream: false` → tek seferde `{"run": {...}}` döner.

#### Akış olayları

| `event:` | `data:` | Anlamı |
|---|---|---|
| `run.started` | `{"run_id","bot_id","backend","model","trigger","ts"}` | Çalışma başladı |
| `token` | `{"text":"Mer"}` | Yanıt parçası |
| `tool.call` | `{"name","args","call_id"}` | Model bir araç çağırdı |
| `tool.result` | `{"name","ok","result","call_id"}` | Araç sonucu |
| `message` | `{"role":"assistant","content":"…"}` | Tamamlanmış yanıt |
| `usage` | `{"prompt_tokens","completion_tokens"}` | Token kullanımı |
| `run.finished` | `{"run_id","status","duration_ms"}` | Bitti (`ok`/`error`/`cancelled`/`tool_limit`) |
| `error` | `{"message":"…"}` | Hata |

Örnek akış:

```
event: run.started
data: {"run_id": "run_1a0", "bot_id": "b_demo", "backend": "echo", "model": "echo"}

event: token
data: {"text": "Disk "}

event: run.finished
data: {"run_id": "run_1a0", "status": "ok", "duration_ms": 1500}
```

**İstemci bağlantıyı koparsa çalışma sunucuda sürer ve kaydedilir.** Telefon uykuya
dalarsa sonuç `/v1/bots/{id}/runs` geçmişinden okunabilir.

---

## Araç döngüsü

Agent, OpenAI "function calling" biçimini kullanır. Model araç çağırdığında:

1. `tool.call` olayı gönderilir, araç sunucuda çalıştırılır.
2. Sonuç `tool` rolüyle konuşmaya eklenir ve model yeniden çağrılır (`MAX_TOOL_STEPS = 6`).
3. Araç bot tanımında **açık değilse** çalıştırılmaz; `tool.result` `ok: false` döner.

Araçlar: `now`, `http_get`, `web_search`, `read_file`, `write_file`, `shell`.
Dosya araçları agent çalışma klasörüyle sınırlıdır; `shell` varsayılan olarak kapalıdır
ve `shell_allowlist` ile komut önekine göre kısıtlanabilir.

---

## Eşleştirme kodu

Agent açılışta şu biçimde bir kod yazdırır:

```
HERMES1:<base64url({"u": "http://1.2.3.4:8713", "t": "<token>", "n": "vps-1"})>
```

Uygulama bu kodu (ya da içinde geçtiği herhangi bir metni) çözüp sunucu profilini
tek dokunuşla oluşturur. `hermes://…` biçimindeki bağlantılar da aynı işi görür.

---

## Model sağlayıcılar

`config.json > backends` altında tanımlıdır; her biri `base_url`, `api_key`, `default_model` alır.

| Ad | Uç nokta biçimi | Araç desteği |
|---|---|---|
| `openai`, `xai`, `openrouter`, `groq`, özel | `POST {base}/chat/completions` (OpenAI uyumlu) | ✅ |
| `ollama` | `POST {base}/api/chat` (NDJSON) | ✅ (modele bağlı) |
| `anthropic` | `POST {base}/v1/messages` | ❌ (yalnızca metin) |
| `echo` | — | ❌ (anahtarsız test) |

Uygulama bu sağlayıcılara **doğrudan bağlanmaz**: tüm istekler agent üzerinden geçer,
anahtarlar sunucuda kalır ve zamanlama agent'ın kendi zamanlayıcısında çalışır.
Telefon, zamanlanmış çalışmaların sonuçlarını `/v1/bots/{id}/runs` ve `/v1/runs/{run_id}`
uçlarından okuyup bildirim gösterir.

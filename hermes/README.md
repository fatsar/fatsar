# 🤖 Hermes Bot Konsol

**Kendi sunucunuzda (VPS ya da ev bilgisayarınız) çalışan yapay zekâ botlarını telefonunuzdan yöneten Android uygulaması** ve onun sunucu tarafı olan **Hermes Agent**.

Grok/ChatGPT gibi tek bir sohbet yerine, **her biri kendi görevine, modeline, araçlarına ve çalışma saatine sahip birden çok bot** tanımlarsınız. Botlar sunucuda yaşar: telefonunuz kapalıyken bile "her sabah 08:30'da haberleri özetle" ya da "saat başı sunucuyu kontrol et, sorun varsa bildir" görevlerini yürütürler.

```
   📱 Android (Hermes Bot Konsol)                🖥  VPS / ev bilgisayarı
  ┌──────────────────────────────┐             ┌──────────────────────────────┐
  │  Botlarım                    │   HTTPS/SSE │  hermes_agent.py             │
  │   🛡 Sunucu Bekçisi  ⏰60dk   │◄───────────►│   • bot kaydı + hafıza       │
  │   📰 Haber Botu     ⏰08:30   │   Bearer    │   • zamanlayıcı (7/24)       │
  │   🤖 Sohbet Botu             │   token     │   • araçlar: shell, http…    │
  │   💻 Kod Yardımcısı          │             │   • model sağlayıcı köprüsü  │
  └──────────────────────────────┘             └───────────────┬──────────────┘
                                                               │
                                        xAI (Grok) · OpenAI · OpenRouter ·
                                        Groq · Ollama (yerel) · Anthropic
```

Uygulama **yalnızca kendi agent'ınıza** bağlanır. Model sağlayıcıları (xAI/Grok, OpenAI, OpenRouter, Groq, Ollama, Claude) ve **API anahtarları agent'ta tanımlıdır**; telefon yalnızca agent'ın "hazır" bildirdiği sağlayıcılar arasından seçim yapar. Anahtarlar telefona hiç inmez — telefonu kaybederseniz tek yapmanız gereken agent token'ını değiştirmektir.

---

## 1. Hızlı başlangıç

### A) VPS (Hostinger vb.) — botlar 7/24 çalışsın

Sunucuya SSH ile bağlanın ve tek satır:

```bash
curl -fsSL https://github.com/fatsar/fatsar/releases/download/hermes-v1.0.0/install.sh | sudo bash
```

Betik şunları yapar: `hermes` sistem kullanıcısı açar, `/opt/hermes` altına kurar, **systemd servisi** tanımlar (sunucu yeniden başlasa da otomatik açılır), erişim anahtarını üretir ve ekrana **eşleştirme kodunu** yazar:

```
==> TELEFONA YAPIŞTIRILACAK EŞLEŞTİRME KODU:
HERMES1:eyJ1IjoiaHR0cDovLzE5My4xODEuMS4xOjg3MTMiLCJ0IjoiWm...
```

Bu kodu kopyalayın → uygulamayı açın → kodu yapıştırın → **Bağlan**. Adres, token ve ad otomatik dolar.

### B) LLM sağlayıcınızı **sunucuda** tanımlayın

```bash
sudo -u hermes python3 /opt/hermes/hermes_agent.py --data-dir /opt/hermes/data \
     --set backends.xai.api_key=xai-XXXX --set default_backend=xai
sudo systemctl restart hermes-agent
```

Tanımlayabileceğiniz sağlayıcılar: `xai` (Grok), `openai`, `openrouter`, `groq`, `anthropic` (Claude), `ollama` (yerel modeller), `echo` (anahtarsız deneme).

Uygulama bunları **Sunucular** ekranında ve bot ayarlarında listeler: anahtarı tanımlı olanlar ✅ ile seçilebilir, eksik olanlar nedeniyle birlikte gösterilir. Model listesi de agent üzerinden gelir (`/v1/models`), böylece telefonda hiçbir anahtar tutulmaz.

### C) Kendi bilgisayarınız (denemek için en kolayı)

```bash
python3 hermes_agent.py          # Windows: py hermes_agent.py
```

Hiçbir kurulum gerekmez (Python 3.8+ yeterli). Telefon ile bilgisayar **aynı Wi-Fi ağında** olmalı; ekranda yazan `http://192.168.x.x:8713` adresi kullanılır. Anahtarınız yoksa varsayılan `echo` arka ucuyla her şeyi anahtarsız deneyebilirsiniz (bot söylediğinizi yankılar).

---

## 2. Uygulamayı kurma (APK)

1. **[Sürümler sayfasından](https://github.com/fatsar/fatsar/releases/tag/hermes-v1.0.0)** `hermes-bot-konsol.apk` dosyasını telefona indirin.
2. Dosyaya dokunun → Android "bilinmeyen kaynaklardan kuruluma izin ver" diye sorar → izin verin.
3. Android 8.0 (API 26) ve üzeri gerekir.

> APK Google Play dışından kurulduğu için imzası Play'inkinden farklıdır. Uygulamayı elle güncellerken imza uyuşmazlığı uyarısı alırsanız eskisini kaldırıp yenisini kurun (sohbet geçmişi silinir; **Ayarlar → Panoya aktar** ile önce yedek alabilirsiniz).

---

## 3. Bot nasıl kurulur?

**Yeni bot** düğmesi bir şablon listesi açar:

| Şablon | Ne yapar | Tipik ayar |
|---|---|---|
| 🤖 Sohbet Botu | Genel amaçlı, esprili asistan | Zamanlama yok |
| 💻 Kod Yardımcısı | Kod yazar, hata ayıklar | temperature 0.3 |
| 🛡️ Sunucu Bekçisi | Disk/bellek/servis kontrolü, sorun varsa bildirim | `shell` aracı + saatte bir |
| 📰 Haber Özetleyici | Verdiğiniz adresleri okur, özet çıkarır | `http_get` + her gün 08:30 |
| 🌍 Çevirmen | Metni akıcı çevirir | temperature 0.3 |
| ✍️ İçerik Yazarı | Sosyal medya/blog metni üretir | temperature 0.9 |
| ⚙️ Boş Bot | Sıfırdan sizin tanımınız | — |

Bot ayarlarında bulunanlar:

- **Sunucu / LLM sağlayıcı / model** — hangi agent, agent'ta tanımlı hangi sağlayıcı (yalnızca hazır olanlar seçilebilir) ve o sağlayıcının hangi modeli. Model boş bırakılırsa agent sağlayıcının varsayılan modelini kullanır.
- **Sistem istemi** — botun kişiliği ve görevi.
- **Yaratıcılık (temperature)**, **en fazla yanıt uzunluğu**, **hatırlanan tur sayısı** (0 = hafızasız).
- **Araçlar** — botun sunucuda kullanabileceği yetenekler (aşağıda).
- **Zamanlama** — *Kapalı* / *Aralıklı* (ör. 60 dakikada bir) / *Her gün* (ör. 08:30) + çalıştırılacak istem + sonuç bildirimi.

### Araçlar

| Araç | Açıklama |
|---|---|
| `now` | Sunucunun tarih/saati |
| `http_get` | Bir adresi indirip metne çevirir |
| `web_search` | Arama sonuçlarının başlık + adresleri |
| `read_file` / `write_file` | Yalnızca agent'ın çalışma klasörü içinde |
| `shell` | Sunucuda komut çalıştırır — **varsayılan kapalı** |

`shell` aracını açmak için:

```bash
sudo -u hermes python3 /opt/hermes/hermes_agent.py --data-dir /opt/hermes/data --set allow_shell=true
# Güvenli kullanım: yalnızca belirli komutlara izin verin
sudo -u hermes python3 /opt/hermes/hermes_agent.py --data-dir /opt/hermes/data \
     --set 'shell_allowlist=df -h'          # birden çok kez çağırarak liste büyütülebilir
sudo systemctl restart hermes-agent
```

---

## 4. Güvenlik — lütfen okuyun

Bu program telefonunuza **sunucunuzu kullanma yetkisi** verir. Varsayılanlar güvenli tarafta ayarlandı, ama şunları bilin:

1. **Token zorunludur.** Agent ilk açılışta 40 karakterlik rastgele bir anahtar üretir; token'sız hiçbir `/v1/...` isteği kabul edilmez.
2. **`shell` aracı varsayılan olarak kapalıdır** ve açıldığında bile `shell_allowlist` ile komut önekleriyle sınırlanabilir.
3. **Dosya araçları** yalnızca agent'ın çalışma klasöründe iş görür; `../` ile dışarı çıkma denemesi reddedilir (test kapsamındadır).
4. **HTTP şifresizdir.** Agent'ı internete doğrudan açıyorsanız önüne HTTPS koyun — uygulama şifresiz ve genel bir adres gördüğünde sizi uyarır. Caddy ile iki satır:

   ```
   bot.alanadiniz.com {
       reverse_proxy 127.0.0.1:8713
   }
   ```
   Ardından agent'ı `--host 127.0.0.1` ile çalıştırın ve uygulamaya `https://bot.alanadiniz.com` yazın.
5. **Daha da güvenlisi:** agent'ı yalnızca yerel ağa/VPN'e açın (Tailscale/WireGuard) ve porta güvenlik duvarından erişimi kısıtlayın.
6. Model sağlayıcı anahtarlarınızı **agent'ta** tutun (telefonda değil). Telefonu kaybederseniz `--set token=YENİ_DEĞER` ile erişimi anında kesersiniz.
7. Uygulamadaki **Ayarlar → Panoya aktar** yedeği anahtarları da içerir; bulut notlarına yapıştırmayın.

---

## 5. Sorun giderme

| Belirti | Çözüm |
|---|---|
| Uygulamada "Sunucuya bağlanılamadı" | Agent çalışıyor mu: `systemctl status hermes-agent`. Port açık mı: `sudo ufw allow 8713/tcp`. Hostinger panelinde de güvenlik duvarı olabilir. |
| "Token hatalı veya eksik (401)" | Eşleştirme kodunu yeniden alın: `sudo -u hermes python3 /opt/hermes/hermes_agent.py --data-dir /opt/hermes/data --show-pairing` |
| Model listesi boş | Sağlayıcı `/v1/models` desteklemiyor olabilir; bot ayarlarında model adını elle yazın. |
| "API anahtarı ayarlı değil" | `--set backends.<sağlayıcı>.api_key=...` sonra `systemctl restart hermes-agent`. |
| Zamanlanmış bot çalışmıyor | Zamanlama agent'ta çalışır: `tail -f /var/log/hermes-agent.log`. Sonuç bildirimi için Ayarlar → "Zamanlanmış sonuçları getir ve bildir" açık olmalı. |
| Bot ayarlarında sağlayıcı listesi boş | Agent'a bağlantı kurulmamış ya da hiçbir sağlayıcının anahtarı yok: `--set backends.<ad>.api_key=…` sonra servisi yeniden başlatın. |
| Yanıt yarıda kesiliyor | Telefon uykuya geçse bile çalışma sunucuda tamamlanır; sonucu bot sohbetinde/çalışma geçmişinde görürsünüz. |

Agent'ı elle denemek:

```bash
curl -H "Authorization: Bearer TOKEN" http://SUNUCU:8713/v1/health
```

---

## 6. Proje yapısı ve geliştirme

```
hermes/
├── agent/
│   ├── hermes_agent.py        # Sunucu (tek dosya, bağımlılıksız Python 3)
│   ├── install.sh             # VPS kurulum betiği (systemd)
│   ├── hermes-agent.service   # systemd birimi
│   └── tests/test_agent.py    # 33 test
├── android/
│   ├── core/                  # Saf Kotlin: Hermes protokol istemcisi, depo, şablonlar
│   └── app/                   # Jetpack Compose arayüz
└── docs/PROTOKOL.md           # Hermes Agent HTTP/SSE protokolü
```

**Test durumu:** çekirdek ve sunucu testleri her derlemede zorunlu olarak koşar.
Arayüz testleri (Robolectric) *bilgilendirme* amaçlıdır: uygulamanın açıldığını ve
ekranlar arasında gezinildiğini doğrular, ama koşucuya göre yavaşlayabildiği için
APK üretimini durdurmaz. Sonuçları CI günlüğünün sonundaki "Arayüz testi özeti" adımında görebilirsiniz.

```bash
# Sunucu testleri
python3 -m unittest discover -s hermes/agent/tests -v

# Çekirdek testleri + APK
cd hermes/android
./gradlew :core:test               # çekirdek (53 test)
./gradlew :app:testDebugUnitTest   # arayüz testleri (Robolectric)
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/
```

**Kalıcı imza (isteğe bağlı):** kendi anahtarınızı üretip depo gizli değeri olarak eklerseniz CI onunla imzalar ve güncellemeler eskisinin üzerine kurulur:

```bash
keytool -genkeypair -keystore hermes.jks -alias hermes -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 hermes.jks     # çıktıyı HERMES_KEYSTORE_BASE64 gizli değeri olarak ekleyin
```
Ayrıca `HERMES_KEYSTORE_PASSWORD`, `HERMES_KEY_ALIAS`, `HERMES_KEY_PASSWORD` gizli değerlerini tanımlayın.

## 7. Lisans ve sorumluluk

Kişisel kullanım için yazılmış açık kaynak bir araçtır. Sunucunuzda komut çalıştırabilen bir bot kurmak güçlü ama riskli bir yetkidir; `shell` aracını yalnızca ne yaptığınızı bilerek açın.

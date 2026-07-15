# Toplantı Asistanı 🎙️

Toplantıları **kaydeden veya mevcut ses/video dosyasını içe aktaran**, konuşmayı **Türkçe yazıya döken**, dolgu sözcüklerini temizleyen, **özet / karar / önemli not / açık soru / risk ve görev** çıkaran Android uygulaması.

Ürün gereksinimleri: [prd.md](prd.md) — uygulama bu PRD'nin **MVP kapsamını** hedefler.

Depodaki Kartvizit Tarayıcı ile aynı ilkeyle çalışır: **her şey cihaz üzerinde**. Konuşma tanıma için [Vosk](https://alphacephei.com/vosk/) açık kaynak motoru ve resmî Türkçe modeli kullanılır; model ilk kullanımda **bir kez** indirilir (~45 MB), sonrasında tüm işlemler tamamen çevrimdışıdır. **Ses kaydı, transkript veya notlar hiçbir sunucuya gönderilmez.**

## Özellikler

- 🎙️ **Canlı kayıt** – ön plan servisiyle ekran kilitliyken bile kesintisiz kayıt; duraklat/sürdür/bitir; süre ve ses seviyesi göstergesi (FR-001..005).
- 📁 **Dosya içe aktarma** – mevcut ses/video dosyalarını (m4a, mp3, wav, mp4…) seçerek veya başka uygulamadan "Paylaş" ile içe alın (FR-006).
- ✍️ **Türkçe transkripsiyon** – segment bazında zaman damgası, konuşmacı etiketi ve güven skoru (FR-010, FR-015); düşük güvenli bölümler işaretlenir ve **dokunarak kayıttan dinlenir** (FR-016, AC-011).
- 🧹 **Ham + temiz transkript** – "ıı", "eee", "şey" gibi dolgular ve açık tekrarlar temizlenir; ham metin değiştirilemez biçimde korunur, düzeltmeler ayrı tutulur (FR-011..014).
- 📋 **AI çıktıları** – kısa/ayrıntılı özet, kararlar, önemli notlar, açık sorular, riskler; her madde kaynak segmente bağlanır (FR-020..022).
- ✅ **Görev çıkarımı** – "Ayşe, cuma gününe kadar teklif taslağını gönderecek" gibi ifadelerden görev + sahip + termin adayı çıkarılır; belirsiz alanlar **"Onay gerekli"** işaretlenir, bilgi uydurulmaz (FR-030..033).
- 🏷️ **Başlık önerisi** – içerikten tahmin edilir; **kullanıcı onaylamadan nihai başlık olmaz** (FR-023, AC-014).
- 📸 **Ekler** – kayıt sırasında fotoğraf/video çekin veya galeriden seçin; ekler toplantı içi zaman damgasıyla saklanır (FR-040..044).
- 🗂️ **Arşiv ve arama** – tarih gruplu liste; başlık, etiket ve transkript metninde arama; çoklu etiket (FR-050..053).
- ✉️ **E-posta paylaşımı** – bölümleri ve ekleri seçin; konu/gövde hazır taslak e-posta uygulamanızda açılır, **onaysız hiçbir şey gönderilmez** (FR-060..062).
- 📤 **Dışa aktarma** – PDF, DOCX, TXT, JSON ve eklerle ZIP; DOCX/PDF üretimi harici kütüphanesiz (FR-064).
- 🗑️ **Kalıcı silme** – silinecek tüm bağlı veriler listelenir, onaydan sonra toplantıya ait her şey cihazdan kaldırılır (FR-054, AC-034).

## Kullanım

1. **Yeni toplantı** → katılımcı rızası hatırlatmasını onaylayın → kayıt başlar. İsterseniz kayıt sırasında fotoğraf/video ekleyin.
2. **Bitir ve işle** → yazıya dökme ve analiz arka planda sürer; bittiğinde bildirim gelir.
3. **İnceleme**: önerilen başlığı onaylayın/düzenleyin, "Onay gerekli" görevleri doğrulayın, konuşmacıları adlandırın, gerekirse segmentleri düzeltin → **İncelemeyi tamamla**.
4. **Paylaş**: bölümleri ve ekleri seçin; e-posta taslağı hazır açılır ya da PDF/DOCX/TXT/JSON/ZIP olarak dışa aktarın.

## Gerekli izinler

| İzin | Neden |
|---|---|
| Mikrofon | Yalnızca canlı kayıt başlatılırken, bağlam içinde istenir |
| Bildirimler (Android 13+) | Kayıt sürüyor / işleme tamamlandı bildirimleri |
| İnternet | Yalnızca Türkçe tanıma modelinin tek seferlik indirmesi; ses verisi asla gönderilmez |

Kamera izni gerekmez (sistem kamera uygulaması kullanılır); galeri erişimi Android Photo Picker ile sağlanır, depolama izni gerektirmez (PRD 14.1).

## Mimari

```
toplanti/src/main/java/com/fatsar/toplanti/
├── ui/                      # Ana ekran, kayıt öncesi/kayıt/detay ekranları, adaptörler
├── service/RecordingService # Ön plan mikrofon servisi → 16 kHz mono WAV
├── service/ProcessingService# Arka plan işleme: çözme → ASR → temizleme → analiz
├── audio/                   # WAV yazıcı, MediaCodec çözücü, mono/16k yeniden örnekleyici
├── asr/                     # Vosk model yöneticisi (indirme) ve transkripsiyon
├── nlp/                     # Cihaz üstü Türkçe analiz (kural tabanlı, birim testli):
│                            #   TranscriptCleaner, InsightExtractor, TaskExtractor,
│                            #   TitleSuggester, MeetingAnalyzer
├── export/                  # TXT/JSON/PDF/DOCX/ZIP üreticileri + e-posta taslağı
├── data/MeetingRepository   # Uygulamaya özel depolamada JSON tabanlı kayıt deposu
└── model/                   # Meeting, TranscriptSegment, Insight, TaskItem, Attachment
```

İşleme hattı (PRD 12 ile hizalı): girdi doğrulama → çözme/normalizasyon (16 kHz mono) → Vosk ASR (kelime zaman damgaları + güven) → segmentleme → dolgu temizleme → özet/karar/soru/risk/görev çıkarımı → kaynak bağlama → kullanıcı onayı. Toplantı içeriği yalnızca işlenecek veri olarak ele alınır; içerikteki hiçbir ifade uygulama davranışını değiştirmez ve onaysız dış eylem (e-posta vb.) tetiklenmez (PRD 12/11–12).

## PRD'ye göre bilinçli MVP kararları

PRD'nin açık soruları (bölüm 20) ve bazı gereksinimler için bu sürümde alınan kararlar:

| Konu | Karar |
|---|---|
| İşleme yeri (soru 1) | Tamamen **cihaz üzerinde** (Vosk + kural tabanlı NLP). Bulut/backend yok; gizlilik ve maliyet açısından en güvenli varsayılan. |
| Min. Android (soru 2) | minSdk 26 (Android 8.0), targetSdk 34 — depodaki diğer uygulamayla aynı. |
| E-posta (soru 5) | Kullanıcının kendi e-posta uygulamasında hazır taslak (Android paylaşım); uygulama kendiliğinden göndermez. |
| Video içe aktarma (soru 6) | Yalnızca ses izi analiz edilir; kare/OCR analizi kapsam dışı. |
| Konuşmacı ayrımı | Otomatik diarizasyon MVP'de yok; PRD hata tablosundaki geri düşüş uygulanır: tek "Konuşmacı 1" + kullanıcı elle konuşmacı ekleyip segment atayabilir ve yeniden adlandırabilir (AC-012 sağlanır). |
| Özet/görev çıkarımı | Deterministik, cihaz üstü kural tabanlı Türkçe NLP; bilgi uydurmama kuralı yapısal olarak garanti (yalnızca kaynak cümleden alıntılanır). |
| Şifreleme | Kayıtlar Android uygulama korumalı alanında (başka uygulama erişemez) tutulur; Keystore tabanlı dosya şifrelemesi ve saklama süresi ayarları sonraki sürüme bırakıldı. |
| Takvim entegrasyonu, şablonlar, canlı transkript | PRD'de P1/P2 — kapsam dışı. |

## APK'yı edinme

Her push'ta GitHub Actions derler: depo **Actions** sekmesi → son "Android CI" çalışması → **Artifacts** → `toplanti-asistani-debug-apk`.

## Kendi makinenizde derleme

```bash
./gradlew :toplanti:assembleDebug       # APK: toplanti/build/outputs/apk/debug/toplanti-debug.apk
./gradlew :toplanti:testDebugUnitTest   # Birim testleri (temizleyici, görev/başlık/içgörü çıkarımı, DOCX)
```

Teknolojiler: Kotlin, Material 3, ViewBinding, Coroutines, Vosk (offline ASR, Apache 2.0), sıfır-bağımlılık DOCX/PDF üreticileri.

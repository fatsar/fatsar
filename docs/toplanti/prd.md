# Toplantı Notları Android Uygulaması — Ürün Gereksinimleri Dokümanı (PRD)

## 1. Doküman Bilgileri

| Alan | Değer |
|---|---|
| Ürün adı | Geçici ad: **Toplantı Asistanı** |
| Platform | Android |
| Doküman türü | Ürün Gereksinimleri Dokümanı (PRD) |
| Sürüm | 1.0 |
| Tarih | 15 Temmuz 2026 |
| Durum | Taslak — ürün, hukuk ve teknik değerlendirmeye açık |
| Hedef sürüm | MVP |
| Birincil dil | Türkçe; çoklu dil desteğine uygun mimari |
| Paydaşlar | Ürün, Android, Backend, AI/ML, Tasarım, QA, Güvenlik, Hukuk/KVKK |

### 1.1 Terimler

- **Toplantı:** Canlı kaydedilen veya sonradan içe aktarılan 1–2 saatlik ses/video oturumu.
- **Ham transkript:** Konuşmanın, dolgu sözcükleri ve tekrarlar dâhil ilk metin çıktısı.
- **Temiz transkript:** Anlamı koruyarak gereksiz tekrar, dolgu sözcüğü ve konuşma gürültüsü azaltılmış metin.
- **Aksiyon/Görev:** Toplantıda yapılacağı kararlaştırılan, tercihen sahibi ve termin tarihi bulunan iş maddesi.
- **Ek:** Toplantı sırasında veya sonrasında ilişkilendirilen görsel ya da video dosyası.
- **Güven skoru:** AI çıktısının doğruluğuna ilişkin 0–100 arası tahmini güven göstergesi.

## 2. Ürün Özeti

Toplantı Asistanı, Android cihazlarda 1–2 saat süren toplantıları kaydeden veya mevcut bir kaydı içe alan; sesi metne dönüştüren; konuşmacıları ayıran; gereksiz dolgu sözcüklerini ve tekrarları temizleyen; özet, önemli notlar, kararlar ve görevler üreten bir toplantı yönetimi uygulamasıdır.

Uygulama toplantı başlığını içerikten tahmin eder ve kaydetmeden önce kullanıcı onayı ister. Kullanıcı toplantı sırasında görsel veya video ekleyebilir; ekler dosya olarak saklanır ve notların içinde zaman damgalı referanslarla gösterilir. Toplantılar tarih bazında arşivlenir, aranabilir ve isteğe bağlı olarak e-posta ile paylaşılabilir.

Ürünün temel ilkesi, AI çıktısını kesin gerçek olarak sunmak yerine kullanıcı tarafından doğrulanabilir, düzeltilebilir ve kaynak zaman damgasına geri izlenebilir hâle getirmektir.

## 3. Problem Tanımı

Uzun toplantılarda katılımcılar aynı anda hem konuşmaya odaklanmak hem de eksiksiz not almak zorunda kalır. Manuel notlar çoğunlukla eksik, kişisel yoruma açık ve görev takibi için yetersizdir. Kayıt almak sorunu tek başına çözmez; 1–2 saatlik kaydı yeniden dinlemek zaman alır. Mevcut çözümlerde Türkçe doğruluğu, konuşmacı ayrımı, görev sahibinin çıkarılması, medya eklerinin bağlama yerleştirilmesi ve kullanıcı gizliliği birlikte yeterli düzeyde ele alınmayabilir.

Ürün şu sorunları çözmelidir:

- Toplantı içeriğinin kaybolması veya yanlış hatırlanması.
- Uzun kayıtların tekrar dinlenmesinin oluşturduğu zaman maliyeti.
- Kararların ve görevlerin dağınık kalması.
- Görev sahibi ve termin tarihlerinin unutulması.
- Görsel/video eklerinin toplantı bağlamından kopması.
- Toplantı çıktılarının paylaşılmasının zahmetli olması.
- Otomatik transkripsiyon ve özetlerdeki hataların kolayca doğrulanamaması.

## 4. Hedefler ve Kapsam Dışı Konular

### 4.1 Ürün hedefleri

1. 1–2 saatlik toplantıyı kesintisiz ve güvenilir biçimde kaydetmek veya içe almak.
2. Türkçe konuşmayı okunabilir ve zaman damgalı metne dönüştürmek.
3. Ham içeriği korurken ayrıca anlam kaybını en aza indiren temiz transkript sunmak.
4. Özet, önemli notlar, kararlar, sorular ve aksiyonları otomatik üretmek.
5. Aksiyonlar için görev sahibi ve termin tarihi adaylarını çıkarmak ve kullanıcıya onaylatmak.
6. Tahmini toplantı başlığını kullanıcı onayından sonra kaydetmek.
7. Görsel/video eklerini dosya olarak saklamak ve ilgili zaman/nota bağlamak.
8. Toplantıları tarih, başlık, kişi, etiket ve içerik üzerinden bulmayı kolaylaştırmak.
9. Kullanıcı onayıyla e-posta ve standart dosya formatlarında paylaşım sağlamak.
10. KVKK odaklı açık rıza, veri kontrolü, şifreleme ve silme mekanizmaları sunmak.

### 4.2 Non-goals / kapsam dışı

- MVP’de görüntülü toplantı platformunun veya konferans sisteminin yerini almak.
- İnsan onayı olmadan görevleri üçüncü taraf sistemlerde otomatik atamak.
- AI çıktılarının hukuki tutanak veya resmî kayıt olduğunu iddia etmek.
- Gizli biçimde kayıt almak ya da katılımcı rızası yönetimini kullanıcıdan devralmak.
- MVP’de gerçek zamanlı çeviri, duygu analizi veya çalışan performans puanlama yapmak.
- MVP’de masaüstü/iOS için tam istemci geliştirmek.
- Konuşmacının kimliğini biyometrik olarak tanımak; sistem yalnızca konuşma segmentlerini ayırır ve kullanıcı isimlendirmesine izin verir.

## 5. Hedef Kullanıcılar ve Personalar

### 5.1 Birincil kullanıcılar

- Proje yöneticileri ve ekip liderleri.
- Danışmanlar, satış ve müşteri başarı ekipleri.
- Küçük işletme sahipleri ve girişim ekipleri.
- Yoğun toplantı yapan bilgi çalışanları.
- Öğrenciler ve akademik çalışma grupları; rıza ve kurum politikaları çerçevesinde.

### 5.2 Personalar

**Ayşe — Proje Yöneticisi**  
Haftada 15–20 toplantıya katılır. Kararları, görev sahiplerini ve terminleri hızlıca ekibe göndermek ister. Yanlış görevlendirme riskine karşı gönderimden önce inceleme bekler.

**Mert — Satış Temsilcisi**  
Müşteri görüşmelerinden ihtiyaç, itiraz ve sonraki adımları çıkarmak ister. Kayıt izni konusunda açık uyarılara ve müşteri bazında aramaya ihtiyaç duyar.

**Selin — Küçük İşletme Sahibi**  
Teknik kuruluma zaman ayırmak istemez. Tek dokunuşla kayıt, net özet ve e-posta paylaşımı bekler.

**Deniz — Erişilebilirlik İhtiyacı Olan Kullanıcı**  
Toplantıyı metinden takip etmek, yazı boyutunu büyütmek ve ekran okuyucu ile tüm temel işlevlere erişmek ister.

## 6. Kullanıcı Senaryoları

1. Kullanıcı yeni toplantı başlatır, katılımcı rızasını teyit eder ve 90 dakika boyunca kayıt alır.
2. İnternet bağlantısı kesildiğinde kayıt yerel tamponda devam eder; bağlantı gelince güvenli yükleme sürer.
3. Kullanıcı toplantı sırasında bir tahta fotoğrafı ekler; fotoğraf, eklendiği dakikayla not akışında görünür.
4. Kullanıcı mevcut bir ses/video dosyasını içe aktarır ve sonradan işlenmesini bekler.
5. İşleme tamamlanınca kullanıcı ham ve temiz transkript arasında geçiş yapar.
6. Kullanıcı konuşmacı etiketlerini “Konuşmacı 1” yerine gerçek isimlerle değiştirir; tüm segmentler güncellenir.
7. Uygulama “Mobil Uygulama MVP Planlama” başlığını önerir; kullanıcı onaylar veya düzenler.
8. Uygulama “Ayşe, cuma gününe kadar teklif taslağını gönderecek” ifadesinden görev, sahip ve termin çıkarır; kullanıcı doğrular.
9. Kullanıcı düşük güvenli bir transkript bölümünü kayıttan dinleyerek düzeltir; bağlı özet/görevler yeniden oluşturulabilir.
10. Kullanıcı özet, kararlar, görevler ve ek bağlantılarını e-posta taslağında görür; alıcıları kontrol edip gönderir.
11. Kullanıcı geçmiş bir toplantıyı tarih, etiket veya transkriptte geçen sözcükle bulur.
12. Kullanıcı toplantıyı ve ona bağlı ham kayıt, transkript, AI çıktıları ve ekleri kalıcı olarak siler.

## 7. Kapsam

### 7.1 MVP kapsamı

- Android’de canlı ses kaydı; duraklatma ve devam ettirme.
- Ses/video dosyası içe aktarma.
- 120 dakikaya kadar kayıt işleme.
- Türkçe transkripsiyon, temel zaman damgaları ve konuşmacı ayrımı.
- Ham ve temiz transkript.
- Özet, önemli notlar, kararlar ve görevler.
- Tahmini başlık ve kullanıcı onayı.
- Görsel/video ekleme, yerel ön izleme ve not içi referans.
- Tarih bazlı toplantı listesi ve toplantı detay ekranı.
- Görev düzenleme; kişi ve termin onayı.
- Kullanıcı onayıyla e-posta paylaşımı.
- PDF, DOCX, TXT ve JSON dışa aktarma; ekler için ZIP paketleme.
- Temel arama, etiketleme, şifreleme, silme ve veri saklama ayarları.
- İşleme durumu, hata geri bildirimi ve yeniden deneme.

### 7.2 Sonraki sürümler

- Takvim entegrasyonu ve toplantı eşleştirme.
- Toplantı şablonları ve özelleştirilebilir özet yapısı.
- Gelişmiş görev yönetimi entegrasyonları.
- Kurumsal çalışma alanları, rol tabanlı erişim ve yönetici politikaları.
- Desteklenen dillerin genişletilmesi ve çok dilli toplantılar.
- Kullanıcı tercihine bağlı canlı transkript.

## 8. Fonksiyonel Gereksinimler

Öncelikler: **MVP** zorunlu, **P1** ilk genişletme, **P2** ileri sürüm.

### 8.1 Toplantı oluşturma ve kayıt

| Kimlik | Gereksinim | Öncelik |
|---|---|---|
| FR-001 | Kullanıcı ana ekrandan tek dokunuşla yeni toplantı başlatabilmelidir. | MVP |
| FR-002 | Kayıt başlamadan önce mikrofon izni ve katılımcı rızası hatırlatması gösterilmelidir. | MVP |
| FR-003 | Kullanıcı kaydı duraklatabilmeli, sürdürebilmeli ve bitirebilmelidir. | MVP |
| FR-004 | Ekran kilitlendiğinde, uygulama arka plana alındığında veya kısa süreli bağlantı kesildiğinde kayıt devam etmelidir; Android foreground service kullanılmalıdır. | MVP |
| FR-005 | Süre, kayıt durumu, depolama uyarısı ve ses giriş seviyesi görünmelidir. | MVP |
| FR-006 | Kullanıcı desteklenen ses/video dosyalarını içe aktarabilmelidir. | MVP |
| FR-007 | Uygulama aynı toplantı için canlı kayıt ve sonradan kayıt/içe aktarma modlarını açıkça ayırmalıdır. | MVP |
| FR-008 | Kullanıcı kayıt sırasında zaman damgalı manuel not ekleyebilmelidir. | P1 |

### 8.2 Transkripsiyon ve temizleme

| Kimlik | Gereksinim | Öncelik |
|---|---|---|
| FR-010 | Sistem Türkçe sesi zaman damgalı metne dönüştürmelidir. | MVP |
| FR-011 | Ham transkript değiştirilemez kaynak sürüm olarak korunmalı; kullanıcı düzenlemeleri sürümlenmelidir. | MVP |
| FR-012 | Sistem “ıı”, “şey”, “yani” gibi dolgu sözcüklerini, anlamsız sesleri ve açık tekrarları azaltan temiz transkript üretmelidir. | MVP |
| FR-013 | Temizleme anlamı, olumsuzlukları, sayıları, özel adları ve karar ifadelerini değiştirmemelidir. | MVP |
| FR-014 | Kullanıcı ham ve temiz transkript arasında geçiş yapabilmelidir. | MVP |
| FR-015 | Segmentler konuşmacı etiketi, başlangıç/bitiş zamanı ve güven skoru içermelidir. | MVP |
| FR-016 | Düşük güvenli segmentler görsel olarak işaretlenmeli ve kayıttaki ilgili noktadan oynatılabilmelidir. | MVP |
| FR-017 | Kullanıcı metni ve konuşmacı etiketini düzeltebilmelidir. | MVP |

### 8.3 AI çıktıları

| Kimlik | Gereksinim | Öncelik |
|---|---|---|
| FR-020 | Sistem kısa özet ve ayrıntılı özet üretmelidir. | MVP |
| FR-021 | Sistem önemli notları, alınan kararları, açık soruları ve riskleri ayrı bölümlerde çıkarmalıdır. | MVP |
| FR-022 | Her AI maddesi mümkün olduğunda bir veya daha fazla kaynak segment/zaman damgasına bağlanmalıdır. | MVP |
| FR-023 | Sistem toplantı başlığı tahmin etmeli, kullanıcı onaylamadan nihai başlık olarak kaydetmemelidir. | MVP |
| FR-024 | Kullanıcı başlık, özet, not, karar ve görevleri düzenleyebilmelidir. | MVP |
| FR-025 | Transkript düzeltmesinden sonra kullanıcı etkilenen AI çıktılarını yeniden oluşturabilmelidir. | P1 |
| FR-026 | Sistem çıktı düzeyinde kalite/güven skoru ve düşük güven nedenini göstermelidir. | P1 |

### 8.4 Görevler

| Kimlik | Gereksinim | Öncelik |
|---|---|---|
| FR-030 | Sistem açık ve örtük aksiyon ifadelerinden görev adayı çıkarmalıdır. | MVP |
| FR-031 | Görev; açıklama, sahip, termin, durum, kaynak zaman damgası ve güven skoru alanlarını desteklemelidir. | MVP |
| FR-032 | Belirsiz sahip veya termin “Onay gerekli” olarak işaretlenmelidir; bilgi uydurulmamalıdır. | MVP |
| FR-033 | Kullanıcı görevleri onaylayabilmeli, düzenleyebilmeli, silebilmeli ve manuel görev ekleyebilmelidir. | MVP |
| FR-034 | E-posta/dışa aktarmada yalnızca kullanıcı tarafından seçilen görevler yer almalıdır. | MVP |
| FR-035 | Takvim veya görev yönetim sistemine aktarma, ayrıca kullanıcı onayı gerektirmelidir. | P1 |

### 8.5 Görsel ve video ekleri

| Kimlik | Gereksinim | Öncelik |
|---|---|---|
| FR-040 | Kullanıcı toplantı sırasında kameradan görsel/video çekebilmeli veya galeriden seçebilmelidir. | MVP |
| FR-041 | Her ek özgün dosya, ön izleme, MIME türü, boyut, eklenme zamanı ve toplantı zaman damgasıyla saklanmalıdır. | MVP |
| FR-042 | Ek, not akışında tıklanabilir bir referans kartı ve toplantı detayında dosya olarak görünmelidir. | MVP |
| FR-043 | Kullanıcı eke açıklama ve etiket ekleyebilmelidir. | MVP |
| FR-044 | Kullanıcı paylaşım öncesinde hangi eklerin gönderileceğini seçebilmelidir. | MVP |
| FR-045 | Dışa aktarmada dosya boyutu veya sağlayıcı sınırı aşılırsa bağlantı, sıkıştırma ya da hariç tutma seçenekleri sunulmalıdır. | P1 |

### 8.6 Arşiv, arama ve düzenleme

| Kimlik | Gereksinim | Öncelik |
|---|---|---|
| FR-050 | Toplantılar oluşturulma tarihi ve toplantı tarihiyle kaydedilmelidir. | MVP |
| FR-051 | Ana ekran yaklaşan/işlenen ve geçmiş toplantıları tarih sırasıyla göstermelidir. | MVP |
| FR-052 | Kullanıcı başlık, tarih aralığı, etiket ve transkript metnine göre arayabilmelidir. | MVP |
| FR-053 | Kullanıcı toplantıya birden çok etiket ekleyebilmelidir. | MVP |
| FR-054 | Silme işleminde toplantıya bağlı tüm veriler açıkça listelenmeli ve kullanıcıdan onay alınmalıdır. | MVP |

### 8.7 Paylaşım ve dışa aktarma

| Kimlik | Gereksinim | Öncelik |
|---|---|---|
| FR-060 | Kullanıcı özet, not, karar, görev, transkript ve eklerden hangilerinin paylaşılacağını seçebilmelidir. | MVP |
| FR-061 | Uygulama konu ve gövdesi önceden doldurulmuş e-posta taslağı üretmelidir. | MVP |
| FR-062 | E-posta alıcıları ve içerik, açık kullanıcı onayı olmadan gönderilmemelidir. | MVP |
| FR-063 | Başarılı/başarısız gönderim sonucu ve yeniden deneme seçeneği gösterilmelidir. | MVP |
| FR-064 | PDF, DOCX, TXT, JSON ve eklerle ZIP dışa aktarma desteklenmelidir. | MVP |

## 9. Önerilen Ek Özellikler

Bu bölüm, ilk kullanıcı talebini güçlendirmek amacıyla ürün önerisi olarak eklenen özellikleri açıkça işaretler.

1. **Konuşmacı ayrımı:** Konuşma bölümlerini “Konuşmacı 1, 2…” olarak ayırır; kullanıcıların sonradan isim vermesine olanak tanır. Özet ve görevlerde kimin ne söylediğinin izlenmesini sağlar.
2. **Canlı/sonradan kayıt modu:** Kullanıcı hem uygulamada canlı kayıt yapabilir hem de mevcut ses/video dosyasını içe aktarabilir. Böylece farklı toplantı alışkanlıkları desteklenir.
3. **Zaman damgaları:** Transkript segmentleri, önemli notlar, görevler ve ekler kaynak zamana bağlanır. Kullanıcı iddiayı tek dokunuşla kayıttan doğrulayabilir.
4. **Görev sahibi ve termin tarihi çıkarımı:** AI, görev ifadesindeki kişi ve tarih adaylarını bulur; belirsiz alanları kullanıcı onayına sunar ve hiçbir bilgiyi uydurmaz.
5. **Takvim entegrasyonu:** Toplantı tarihini, başlığını ve katılımcı adaylarını takvim etkinliğiyle eşleştirir; onaylanmış görevler için hatırlatıcı oluşturabilir.
6. **Arama ve etiketleme:** Başlık, konuşmacı, tarih, etiket ve tam metin üzerinden geçmiş toplantıları bulmayı sağlar.
7. **Gizlilik, izinler ve şifreleme:** Açık rıza hatırlatması, en az ayrıcalıklı izinler, aktarımda ve depolamada şifreleme, saklama süresi ve kalıcı silme kontrolü sunar.
8. **Çevrimdışı kayıt tamponu:** İnternet olmasa bile kaydın şifreli biçimde cihazda sürmesini, bağlantı gelince parçalı ve devam edebilir yüklemeyi sağlar.
9. **Dışa aktarma formatları:** PDF, DOCX, TXT, JSON ve ZIP; farklı kullanıcıların arşivleme, düzenleme ve sistem entegrasyonu ihtiyaçlarını karşılar.
10. **Toplantı şablonları:** Stand-up, müşteri görüşmesi, proje planlama ve karar toplantısı gibi türlerde farklı özet/görev bölümleri sağlar.
11. **Kalite güven skoru:** Transkript segmentlerinde ve AI çıktılarında tahmini güveni gösterir; düşük kaliteli kayıt veya belirsiz çıkarımları görünür kılar.
12. **Kullanıcı düzeltme akışı:** Kullanıcı kaynağı dinleyerek metni, konuşmacıyı, başlığı ve AI çıktılarını düzeltebilir; düzeltmeler sürümlenir ve istenirse türetilmiş çıktılar yenilenir.

## 10. Ana Ekranlar ve Akışlar

### 10.1 Ana ekran

- “Yeni toplantı” birincil eylemi.
- “Dosya içe aktar” ikincil eylemi.
- Tarih gruplu geçmiş toplantı listesi.
- İşleniyor, inceleme gerekli ve tamamlandı durumları.
- Arama ve etiket filtreleri.

### 10.2 Kayıt öncesi ekran

1. Toplantı dili ve kayıt modu seçimi.
2. Mikrofon/medya izinleri.
3. Katılımcı rızası ve yerel mevzuat uyarısı.
4. Tahmini boş alanlar: geçici başlık, isteğe bağlı katılımcılar ve şablon.
5. “Kaydı başlat” eylemi.

### 10.3 Canlı kayıt ekranı

- Kayıt süresi, dalga/seviye göstergesi ve durum.
- Duraklat, sürdür, bitir.
- Görsel ekle, video ekle ve manuel zaman damgalı not.
- Depolama, pil ve bağlantı uyarıları.
- Kayıt sürdüğünü belirten kalıcı Android bildirimi.

### 10.4 İşleme ekranı

- Yükleme, transkripsiyon, konuşmacı ayrımı, analiz ve sonuç hazırlama adımları.
- Yüzde veya aşama bazlı ilerleme; kesin olmayan süre tahmininden kaçınma.
- Uygulamadan çıkılabileceği ve tamamlanınca bildirim gönderileceği bilgisi.
- İptal ve hata hâlinde yeniden deneme.

### 10.5 İnceleme ve başlık onayı

1. Tahmini başlık, düzenlenebilir alan olarak gösterilir.
2. Kullanıcı öneriyi onaylar veya değiştirir.
3. Düşük güvenli transkript ve görevler “İnceleme gerekli” bölümünde toplanır.
4. Kullanıcı konuşmacıları, görev sahibi/terminini ve önemli AI maddelerini doğrular.
5. “İncelemeyi tamamla” ile toplantı tamamlandı durumuna geçer.

### 10.6 Toplantı detay ekranı

Sekmeler veya erişilebilir bölümler:

- Genel Bakış: başlık, tarih, süre, etiketler, kısa özet.
- Notlar: önemli noktalar, kararlar, açık sorular, riskler.
- Görevler: sahip, termin, durum ve kaynak.
- Transkript: ham/temiz seçimi, konuşmacılar, zaman damgaları, arama.
- Ekler: görsel/video dosyaları ve not içi bağlam.
- Paylaş/Dışa Aktar.

### 10.7 E-posta paylaşım akışı

1. Paylaşılacak bölümleri seç.
2. Ekleri seç ve toplam boyutu gör.
3. Alıcı, konu ve gövdeyi incele/düzenle.
4. Son onay ver.
5. Gönderim sonucunu gör; başarısızsa taslağı koru ve yeniden dene.

## 11. Veri Modeli

### 11.1 Ana varlıklar

**User**

- `id`, `email`, `locale`, `timezone`
- `retention_policy`, `notification_preferences`
- `created_at`, `updated_at`

**Meeting**

- `id`, `user_id`, `title`, `suggested_title`, `title_confirmed`
- `meeting_date`, `started_at`, `ended_at`, `duration_seconds`
- `mode` (`live`, `imported`), `language`, `status`
- `template_id`, `processing_version`
- `created_at`, `updated_at`, `deleted_at`

**Recording**

- `id`, `meeting_id`, `local_uri`, `remote_object_key`
- `mime_type`, `size_bytes`, `duration_seconds`, `checksum`
- `upload_status`, `encryption_metadata`, `created_at`

**Speaker**

- `id`, `meeting_id`, `label`, `display_name`, `confirmed_by_user`

**TranscriptSegment**

- `id`, `meeting_id`, `speaker_id`
- `start_ms`, `end_ms`, `raw_text`, `clean_text`
- `confidence`, `review_status`, `revision_number`

**MeetingInsight**

- `id`, `meeting_id`, `type` (`summary`, `important_note`, `decision`, `question`, `risk`)
- `content`, `confidence`, `review_status`
- `source_segment_ids`, `revision_number`

**Task**

- `id`, `meeting_id`, `title`, `description`
- `owner_text`, `owner_contact_id`, `due_at`, `due_text_original`
- `status`, `confidence`, `review_status`, `source_segment_ids`

**Attachment**

- `id`, `meeting_id`, `type`, `mime_type`, `filename`
- `local_uri`, `remote_object_key`, `thumbnail_uri`, `size_bytes`, `checksum`
- `captured_at`, `meeting_offset_ms`, `caption`, `tags`

**Tag / MeetingTag**

- `Tag`: `id`, `user_id`, `name`, `color`
- `MeetingTag`: `meeting_id`, `tag_id`

**Export / EmailDelivery**

- `id`, `meeting_id`, `format` veya `provider`
- seçilen bölümler, alıcılar, `status`, `attempt_count`, hata kodu
- `created_at`, `completed_at`; hassas alıcı verileri için sınırlı saklama

**ConsentAcknowledgement / AuditEvent**

- `id`, `meeting_id`, `user_id`, `type`, `timestamp`, `policy_version`
- Hassas kayıt içeriği yerine gerekli güvenlik ve kullanıcı eylemi metadatası.

### 11.2 İlişkiler ve yaşam döngüsü

- Bir `Meeting`, bir veya daha fazla `Recording`, çok sayıda `TranscriptSegment`, `Insight`, `Task` ve `Attachment` içerir.
- AI çıktıları kaynak segment kimliklerini saklar; böylece zaman damgasına geri izlenir.
- Toplantı silindiğinde ilişkili bulut nesneleri, indeksler ve türetilmiş çıktılar da saklama politikası içinde silinir.
- Ham kayıt ile kullanıcı düzeltmeleri ayrı sürümlerde tutulur; dışa aktarmada güncel onaylı sürüm kullanılır.

## 12. AI İşleme Hattı

1. **Girdi doğrulama:** Dosya türü, süre, boyut, bozukluk ve kötü amaçlı içerik kontrolleri.
2. **Yerel güvenli tampon:** Kayıt, küçük ve şifreli parçalara bölünür; her parça için checksum tutulur.
3. **Yükleme ve normalizasyon:** Devam edebilir yükleme; örnekleme hızı/kanal normalizasyonu ve yalnızca gerekli ses çıkarımı.
4. **Ses etkinliği algılama:** Uzun sessizlikler ve konuşma dışı bölümler işaretlenir; ham kayıt değiştirilmez.
5. **Konuşmacı ayrımı:** Segmentler konuşmacı kümelerine atanır. Biyometrik kimlik iddiasında bulunulmaz.
6. **Otomatik konuşma tanıma:** Sözcük/segment zaman damgaları ve güven değerleriyle ham transkript üretilir.
7. **Metin temizleme:** Dolgu sözcükleri ve tekrarlar azaltılır; özel ad, sayı, olumsuzluk ve taahhütleri koruyan kontroller uygulanır.
8. **Yapısal çıkarım:** Özet, önemli not, karar, açık soru, risk, görev, görev sahibi, termin ve başlık adayı üretilir.
9. **Kaynak bağlama:** Her türetilmiş madde ilgili transkript segmentlerine ve zaman damgalarına bağlanır.
10. **Kalite değerlendirmesi:** Ses kalitesi, ASR güveni, kaynak kapsamı ve çıkarım belirsizliği birleştirilir; düşük güvenli sonuçlar kullanıcı incelemesine yönlendirilir.
11. **Güvenlik filtresi:** Prompt injection benzeri toplantı içeriği, sistem talimatı olarak değil işlenecek veri olarak ele alınır; dış eylem tetiklemez.
12. **Kullanıcı onayı:** Başlık ve belirsiz görev alanları onaylanır; e-posta veya entegrasyon işlemi kendiliğinden yapılmaz.
13. **Düzeltme ve yeniden işleme:** Düzeltmeler sürümlenir; kullanıcı isterse etkilenen özet ve görevler yeniden oluşturulur.

### 12.1 AI davranış kuralları

- Kaynakta bulunmayan kişi, tarih, karar veya görev üretilmemelidir.
- Belirsizlik açıkça gösterilmeli; “belirtilmedi” veya “onay gerekli” kullanılmalıdır.
- Temiz transkript, ham transkriptin yerine geçmemeli ve ham kayıt silinmemelidir.
- Özet, azınlık görüşlerini veya anlaşmazlıkları “uzlaşma” gibi göstermemelidir.
- E-posta adresi veya takvim katılımcısı, yalnızca yetkili veri kaynağı ve kullanıcı onayıyla eşleştirilmelidir.
- Model/prompt sürümü ve çıktı üretim zamanı denetlenebilir biçimde tutulmalıdır.

## 13. Bildirim ve E-posta Akışı

### 13.1 Bildirimler

- Kayıt sırasında zorunlu foreground service bildirimi.
- İşleme tamamlandı bildirimi; kilit ekranında hassas içerik varsayılan olarak gösterilmez.
- İşleme başarısız oldu / kullanıcı işlemi gerekiyor bildirimi.
- Onaylanmış görevler için, kullanıcı etkinleştirirse termin hatırlatması.
- Bildirim kategorileri Android ayarlarından ayrı ayrı kapatılabilir.

### 13.2 E-posta

- Varsayılan konu: `[Toplantı Notları] {Onaylı Başlık} — {Tarih}`.
- Gövde sırası: kısa özet, kararlar, görevler, önemli notlar, toplantı bilgileri.
- Transkript ve medya ekleri varsayılan olarak seçili değildir; kullanıcı bilinçli biçimde ekler.
- Gönderim öncesi alıcı, içerik, ek ve boyut doğrulaması yapılır.
- Sağlayıcı sınırı aşılırsa güvenli bağlantı veya dosya hariç tutma önerilir.
- Sistem, kullanıcı onayı olmadan otomatik e-posta göndermez.
- Başarısız gönderimde taslak ve seçimler korunur; idempotency anahtarı çift gönderimi önler.

## 14. İzinler, Gizlilik ve Güvenlik

### 14.1 Android izinleri

- Mikrofon: yalnızca kayıt başlatılırken, bağlam içinde istenir.
- Kamera: yalnızca kamera ile ek oluşturulurken istenir.
- Medya/dosya erişimi: Android Photo Picker ve Storage Access Framework tercih edilir; geniş depolama izninden kaçınılır.
- Bildirim: Android sürümüne uygun biçimde ve işleme/kayıt değeri açıklanarak istenir.
- Takvim: yalnızca entegrasyon kullanıcı tarafından etkinleştirildiğinde, ayrı izinle istenir.

İzin reddedildiğinde uygulama nedenini ve sınırlı alternatifi açıklamalı; kullanıcıyı zorlamamalıdır.

### 14.2 Rıza ve mevzuat

- Kayıt öncesinde katılımcıların bilgilendirilmesi ve gerekli rızanın alınmasının kullanıcının sorumluluğunda olduğu açıkça belirtilir.
- Ürün, mümkünse sesli/görsel kayıt göstergesi ve paylaşılabilir kısa rıza bildirimi sunar.
- KVKK başta olmak üzere hedef pazardaki veri koruma ve iletişim mevzuatı için hukuk incelemesi yapılır.
- Rıza teyidi olay kaydı olarak tutulabilir; katılımcının gerçek rızasının alındığını uygulama tek başına garanti etmez.

### 14.3 Güvenlik kontrolleri

- Aktarımda TLS 1.2+; depolamada güçlü sunucu tarafı şifreleme.
- Yerel geçici dosyalar Android Keystore ile korunan anahtarlarla şifrelenir.
- Anahtarlar veriden ayrı yönetilir ve düzenli döndürülür.
- Hassas verilere en az ayrıcalık ve rol tabanlı erişim.
- Ham kayıtlar ve transkriptler uygulama loglarına, analitik olaylara veya çökme raporlarına yazılmaz.
- İmzalı, kısa ömürlü medya bağlantıları; yetki kontrolü her erişimde tekrarlanır.
- Yüklenen dosyalar tür, boyut ve zararlı içerik açısından doğrulanır.
- Hesap/veri dışa aktarma ve kalıcı silme mekanizmaları bulunur.
- Kullanıcı tarafından seçilebilir saklama süresi; süresi dolan yerel ve bulut kopyalarının silinmesi.
- Yedeklerde silme politikasının uygulanması ve silme SLA’sının açıklanması.
- Güvenlik olayları için audit log, anomali tespiti ve olay müdahale planı.

## 15. Hata Durumları ve Kurtarma

| Durum | Beklenen davranış |
|---|---|
| Mikrofon izni reddedildi | Kayıt başlatılmaz; gerekçe ve ayarlara gitme seçeneği gösterilir. Dosya içe aktarma kullanılabilir kalır. |
| Yetersiz depolama | Kayıt öncesi/takip sırasında uyarı; güvenli durdurma ve eldeki parçaların korunması. |
| Uygulama kapanması/cihaz yeniden başlaması | Tamamlanmış şifreli parçalar korunur; uygulama açılınca kurtarma ve devam seçeneği sunulur. |
| İnternet kesintisi | Kayıt yerel tamponda sürer; yükleme bağlantı gelince kaldığı yerden devam eder. |
| Düşük pil/ısınma | Erken uyarı; medya yakalama veya canlı işleme azaltılabilir, ses kaydı önceliklendirilir. |
| Bozuk/desteklenmeyen dosya | Dosya işlenmez; desteklenen biçim ve düzeltme önerisi gösterilir. |
| Çok uzun kayıt | Sınır önceden gösterilir; güvenli biçimde bitirme veya parçalara ayırma önerilir. |
| Çok düşük ses kalitesi | Düşük güven uyarısı, sorunlu zaman aralıkları ve yeniden yükleme/kayıt önerisi gösterilir. |
| Konuşmacılar ayrılamadı | Tek konuşmacılı transkript sunulur; kullanıcı manuel etiket ekleyebilir. |
| AI özet/görev üretimi başarısız | Transkript erişilebilir kalır; yalnızca başarısız aşama yeniden denenir. |
| E-posta sağlayıcısı hatası | Taslak korunur, hata açıklanır, çift gönderimi önleyerek yeniden deneme sunulur. |
| Ek boyutu sınırı aştı | Sıkıştırma, güvenli bağlantı, seçimi kaldırma veya dışa aktarma seçenekleri sunulur. |
| Zaman dilimi belirsizliği | Özgün ifade korunur; kullanıcıdan termin tarihi/saatini onaylaması istenir. |
| Silme sırasında kısmi hata | Öğeler “silme bekliyor” durumuna alınır; arka planda yeniden denenir ve kullanıcıya sonuç bildirilir. |

## 16. Performans ve Kalite Kriterleri

### 16.1 Kayıt güvenilirliği

- Desteklenen cihazlarda 120 dakikalık kaydın başarıyla tamamlanma oranı: **≥ %99**.
- Kayıt çökmesi sonrası kurtarılabilen ses oranı: **≥ %99,5** (tamamlanmış parça bazında).
- Ses parçası kaybı: normal koşullarda toplam sürenin **< %0,1’i**.
- Kayıt ekranı temel eylem tepki süresi: p95 **< 200 ms**.

### 16.2 İşleme

- İyi bağlantı ve normal servis yükünde 60 dakikalık kayıt için uçtan uca sonuç süresi: p50 **≤ 15 dk**, p95 **≤ 30 dk**.
- 120 dakikalık kayıt desteklenmeli; aşamalar devam edebilir ve idempotent olmalıdır.
- Toplantı listesi ilk anlamlı içerik: sıcak açılışta p95 **≤ 2 sn**, soğuk açılışta p95 **≤ 4 sn**.
- Arama sonucu: yerel/indekslenmiş içerikte p95 **≤ 1 sn**.

### 16.3 AI kalite hedefleri

- Türkçe ASR için temsilî test setinde hedef sözcük hata oranı (WER): sessiz/iyi ses koşulunda **≤ %15**; gerçek ortam kırılımı ayrıca raporlanır.
- Görev tespiti F1: doğrulanmış test setinde **≥ 0,80**.
- Açıkça belirtilen görev sahibi doğruluğu: **≥ %85**; termin tarihi doğruluğu: **≥ %85**.
- Başlık önerisinin kullanıcı tarafından aynen veya küçük düzenlemeyle kabulü: **≥ %75**.
- Kaynak zaman damgasına bağlı AI maddesi oranı: **≥ %95**.
- Kalite metrikleri aksan, cinsiyet, cihaz, gürültü ve konuşma hızı gibi uygun kırılımlarda adalet açısından izlenir.

### 16.4 Erişilebilirlik ve uyumluluk

- WCAG 2.2 AA ilkeleri ve Android erişilebilirlik yönergeleri hedeflenir.
- TalkBack, dinamik yazı boyutu, yeterli kontrast, dokunma hedefleri ve yalnızca renge bağlı olmayan durum göstergeleri desteklenir.
- Desteklenen Android sürümleri teknik fizibiliteyle kesinleştirilecek; MVP hedefi son beş ana Android sürümü olmalıdır.

## 17. Analitik ve Başarı Metrikleri

### 17.1 Kuzey yıldızı metriği

**Haftalık doğrulanmış toplantı sayısı:** En az bir AI çıktısı kullanıcı tarafından görüntülenmiş ve toplantı “inceleme tamamlandı” durumuna getirilmiş toplantılar.

### 17.2 Ürün metrikleri

- Kayıt başlatma → başarıyla işleme dönüşüm oranı.
- İşleme tamamlandı → sonuç görüntülendi dönüşüm oranı.
- Başlık önerisi kabul/düzenleme/reddetme oranı.
- Özet, karar ve görev düzenleme oranı.
- Görevlerin onaylanma, düzenlenme ve silinme oranı.
- Toplantı başına kaynak zaman damgası açma oranı.
- E-posta/dışa aktarma kullanım ve başarı oranı.
- D7/D30 kullanıcı devamlılığı.
- Aylık aktif kullanıcı başına işlenen toplantı ve dakika.
- Kullanıcı bildirimiyle bulunan kritik AI hata oranı.

### 17.3 Güvenilirlik ve güven metrikleri

- Kayıt kaybı ve kurtarma oranı.
- İşleme hata oranı; aşama ve cihaz kırılımı.
- P50/P95 işleme süresi.
- Düşük güvenli segment yüzdesi.
- Veri silme taleplerinin SLA içinde tamamlanma oranı.
- Yetkisiz erişim ve güvenlik olayı sayısı.
- İzin isteme ekranı terk oranı.

### 17.4 Analitik gizliliği

- Analitik olaylarda ham ses, transkript, kişi adı, e-posta, başlık veya not içeriği bulunmaz.
- Yalnızca gerekli olaylar, anonim/pseudonim kimlikler ve kaba kategoriler kullanılır.
- Kullanıcı analitik tercihlerini yönetebilir; mevzuata göre açık rıza uygulanır.

## 18. MVP / P1 / P2 Yol Haritası

### MVP — Güvenilir kayıt ve doğrulanabilir toplantı çıktısı

- Canlı kayıt ve dosya içe aktarma.
- Çevrimdışı şifreli tampon ve devam edebilir yükleme.
- Türkçe, zaman damgalı transkript ve temel konuşmacı ayrımı.
- Ham/temiz transkript ve kullanıcı düzeltmeleri.
- Başlık önerisi ve onay akışı.
- Özet, önemli not, karar, açık soru ve görev çıkarımı.
- Görev sahibi/termin adayı ve kullanıcı onayı.
- Görsel/video ekleri ve not içi referans.
- Tarih bazlı arşiv, temel arama ve etiketler.
- E-posta taslağı ve PDF/DOCX/TXT/JSON/ZIP dışa aktarma.
- Temel güven skorları, güvenlik, saklama ve silme.

### P1 — İş akışı entegrasyonları ve kalite

- Google/cihaz takvimi entegrasyonu ve toplantı eşleştirme.
- Görev/hatırlatıcı entegrasyonları.
- Toplantı şablonları.
- Düzeltmeden sonra kısmi AI yeniden üretimi.
- Gelişmiş kalite açıklamaları ve sorunlu ses bölümü yönlendirmesi.
- Paylaşım bağlantıları, süre sonu ve erişim kontrolü.
- Çoklu dil ve karışık dil desteğinin ilk genişletmesi.

### P2 — Kurumsal ve ileri yetenekler

- Takım çalışma alanları ve rol tabanlı erişim.
- Kurumsal SSO, yönetici saklama politikaları ve denetim dışa aktarımı.
- Gelişmiş toplantı şablonları ve kişiselleştirilebilir analiz.
- CRM/proje yönetimi entegrasyonları.
- Kullanıcı tercihine bağlı canlı transkript ve canlı önemli an işaretleme.
- Web/masaüstü erişimi ve cihazlar arası senkronizasyon.

## 19. Kabul Kriterleri

### 19.1 Kayıt ve kurtarma

- **AC-001:** Mikrofon izni verilmiş bir cihazda kullanıcı kaydı başlatır, 120 dakikaya kadar duraklatıp sürdürebilir ve bitirebilir.
- **AC-002:** Ekran kilitliyken kayıt foreground service ile devam eder ve görünür sistem bildirimi bulunur.
- **AC-003:** Ağ kesildiğinde ses kaydı şifreli yerel tamponda devam eder; ağ geldiğinde yükleme tamamlanmış parçalardan yeniden başlamaz, kaldığı yerden sürer.
- **AC-004:** Uygulama beklenmedik kapanırsa tamamlanan kayıt parçaları yeniden açılışta kurtarılabilir.

### 19.2 Transkript ve AI

- **AC-010:** Tamamlanan toplantıda zaman damgalı ham transkript ve ayrı temiz transkript görüntülenir.
- **AC-011:** Kullanıcı bir segmente dokunarak kaydı ilgili zaman noktasından oynatabilir.
- **AC-012:** Konuşmacı etiketi değiştirildiğinde o konuşmacıya bağlı tüm segmentler tutarlı biçimde güncellenir.
- **AC-013:** Özet, önemli not, karar ve görev maddeleri mümkün olduğunda kaynak zaman damgasına bağlantı içerir.
- **AC-014:** Başlık önerisi, kullanıcı onayı veya düzenlemesi olmadan nihai başlık olarak işaretlenmez.
- **AC-015:** Sahibi/termini belirsiz görevler bilgi uydurmak yerine “Onay gerekli” gösterir.
- **AC-016:** Kullanıcı transkript ve AI çıktılarını düzenleyebilir; değişiklik kaydedilip yeniden açıldığında korunur.

### 19.3 Ekler ve arşiv

- **AC-020:** Kayıt sırasında eklenen görsel/video, özgün dosya olarak saklanır ve eklendiği toplantı zamanıyla not akışında görünür.
- **AC-021:** Toplantı, seçilen tarih ve onaylı başlıkla arşivde görüntülenir.
- **AC-022:** Kullanıcı geçmiş toplantıyı başlık, tarih, etiket veya transkript sözcüğüyle bulabilir.

### 19.4 Paylaşım ve güvenlik

- **AC-030:** E-posta gönderiminden önce kullanıcı alıcıları, içeriği ve ekleri görüp son onay verir.
- **AC-031:** Başarısız e-posta gönderimi taslağı kaybetmez ve yeniden deneme çift gönderim oluşturmaz.
- **AC-032:** Kullanıcı PDF, DOCX, TXT ve JSON çıktı oluşturabilir; seçilen medya dosyalarını ZIP’e ekleyebilir.
- **AC-033:** Mikrofon, kamera, medya ve takvim izinleri yalnızca ilgili özellik kullanılırken bağlam içinde istenir.
- **AC-034:** Toplantı kalıcı silindiğinde ilişkili kayıt, transkript, AI çıktıları, indeks kayıtları ve ekler tanımlı silme SLA’sı içinde kaldırılır.
- **AC-035:** Uygulama logları ve analitik olayları ham ses, transkript veya toplantı başlığı içermez.

### 19.5 Kalite çıkış eşiği

- Kritik veya yüksek öncelikli açık güvenlik açığı bulunmamalıdır.
- Desteklenen cihaz test matrisinde iki saatlik kayıt senaryosu hedef başarı oranını karşılamalıdır.
- ASR ve görev çıkarımı, bölüm 16’daki doğrulanmış test seti hedeflerini karşılamalı veya sapma ürün/hukuk/paydaş onayıyla belgelenmelidir.
- Erişilebilirlik, silme, izin reddi ve çevrimdışı kurtarma senaryoları yayın öncesi testlerden geçmelidir.

## 20. Açık Sorular

1. İşleme varsayılan olarak bulutta mı, cihazda mı, yoksa hibrit mi yapılacak?
2. MVP’de desteklenecek minimum Android sürümü ve cihaz RAM/depolama alt sınırı nedir?
3. Ham kayıtların varsayılan saklama süresi ne olacak; kullanıcı transkript tamamlanınca otomatik silmeyi seçebilecek mi?
4. Ücretsiz/ücretli planlarda aylık dakika, dosya boyutu ve saklama sınırları ne olacak?
5. E-posta gönderimi sistem sağlayıcısıyla mı, Android paylaşım ekranıyla mı, yoksa bağlı kullanıcı hesabıyla mı yapılacak?
6. Video içe aktarmada yalnızca ses mi analiz edilecek; kare/OCR analizi hangi sürümde değerlendirilecek?
7. Konuşmacı ayrımı için kabul edilen hata eşiği ve maksimum konuşmacı sayısı nedir?
8. Takvim ve görev entegrasyonlarında ilk hedef sağlayıcılar hangileridir?
9. Kurumsal müşteriler için veri yerleşimi ve özel anahtar yönetimi gerekecek mi?
10. Kullanıcı düzeltmeleri yalnızca kendi çıktısını mı iyileştirecek, yoksa açık rıza ile model geliştirmede kullanılabilecek mi?
11. Paylaşılan ekler için güvenli bağlantı mı yoksa doğrudan e-posta eki mi varsayılan olmalı?
12. Kullanıcının katılımcılara gösterebileceği standart rıza ekranı veya sesli anons özelliği gerekli mi?

## 21. Riskler ve Azaltımlar

| Risk | Etki | Azaltım |
|---|---|---|
| Kayıt sırasında veri kaybı | Çok yüksek | Foreground service, parçalı şifreli yazma, checksum, kurtarma testi, depolama/pil uyarısı. |
| Transkript hatası | Yüksek | Güven skoru, zaman damgasından dinleme, ham metni koruma, kullanıcı düzeltmesi, çeşitli Türkçe test setleri. |
| Yanlış veya uydurulmuş görev/karar | Yüksek | Kaynak zorunluluğu, belirsizlik etiketi, insan onayı, bilgi uydurmama kuralları, kalite değerlendirmesi. |
| Yanlış konuşmacı ataması | Orta/Yüksek | “Konuşmacı N” varsayılanı, biyometrik kimlik iddiasından kaçınma, toplu yeniden adlandırma ve manuel düzeltme. |
| İzinsiz kayıt / mevzuat ihlali | Çok yüksek | Kayıt öncesi açık uyarı ve rıza teyidi, görünür kayıt göstergesi, hukuk incelemesi, bölgesel politika. |
| Hassas veri sızıntısı | Çok yüksek | Uçtan uca güvenlik tasarımı, şifreleme, en az ayrıcalık, kısa ömürlü bağlantı, log redaksiyonu, sızma testi. |
| Uzun kayıtların maliyeti | Yüksek | Parçalı işleme, sessizlik optimizasyonu, kota/fiyat şeffaflığı, katmanlı saklama, maliyet alarmları. |
| Pil tüketimi ve ısınma | Orta | Verimli codec, örnekleme ayarı, canlı AI yerine sunucu/sonradan işleme, cihaz matrisi testleri. |
| Ağ kesintisi | Yüksek | Çevrimdışı tampon, devam edebilir yükleme, Wi-Fi tercihi ve kullanıcıya açık durum. |
| E-posta ile yanlış kişiye veri gönderimi | Yüksek | Son onay, alıcı uyarısı, hassas eklerin varsayılan kapalı olması, iptal penceresi mümkünse. |
| Dış sağlayıcı bağımlılığı | Orta/Yüksek | Sağlayıcı soyutlama katmanı, veri taşınabilirliği, kuyruk/idempotency, SLA ve yedek sağlayıcı planı. |
| Dil/aksan bazlı kalite farkı | Yüksek | Temsilî veri setleri, kırılımlı kalite metrikleri, kullanıcı geri bildirimi ve model yönlendirme. |
| Silmenin tüm kopyalara uygulanmaması | Çok yüksek | Veri envanteri, silme orkestrasyonu, yedek yaşam döngüsü, doğrulama işi ve kullanıcıya durum bildirimi. |
| Toplantı içeriğinde kötü amaçlı talimat | Yüksek | İçeriği güvenilmeyen veri olarak izole etme; araç/e-posta/takvim eylemlerini yalnızca kullanıcı onayıyla yürütme. |

## 22. Yayına Hazırlık Kontrol Listesi

- İki saatlik kayıt, ekran kilidi, ağ kesintisi, düşük depolama ve çökme kurtarma testleri tamamlandı.
- Türkçe kalite test seti ve kabul eşikleri ürün/AI ekiplerince onaylandı.
- KVKK, kayıt rızası, gizlilik politikası ve veri işleyen sözleşmeleri hukuk tarafından incelendi.
- Tehdit modellemesi, sızma testi, bağımlılık taraması ve erişim denetimi tamamlandı.
- Android izin metinleri, foreground service davranışı ve mağaza veri güvenliği beyanı doğrulandı.
- Erişilebilirlik ve desteklenen cihaz matrisi testleri tamamlandı.
- Silme, dışa aktarma, saklama süresi ve hesap kapatma uçtan uca doğrulandı.
- İzleme panoları; kayıt kaybı, işleme hatası, gecikme, maliyet ve güvenlik uyarıları için hazırlandı.
- Destek ekibi için kayıt kaybı, yanlış çıktı, faturalandırma ve veri silme prosedürleri hazırlandı.


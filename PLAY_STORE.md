# KartCep — Google Play yayın rehberi

Kodda gereken hazırlıklar tamamlandı. Aşağıda **kodda ne yapıldığı** ve
**senin yapman gerekenler** ayrı ayrı listelenmiştir.

---

## 1. Kodda tamamlananlar ✅

| Gereksinim | Durum |
|---|---|
| Hedef API düzeyi (Play, güncel API'yi zorunlu tutar) | `compileSdk`/`targetSdk` = **35** |
| Mağaza paketi biçimi (Play APK kabul etmez) | CI her gönderimde **AAB** üretir |
| Kod/kaynak küçültme | Release'de `minifyEnabled` + `shrinkResources` **açık** |
| R8 kuralları (ML Kit, model sınıfları) | `app/proguard-rules.pro` yazıldı |
| Gereksiz hassas izin | `READ_CONTACTS` **kaldırıldı** (yalnızca `WRITE_CONTACTS`) |
| Yedekleme kuralları | `backup_rules.xml` + `data_extraction_rules.xml` |
| İmzalama sırlarının depoya girmemesi | `keystore.properties` ve `*.jks` **.gitignore**'da |
| Gizlilik politikası metni | `PRIVACY.md` |

---

## 2. Senin yapman gerekenler

### 2.1 Yükleme anahtarı (upload key) oluştur

Depodaki `kartvizit.jks` **yalnızca doğrudan dağıtılan APK içindir**; Play'e
onunla yükleme yapma. Yeni bir yükleme anahtarı üret:

```bash
keytool -genkey -v -keystore upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Proje kökünde `keystore.properties` oluştur (bu dosya .gitignore'da):

```properties
storeFile=/tam/yol/upload-keystore.jks
storePassword=•••
keyAlias=upload
keyPassword=•••
```

> **Anahtarı ve parolayı kaybetme.** Play'de "Uygulama imzalama" (App Signing)
> açıksa yükleme anahtarını sıfırlatabilirsin; yine de yedeğini güvenli bir
> yerde sakla.

### 2.2 Mağaza paketini üret

```bash
./gradlew :app:bundleRelease
# Çıktı: app/build/outputs/bundle/release/app-release.aab
```

`keystore.properties` varsa paket imzalanmış çıkar. (CI'daki AAB imzasızdır;
yalnızca derlemenin bozulmadığını doğrulamak içindir.)

### 2.3 Gizlilik politikasını yayına al

Play, kişisel veri işleyen uygulamalarda **herkese açık bir URL** ister.
`PRIVACY.md` içeriğini bir yere yayınla (GitHub Pages en kolayı) ve URL'yi
Play Console → *Uygulama içeriği* → *Gizlilik politikası* alanına gir.

### 2.4 Veri güvenliği (Data safety) formu

Play Console → *Uygulama içeriği* → *Veri güvenliği*. Doğru cevaplar:

- **Veri toplanıyor mu?** Hayır — veriler cihazdan çıkmıyor, geliştiriciye
  gönderilmiyor.
- **Veri paylaşılıyor mu?** Hayır.
- **Veriler şifreleniyor mu (aktarımda)?** Uygulama sunucuya veri göndermiyor.
- **Kullanıcı silme talep edebilir mi?** Evet — uygulama içinden silinebilir.
- Kişiler iznini beyan ederken: kişi bilgisi **yalnızca cihazda** işleniyor,
  kullanıcının isteğiyle telefon rehberine yazılıyor.

### 2.5 Mağaza listeleme görselleri

Bunlar APK'nın içinde değildir, Play Console'a ayrıca yüklenir:

| Öğe | Gereken |
|---|---|
| Uygulama simgesi | 512 × 512 PNG (32-bit, alfa kanalsız) |
| Öne çıkan görsel | 1024 × 500 PNG/JPG |
| Telefon ekran görüntüsü | En az 2 adet (ör. 1080 × 1920) |
| Kısa açıklama | En fazla 80 karakter |
| Tam açıklama | En fazla 4000 karakter |

Hazır metin önerisi:

- **Kısa açıklama:** `Kartvizitleri tarayın, kişilere ve Excel'e saniyeler içinde aktarın.`
- **Tam açıklama:** Uygulama tamamen çevrimdışı çalışır; kartvizit metni
  cihazda tanınır. Ad, unvan, firma, cep/iş/faks/ev telefonları, e-posta, web
  sitesi ve adres otomatik ayrıştırılır. Karekod okunur, tek fotoğraftaki
  birden fazla kartvizit ayrılır. Kayıtları profillere (İş, Özel, istediğiniz
  kadar yenisi) ayırın, arayın, telefon rehberine ekleyin, Excel (.xlsx) veya
  vCard (.vcf) olarak dışa aktarın ve seçtiğiniz bulut klasörüne yedekleyin.

### 2.6 Sürüm numarası

Her yeni yüklemede `app/build.gradle.kts` içindeki `versionCode` **artmalıdır**
(`versionName` serbesttir). Play aynı `versionCode` ile ikinci kez yükleme
kabul etmez.

### 2.7 Yayın öncesi son kontrol

```bash
./gradlew :app:lintRelease   # engelleyici uyarı var mı
./gradlew :app:testDebugUnitTest
```

Play Console'da önce **kapalı test (internal testing)** kanalına yükleyip
telefonda dene; küçültülmüş (R8) sürümde tarama akışını mutlaka bir kez test
et, sonra üretime al.

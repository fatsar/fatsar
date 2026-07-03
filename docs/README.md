# Kartvizit Tarayıcı 📇

Kartvizitleri tarayıp **telefon rehberine ekleyen** ve tüm kayıtları **Excel (.xlsx) dosyası olarak Gmail ile gönderebilen** Android uygulaması.

Tüm işlemler **cihaz üzerinde** çalışır: metin tanıma (OCR) için Google ML Kit'in cihaz içi modeli uygulamayla birlikte paketlenir. **İnternet bağlantısı veya herhangi bir yapay zekâ / bulut servisi gerekmez** — yalnızca Excel dosyasını e-postayla gönderirken Gmail'in kendisi internet kullanır.

## Özellikler

- 📷 **Kamerayla tarama** – kartvizitin fotoğrafını çekin, bilgiler otomatik çıkarılsın.
- 🖼️ **Galeriden seçme** – telefondaki hazır fotoğrafları (ör. bilgisayarda taranıp telefona aktarılmış görselleri) seçin.
- 📤 **Paylaşarak açma** – başka uygulamalardan (WhatsApp, Drive, Dosyalar…) herhangi bir görseli bu uygulamaya "Paylaş" ile gönderin.
- 🔍 **Cihaz içi OCR + akıllı alan ayrıştırma** – ad, unvan, şirket, telefon(lar), e-posta, web sitesi ve adres otomatik tanınır (Türkçe ve İngilizce kartvizitlere göre ayarlandı). Kaydetmeden önce tüm alanları düzenleyebilirsiniz.
- ✨ **Otomatik metin düzeltme** – fazla boşluklar temizlenir; TAMAMEN BÜYÜK yazılmış ad/unvan/şirket/adres Türkçe kurallarına göre düzgün büyük-küçük harfe çevrilir ("AHMET YILMAZ" → "Ahmet Yılmaz", kısaltmalar korunur: "A.Ş.", "LTD"). Kişi adı, e-posta adresiyle eşleştirilerek firma adının isim sanılması önlenir.
- 👤 **Telefon rehberine ekleme** – kişinin **adı ve soyadı ayrı alanlar** olarak rehbere yazılır; şirket, unvan, numara, e-posta ve adres de eklenir.
- 🗂️ **Kategoriler** – kayıtları isteğe bağlı kategorilere ayırın ("Müşteriler", "Tedarikçiler"…); ana ekranda kategoriye göre filtreleyin. Kategori Excel'de ayrı sütun, vCard'da CATEGORIES alanı olarak dışa aktarılır.
- 📇 **Rehber dosyası (.vcf) paylaşımı** – tüm kayıtları standart vCard dosyası olarak WhatsApp/e-posta/Bluetooth ile paylaşın; alıcı dosyaya dokunarak kişileri kendi telefon rehberine aktarır.
- 📊 **Excel dosyası** – her kayıtta `kartvizitler.xlsx` otomatik güncellenir (harici kütüphane olmadan üretilen standart Office Open XML; Excel, Google E-Tablolar ve LibreOffice ile açılır).
- ✉️ **Gmail ile gönderme** – menüden tek dokunuşla Excel dosyası, telefonunuzdaki Gmail hesabı üzerinden istediğiniz adrese (ör. kendi adresinize) eklenti olarak gönderilir. Dosya ayrıca İndirilenler klasörüne de kaydedilebilir.

> **Not:** Google Drive/E-Tablolar'a otomatik arka plan senkronizasyonu bilinçli olarak eklenmedi; bunun için Google Cloud Console'da uygulamaya özel OAuth istemcisi oluşturmak gerekir. Mevcut tasarımda dosya, telefondaki Gmail hesabınız üzerinden gönderilir — ek kurulum gerektirmez ve uygulama Claude ya da başka bir servise bağımlı değildir.

## Kullanım

1. **Kamerayla Tara** veya **Fotoğraf Seç** ile kartvizit görselini alın.
2. Tanınan bilgileri kontrol edin/düzeltin, *"Telefon rehberine de ekle"* işaretliyken **Kaydet**'e basın.
3. Menüden (⋮) **Excel'i Gmail ile gönder**'e dokunun. İsterseniz önce **Alıcı e-posta adresi**'ni bir kez ayarlayın; Gmail o adrese hazır taslak açar.

## Gerekli izinler

| İzin | Neden |
|---|---|
| Kişiler (okuma/yazma) | Taranan kişiyi telefon rehberine eklemek için |

Kamera izni gerekmez (sistem kamera uygulaması kullanılır); fotoğraflara erişim Android'in fotoğraf seçicisiyle sağlanır, depolama izni gerektirmez.

## APK'yı edinme

Her push'ta GitHub Actions APK'yı derler: depo **Actions** sekmesi → son "Android CI" çalışması → **Artifacts** → `kartvizit-tarayici-debug-apk`. İndirdiğiniz `app-debug.apk` dosyasını telefona kopyalayıp açarak kurabilirsiniz (bilinmeyen kaynaklara izin vermeniz istenebilir).

## Kendi makinenizde derleme

Gereksinimler: JDK 17+ ve Android SDK (Platform 34) **veya** sadece Android Studio.

```bash
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # Birim testleri (OCR ayrıştırıcı + Excel üretici)
```

- **minSdk:** 26 (Android 8.0) • **targetSdk:** 34
- Teknolojiler: Kotlin, Material 3, ML Kit Text Recognition (cihaz içi, paketli model), ViewBinding

## Proje yapısı

```
app/src/main/java/com/fatsar/kartvizit/
├── MainActivity.kt              # Liste, tarama girişleri, Excel/Gmail menüsü
├── EditContactActivity.kt       # OCR sonucunu düzenleme ve kaydetme
├── ocr/CardTextParser.kt        # Ham OCR metnini alanlara ayıran çözümleyici
├── ocr/TextNormalizer.kt        # Boşluk/büyük-küçük harf düzeltme, ad-soyad ayırma
├── export/XlsxWriter.kt         # Sıfır bağımlılıkla .xlsx üretimi
├── export/VcfWriter.kt          # vCard 3.0 (.vcf) rehber dosyası üretimi
├── export/ExportManager.kt      # Excel/vCard oluşturma, Gmail, paylaşım, İndirilenler
├── contacts/DeviceContacts.kt   # Rehbere kişi ekleme
├── data/ContactRepository.kt    # Cihazda JSON tabanlı kayıt deposu
└── ui/ContactsAdapter.kt        # Kayıt listesi
```

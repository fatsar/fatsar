# Notlar ✍️

Kenar çubuğunda liste, ana bölmede **markdown** düzenleyici; anında arama, önizleme geçişi ve **Samsung S Pen / diğer kalemler** için basınç duyarlı yazı desteği olan Android not uygulaması.

Her şey **cihazda** kalır: hesap, bulut ya da internet gerekmez. Notlar uygulamanın kendi klasöründeki `notlar.json` dosyasında saklanır.

## Özellikler

- 🗂 **Kenar çubuğu listesi** – notlar son düzenlenme sırasına göre; sabitlenenler üstte. Her satırda başlık, ilk satırdan özet ve “Düzenlendi: az önce / 5 dk önce / dün 21:40 / 12 Mayıs 2026” damgası.
- 🔎 **Anında arama** – her tuş vuruşunda süzülür. Türkçe'ye uygun: “cizim” yazınca “Çizim” bulunur (İ/ı ve ş/ğ/ü farkları göz ardı edilir), boşluklu sorguda tüm sözcüklerin geçmesi aranır, eşleşmeler listede vurgulanır.
- 📝 **Markdown düzenleme** – başlıklar, kalın/eğik/üstü çizili/`kod`/==vurgu==, madde ve numaralı listeler, görev kutuları (`- [x]`), alıntı, kod bloğu, yatay çizgi, bağlantılar. Enter'a basınca liste kendiliğinden sürer, boş maddede biter.
- 👁 **Önizleme geçişi** – aynı bölmede ham metin ile render edilmiş markdown arasında geçiş (araç çubuğu ya da **Ctrl+P**).
- 💾 **Yerel kalıcılık** – yazmayı bıraktıktan 600 ms sonra ve uygulama arka plana alındığında otomatik kaydedilir. Yazma atomiktir (geçici dosya + yeniden adlandırma) ve bir önceki sürüm `.bak` olarak tutulur; dosya bozulursa yedekten dönülür.
- ➕🗑 **Oluşturma / silme** – “Yeni not” düğmesi ya da **Ctrl+N**; silmede onay ve ardından **geri alma** bildirimi.
- ⌨️ **Klavye dostu** – aşağıdaki kısayollar, listede ok tuşlarıyla gezinme ve Enter ile açma, Esc ile aramadan/listeye dönüş. Bluetooth klavye ve Samsung DeX ile rahat kullanılır.
- ✒️ **Kalem desteği** – aşağıda ayrıntılı.
- 🌗 **Aydınlık/karanlık tema** ve Material You renkleri; katlanabilir/geniş ekranlarda iki bölme yan yana, telefonda tek bölme (kaydırmalı) düzen.
- 📤 **Paylaşma** – notu düz metin olarak paylaşın; başka uygulamalardan paylaşılan metin de yeni nota dönüşür.

## Kalem (S Pen ve diğer stylus'lar)

Galaxy S26 Ultra gibi S Pen'li telefonlarda ve kalem destekli diğer cihazlarda:

| Özellik | Nasıl çalışır |
|---|---|
| **Doğrudan el yazısı** | Metin alanına kalemle yazın; Android 14+ el yazısını metne çevirir (`autoHandwritingEnabled`). |
| **Önizlemeden yazıya geçiş** | Önizlemedeyken kalemle dokunmak düzenleyiciyi açar ve el yazısına hazırlar (Android 14+ “handwriting delegation”, eski sürümlerde dokunma algılamasıyla). |
| **Basınç ve eğim** | Çizim katmanında bastırınca kalın, hafif dokununca ince çizer; kalemin eğimi ucu genişletir. |
| **Yan tuş = silgi** | S Pen'in yan düğmesi basılıyken (ya da ucu ters çevrilen kalemlerde) silgi devreye girer; ayrıca silgi ucu düğmesi vardır. |
| **Avuç içi reddi** | Kalem bir kez algılandıktan sonra parmak dokunuşları çizgi üretmez — el ekrana dayanabilir. Anahtarla kapatılabilir. |
| **Havada gezinme** | Kalem ekrana değmeden yaklaştığında ucun nereye geleceğini gösteren halka çizilir. |
| **Fosforlu kalem** | Yarı saydam, kalın uç; altındaki yazı okunmaya devam eder. |
| **Kaybolmayan çizim** | Çizgiler vektör olarak (JSON) saklanır — sonradan açılıp geri alınabilir; önizlemede PNG olarak görünür. |

Çizim, notun **kalem katmanı** olarak saklanır: araç çubuğundaki ✎ **Kalemle yaz** ile açılır, önizlemede notun altında görünür, listede küçük bir kalem rozetiyle belirtilir.

## Klavye kısayolları

| Kısayol | İşlev |
|---|---|
| `Ctrl + N` | Yeni not |
| `Ctrl + F` | Aramaya git |
| `Ctrl + P` | Önizleme ↔ düzenleme |
| `Ctrl + E` | Düzenleyiciye odaklan |
| `Ctrl + B` / `Ctrl + I` | Kalın / eğik |
| `Ctrl + K` | Bağlantı ekle |
| `Ctrl + Shift + K` | Görev kutusu |
| `Ctrl + L` | Madde işareti |
| `Ctrl + D` | Notu sil |
| `Ctrl + S` | Hemen kaydet |
| `Esc` | Aramayı temizle / listeye dön |
| `↑` `↓` `Enter` | Listede gezin ve aç |
| `Ctrl + Z` / `Ctrl + Shift + Z` | Çizimde geri / ileri al |

## Veriler nerede duruyor?

| Dosya | İçerik |
|---|---|
| `files/notlar.json` | Tüm notlar (kimlik, gövde, oluşturma/düzenleme zamanı, sabitleme, çizim adı) |
| `files/notlar.json.bak` | Bir önceki sürüm (kurtarma için) |
| `files/cizimler/<ad>.json` | Kalem çizgileri (vektör) |
| `files/cizimler/<ad>.png` | Çizimin önizlemede gösterilen görüntüsü |

Notlar cihaz yedeklemesine dahildir (`backup_rules.xml`); başka hiçbir yere gönderilmez. Uygulama **hiçbir izin istemez** ve internet erişimi yoktur.

## APK'yı edinme

Her push'ta GitHub Actions APK'yı derler: depo **Actions** sekmesi → son “Android CI” çalışması → **Artifacts** → `notlar-debug-apk`. İndirdiğiniz `notlar-debug.apk` dosyasını telefona kopyalayıp açarak kurabilirsiniz.

## Kendi makinenizde derleme

```bash
./gradlew :notlar:assembleDebug      # APK: notlar/build/outputs/apk/debug/
./gradlew :notlar:testDebugUnitTest  # birim testleri
```

Gereksinimler: JDK 17, Android SDK 34. En düşük Android sürümü 8.0 (API 26); el yazısı özellikleri Android 14+ ister, diğer her şey eski sürümlerde de çalışır.

## Mimari

```
notlar/src/main/java/com/fatsar/notlar/
├── MainActivity.kt          # liste + arama + düzenleyici/önizleme, kısayollar
├── NotlarApp.kt             # Material You renkleri
├── model/Note.kt            # not modeli, başlık/özet türetme
├── data/
│   ├── NoteStore.kt         # dosya okuma/yazma (atomik + yedek)
│   ├── NoteSerializer.kt    # notlar ⇄ JSON
│   └── StrokeSerializer.kt  # çizgiler ⇄ JSON
├── markdown/
│   ├── Markdown.kt          # saf Kotlin ayrıştırıcı (blok + satır içi)
│   ├── MarkdownEditing.kt   # biçim kısayolları, liste devamı
│   └── MarkdownRenderer.kt  # bloklar → Spannable (önizleme)
├── search/NoteSearch.kt     # Türkçe duyarlı süzme, sıralama, vurgulama
├── pen/Stroke.kt            # kalem çizgisi modeli (basınç, eğim)
├── ui/
│   ├── NoteListAdapter.kt   # kenar çubuğu listesi
│   ├── PenCanvasView.kt     # kalem tuvali (avuç reddi, hover, silgi)
│   └── SketchActivity.kt    # çizim ekranı
└── util/                    # göreli zaman damgaları
```

Markdown ayrıştırma, arama, JSON ve zaman mantığı Android'den bağımsız tutuldu; bu yüzden 54 birim testiyle doğrudan sınanabiliyor (`./gradlew :notlar:testDebugUnitTest`).

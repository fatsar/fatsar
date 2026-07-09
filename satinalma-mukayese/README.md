# Satınalma Teklif Toplama ve Mukayese Programı

Planlamadan gelen satınalma taleplerini alır, **her stok kodu için onaylı
tedarikçilere Outlook üzerinden teklif e-postası gönderir**, gelen cevapları
yine Outlook'tan otomatik toplar ve tek Excel dosyasında **mukayese tablosu**
oluşturur.

## Nasıl çalışır?

```
Talep Excel'i ──┐
                ├─► [2. GÖNDER] ─► Her tedarikçiye e-posta + Excel teklif formu (Outlook)
Tedarikçi     ──┘
listesi Excel'i
                     Tedarikçiler formu doldurup yanıtlar
                                   │
                ┌──────────────────┘
                ▼
        [3. TOPLA]  Outlook gelen kutusundan cevapları/formları indirir
                ▼
        [4. RAPOR]  mukayese_TEKLIF-....xlsx  (en ucuz teklif yeşil işaretli)
```

Her gönderim turu `TEKLIF-20260709-AB12` gibi benzersiz bir **Teklif No** alır.
Bu numara e-posta konusuna eklenir; tedarikçiler konuyu değiştirmeden
yanıtladığı sürece cevaplar otomatik eşleştirilir.

## Kurulum (Windows + Outlook)

1. [Python](https://www.python.org/downloads/) kurun (kurulumda **"Add Python
   to PATH"** kutusunu işaretleyin).
2. Bu klasörü bilgisayarınıza kopyalayın.
3. Komut satırında (cmd) bu klasöre gelip şunu çalıştırın:

   ```
   pip install -r requirements.txt
   ```

Outlook masaüstü uygulamasının kurulu ve hesabınızın tanımlı olması yeterlidir;
ayrıca şifre/sunucu ayarı gerekmez.

## Kullanım

`baslat.bat` dosyasına çift tıklayın veya komut satırından:

```
python mukayese.py
```

Etkileşimli menü açılır. Komut satırından da kullanılabilir:

```
python mukayese.py ornek
python mukayese.py gonder --talep ornek_talep.xlsx --tedarikciler ornek_tedarikciler.xlsx --taslak
python mukayese.py topla
python mukayese.py rapor
```

### 1) Örnek şablonları oluşturun

`python mukayese.py ornek` iki dosya üretir:

- **ornek_talep.xlsx** — planlamadan gelen talep bu düzende girilir:

  | Kodu | İsmi | Miktar | Birim | Teslim tarihi |
  |------|------|--------|-------|---------------|
  | HMPT000220 | DOTP | 20.000,00 | KG. | 17.06.2026 |

- **ornek_tedarikciler.xlsx** — her stok kodu için onaylı tedarikçiler.
  Aynı stok için birden fazla tedarikçi = birden fazla satır:

  | Stok Kodu | Stok Adı | Tedarikçi Adı | E-posta | Yetkili |
  |-----------|----------|---------------|---------|---------|
  | HMPT000220 | DOTP | ABC Kimya A.Ş. | satis@abckimya.com | Ahmet Yılmaz |
  | HMPT000220 | DOTP | Delta Plastifiyan Ltd. | teklif@deltaplast.com | |

  Örnek dosyadaki firmalar hayalidir; kendi onaylı tedarikçi listenizle
  değiştirin. Bu dosyayı bir kez hazırlayıp her turda kullanabilirsiniz.

### 2) Teklif e-postalarını gönderin

```
python mukayese.py gonder --talep talep.xlsx --tedarikciler tedarikciler.xlsx --taslak
```

- Aynı tedarikçi birden çok kalemde onaylıysa **tek e-postada birleştirilir**.
- Her e-postaya, tedarikçinin dolduracağı sarı alanlı bir **Excel teklif
  formu** eklenir (birim fiyat, para birimi, termin, ödeme vadesi, not).
- `--taslak` ile e-postalar gönderilmez, Outlook **Taslaklar** klasörüne
  kaydedilir; kontrol edip elle gönderirsiniz. Doğrudan göndermek için
  `--taslak` yazmayın.
- `--son-cevap 13.06.2026` ile tedarikçiye son cevap tarihi bildirilir.
- Tedarikçisi tanımlı olmayan stok kodları ekranda uyarı olarak listelenir.

### 3) Cevapları toplayın

```
python mukayese.py topla
```

Gelen kutusunda konusunda Teklif No geçen e-postaları bulur, ekli Excel
formlarını `gelen_teklifler/TEKLIF-…/` klasörüne kaydeder. Eki olmayan
cevapların gövde metni `.txt` olarak saklanır (fiyatı elle forma işlersiniz).

### 4) Mukayese raporunu alın

```
python mukayese.py rapor
```

`mukayese_TEKLIF-….xlsx` oluşturulur:

- Satırlar: talep kalemleri — Sütunlar: tedarikçiler
- Her tedarikçi için Birim Fiyat / Para Birimi / Tutar / Termin / Vade
- Kalem bazında **en düşük birim fiyat yeşil** işaretlenir
- Altta tedarikçi bazında **toplam tutar** (eksik kalem verenler not düşülür)

Tedarikçi formu e-posta yerine elden/WhatsApp'tan geldiyse dosyayı
`gelen_teklifler/TEKLIF-…/` klasörüne kopyalamanız yeterlidir; rapor onu da
dahil eder.

## Klasörler

| Klasör | İçerik |
|--------|--------|
| `kayitlar/` | Her teklif turunun kaydı (kalemler, tedarikçiler) |
| `gonderilen_formlar/` | Tedarikçilere gönderilen boş formlar |
| `gelen_teklifler/` | Outlook'tan indirilen doldurulmuş formlar |

## Notlar

- Program Türkçe sayı biçimini (`20.000,00`) ve Excel tarihlerini tanır.
- Sütun başlıkları esnektir: "Kodu/Stok Kodu", "İsmi/Stok Adı",
  "Teslim tarihi/Termin" gibi varyasyonlar kabul edilir.
- Outlook gönderimi yalnızca Windows'ta çalışır (pywin32 + Outlook COM);
  form oluşturma ve rapor kısmı her sistemde çalışır.

# KartCep — Gizlilik Politikası

**Son güncelleme:** 2026-07-25

KartCep, kartvizitleri telefonunuzun kamerasıyla tarayıp kişi bilgilerine
dönüştüren bir uygulamadır. Bu politika, uygulamanın hangi verileri işlediğini
ve bu verilerin nerede tutulduğunu açıklar.

## Kısaca

- Kartvizit tanıma **tamamen cihazınızda** yapılır. Taradığınız görüntüler ya da
  okunan bilgiler bize veya herhangi bir sunucuya gönderilmez.
- Uygulamanın kendi sunucusu yoktur. Geliştirici, verilerinize erişemez.
- Veriler yalnızca **siz** bir işlem başlattığınızda cihazdan çıkar (e-posta ile
  gönderme, dosya paylaşma veya seçtiğiniz bulut klasörüne yedekleme).

## İşlenen veriler

| Veri | Amaç | Nerede saklanır |
|---|---|---|
| Kartvizitten okunan ad, unvan, firma, telefon, e-posta, adres, web sitesi, notlar | Kişi kaydı oluşturmak | Yalnızca cihazınızdaki uygulama deposunda (`contacts.json`) |
| Seçtiğiniz kartvizit fotoğrafı / çektiğiniz fotoğraf | Metni tanımak | Geçici olarak işlenir; kalıcı olarak saklanmaz |
| Ayarlarınız (tema, profiller, sıralama, alıcı e-posta adresi) | Tercihlerinizi hatırlamak | Cihazınızdaki uygulama ayarlarında |

## İzinler

- **Kişiler (yazma) — `WRITE_CONTACTS`:** Yalnızca siz "Rehbere Ekle"
  dediğinizde, seçtiğiniz kaydı telefon rehberinize eklemek için kullanılır.
  Uygulama rehberinizi **okumaz**; okuma izni istemez.
- **Kamera:** Fotoğraf çekmek için cihazın kamera uygulaması kullanılır;
  uygulama ayrı bir kamera izni istemez.

## Verinin cihazdan çıktığı durumlar

Bunların hepsi sizin başlattığınız işlemlerdir:

- **Excel/vCard gönderme:** Seçtiğiniz e-posta ya da paylaşım uygulamasına
  dosya iletilir. Dosyanın nereye gittiğini siz belirlersiniz.
- **Buluta yedekleme:** Seçtiğiniz klasöre (Google Drive, OneDrive, Dropbox ya
  da telefon belleği) bir `.zip` yedek yazılır. Klasörü siz seçersiniz; uygulama
  başka bir konuma erişmez.
- **Android sistem yedeklemesi:** Cihaz yedeklemeniz açıksa kayıtlarınız
  Google hesabınızın yedeğine dahil olabilir. Bu, Android'in kendi özelliğidir.

## Çocukların gizliliği

Uygulama çocuklara yönelik değildir ve çocuklardan bilerek veri toplamaz.

## Verilerinizi silme

Kayıtları uygulama içinden tek tek silebilirsiniz. Uygulamayı kaldırmak ya da
Ayarlar → Uygulamalar → KartCep → Depolama → Verileri temizle demek, cihazdaki
tüm kayıtları kalıcı olarak siler. Daha önce dışa aktardığınız ya da
yedeklediğiniz dosyalar sizin denetiminizdedir.

## Değişiklikler

Bu politika güncellenirse bu sayfadaki tarih değiştirilir.

## İletişim

Sorularınız için: **fatsar@gmail.com**

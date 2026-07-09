# Saatlik Haber Özeti Uygulaması

Bu proje, farklı haber kaynaklarından RSS akışlarını toplayarak her saat otomatik olarak özetleyen basit bir Flask uygulamasıdır. Uygulama, özetleri web arayüzünde listeler ve gerekirse manuel olarak da yenilenebilir.

## Özellikler

- BBC, Reuters ve Al Jazeera RSS akışlarından haber başlıkları ve özetleri.
- Saatlik otomatik yenileme (APScheduler ile arka planda).
- Web arayüzünden anlık yenileme butonu ve otomatik yenilemeyi açıp kapama.
- JSON formatında haberleri sunan `/api/articles` uç noktası.

## Kurulum

1. Python 3.11 veya üstü bir sürüm kurulu olmalıdır.
2. Gerekli bağımlılıkları yükleyin:

   ```bash
   pip install -r requirements.txt
   ```

3. Uygulamayı başlatın:

   ```bash
   flask --app app run
   ```

   veya doğrudan Python ile:

   ```bash
   python app.py
   ```

4. Tarayıcınızdan `http://127.0.0.1:5000` adresine giderek uygulamayı görüntüleyebilirsiniz.

## Notlar

- İlk açılışta veriler otomatik olarak yüklenir.
- Haber özetleri, RSS akışlarının sağladığı özet/description alanından alınır ve 200 karaktere kadar kısaltılır.
- Yeni kaynaklar eklemek için `app.py` içindeki `NEWS_SOURCES` sözlüğüne yeni bir başlık ve RSS URL'si ekleyebilirsiniz.

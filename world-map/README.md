# Dünya Metrik Atlası

Ülkeleri seçilen bir metriğe göre renklendiren, tek dosyalık etkileşimli dünya haritası.
`index.html` dosyasını doğrudan tarayıcıda açmak yeterlidir; kurulum, sunucu ya da
çalışma anında dış bağımlılık yoktur (yalnızca yazı tipleri Google Fonts'tan yüklenir,
çevrimdışıyken sistem yazı tipine düşer).

## Neler var

- **Beş metrik:** kişi başı gelir, yaşam beklentisi, nüfus, internet erişimi, kişi başı CO₂.
  Üstteki anahtarla anında geçiş yapılır.
- **Choropleth renklendirme:** tek renkli sekiz basamaklı sıralı ölçek; eşikler her metrik
  için ayrı tanımlıdır. Verisi olmayan ülkeler nötr griyle gösterilir.
- **Lejand:** eşik değerleriyle birlikte; bir basamağın üzerine gelince o aralıktaki ülkeler
  haritada öne çıkar, diğerleri soluklaşır.
- **Hover ipucu:** ülke adı, seçili metriğin değeri ve sıralaması.
- **Pan / zoom:** sürükleme, tekerlek, çift tıklama, dokunmatikte iki parmakla yakınlaştırma,
  `+` / `−` / sıfırlama düğmeleri ve klavyeden `+` / `−`.
- **Yan panel:** seçili ülkenin bölgesi, bölge içi sırası, tüm metrik değerleri ve dünya
  medyanına göre konumu. Seçim yokken metriğin özeti ile en yüksek/en düşük beş ülke listelenir.
- **Ülke arama**, `Esc` ile seçimi temizleme, açık/koyu tema desteği.

## Veri

`index.html` içine gömülü veri seti **gösterim amaçlı hazırlanmış örnek veridir**; 189 ülke için
yaklaşık büyüklükleri yansıtır, resmî istatistik olarak kullanılmamalıdır. Gerçek bir kaynağa
bağlanmak için dosyadaki `RAW` tablosunu (`Ülke|Bölge|nüfus|gelir|yaşam|internet|CO₂` satırları)
değiştirmek ve gerekiyorsa `METRICS` içindeki `bins` eşiklerini güncellemek yeterlidir.

Ülke sınırları [world-atlas](https://github.com/topojson/world-atlas) (Natural Earth 1:50m)
verisinden üretilmiş, sadeleştirilmiş ve Natural Earth izdüşümüne göre önceden hesaplanmış
SVG yollarıdır.

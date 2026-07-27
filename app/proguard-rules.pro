# Release derlemesinde kod küçültme (R8) açıktır. Aşağıdaki kurallar,
# yansıma (reflection) ile erişilen ya da yerel koddan çağrılan sınıfların
# yanlışlıkla atılmasını/yeniden adlandırılmasını engeller.

# ---- ML Kit (metin tanıma + karekod) ----
# Modeller ve yükleyicileri yansımayla bulunur; isteğe bağlı sınıflara yapılan
# başvurular da derlemede uyarı üretmemeli.
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---- Uygulama modelleri ----
# Kayıtlar JSON'a yazılıp okunduğu için alan/sınıf adları korunur.
-keep class com.fatsar.kartvizit.model.** { *; }
-keep class com.fatsar.kartvizit.ocr.OcrLine { *; }

# ---- org.json ----
-dontwarn org.json.**

# Kilitlenme raporlarının okunabilmesi için satır numaralarını sakla
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

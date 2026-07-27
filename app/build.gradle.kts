plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

android {
    namespace = "com.fatsar.kartvizit"
    // Google Play, yeni yüklemelerin güncel API düzeyini hedeflemesini ister
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fatsar.kartvizit"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "3.1"

        // CI, -PbuildSha=<kısa-sha> geçirir; menüdeki "Sürüm" satırında
        // hangi derlemenin kurulu olduğu görülür (eski APK karışıklığına son)
        val buildSha = (project.findProperty("buildSha") as? String) ?: "yerel"
        buildConfigField("String", "BUILD_SHA", "\"$buildSha\"")
    }

    // Doğrudan (mağaza dışı) dağıtılan derlemeler için sabit anahtar: yeni
    // sürümler eskisinin üzerine kurulabilsin diye depoda tutulur. YALNIZCA
    // debug derlemesinde kullanılır; mağaza sürümü bu anahtarla imzalanmaz.
    //
    // Play Store yüklemesi için yükleme anahtarı depoya konmaz; ya
    // `keystore.properties` dosyasından ya da ortam değişkenlerinden okunur
    // (KARTCEP_STORE_FILE, KARTCEP_STORE_PASSWORD, KARTCEP_KEY_ALIAS,
    // KARTCEP_KEY_PASSWORD). Hiçbiri yoksa release imzasız üretilir.
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun secret(key: String, env: String): String? =
        (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

    val releaseStore = secret("storeFile", "KARTCEP_STORE_FILE")

    signingConfigs {
        create("shared") {
            storeFile = file("kartvizit.jks")
            storePassword = "kartvizit"
            keyAlias = "kartvizit"
            keyPassword = "kartvizit"
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = secret("storePassword", "KARTCEP_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "KARTCEP_KEY_ALIAS")
                keyPassword = secret("keyPassword", "KARTCEP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // Mağaza sürümü küçültülür: kullanılmayan kod ve kaynaklar atılır
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Bulut/klasör yedeklemesi için Storage Access Framework sarmalayıcısı
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Cihaz üzerinde (offline) metin tanıma - model uygulamayla birlikte gelir
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Cihaz üzerinde (offline) karekod/barkod tanıma
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
    // Birim testlerinde gerçek org.json (android.jar'ın sahte sürümü yerine)
    testImplementation("org.json:json:20240303")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fatsar.kartvizit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fatsar.kartvizit"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"

        // CI, -PbuildSha=<kısa-sha> geçirir; menüdeki "Sürüm" satırında
        // hangi derlemenin kurulu olduğu görülür (eski APK karışıklığına son)
        val buildSha = (project.findProperty("buildSha") as? String) ?: "yerel"
        buildConfigField("String", "BUILD_SHA", "\"$buildSha\"")
    }

    // Sabit imza anahtarı: her derleme aynı anahtarla imzalanır; böylece yeni
    // sürümler eskisinin üzerine (kaldırmadan) kurulabilir. Kendi kendine
    // dağıtılan bir uygulama olduğundan anahtar depoya dahildir.
    signingConfigs {
        create("shared") {
            storeFile = file("kartvizit.jks")
            storePassword = "kartvizit"
            keyAlias = "kartvizit"
            keyPassword = "kartvizit"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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

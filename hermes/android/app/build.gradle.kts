plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fatsar.hermes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fatsar.hermes"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        resourceConfigurations += listOf("tr", "en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Kendi imza anahtarınız varsa (HERMES_KEYSTORE_FILE ortam değişkeni ile
    // gösterilir) release APK onunla imzalanır; yoksa hata vermemek için
    // Android'in debug anahtarı kullanılır. Anahtar dosyası depoda TUTULMAZ.
    val keystorePath: String? = System.getenv("HERMES_KEYSTORE_FILE")
    signingConfigs {
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            create("dagitim") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("HERMES_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HERMES_KEY_ALIAS") ?: "hermes"
                keyPassword = System.getenv("HERMES_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("dagitim") ?: signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric'in gerçek kaynak/tema dosyalarını okuyabilmesi için
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    // Dispatchers.Main'in Android üzerinde çalışması için gerekli
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Arayüzü emülatörsüz (JVM üzerinde) çalıştırıp deneyen testler
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Aynı akışın gerçek cihaz/emülatör sürümü (elle çalıştırmak için)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

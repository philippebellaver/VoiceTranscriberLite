plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

android {
    namespace = "com.example.voicetranscriberlite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voicetranscriberlite"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Incluindo jniLibs para Vosk e LAME
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/LGPL2.1",
                "META-INF/AL2.0",
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Activity + Compose
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.activity:activity-ktx:1.9.2")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle & Coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Vosk para reconhecimento offline
    implementation(files("libs/vosk-android-0.3.70.aar"))

    // LAME MP3 nativo
    // Lembre-se de colocar libmp3lame.so nas pastas corretas dentro de src/main/jniLibs
    // armeabi-v7a, arm64-v8a, x86, x86_64
    // carregado no código: System.loadLibrary("mp3lame")

    // JNA atualizado
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Apache POI para exportar DOCX
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // iText para gerar PDF
    implementation("com.itextpdf:itextg:5.5.10")

    // Material icons
    implementation("androidx.compose.material:material-icons-extended:1.5.0")

    // Testes
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Forçando JNA correto
configurations.all {
    resolutionStrategy {
        force("net.java.dev.jna:jna:5.14.0")
        force("net.java.dev.jna:jna-platform:5.14.0")
    }
}

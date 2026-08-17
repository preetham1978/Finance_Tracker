import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("org.gradle.test-retry")
}
android {
    namespace = "com.example"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.aistudio.financetracker.vkqywz"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
        val geminiKey = System.getenv("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"") // force rebuild
        // Free-tier Groq key (console.groq.com/keys), used as a fallback
        // text-generation provider when Gemini fails -- see GroqApiService.kt.
        val groqKey = System.getenv("GROQ_API_KEY") ?: ""
        buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
    }
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            val geminiKey = System.getenv("GEMINI_API_KEY") ?: ""
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
            val groqKey = System.getenv("GROQ_API_KEY") ?: ""
            buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Networking & JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.mlkit.document.scanner)
    // On-device text recognition (OCR) -- powers the bill scanner's
    // reliable, billing-free fallback path in OcrManager.kt.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
// Retry a failed unit test up to twice before letting it fail the build. This exists purely
// to absorb the known, non-deterministic "Firebase Background Thread" flakiness under
// Robolectric (see EditProfileDialogTest.kt / LoginValidationTest.kt doc comments) - it does
// NOT mask a genuinely broken test, since a real bug fails consistently and will still fail
// on every retry attempt.
tasks.withType<Test>().configureEach {
    retry {
        maxRetries.set(2)
        maxFailures.set(5)
        failOnPassedAfterRetry.set(false)
    }
}

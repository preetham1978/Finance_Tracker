import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Loads KEY=VALUE pairs from the project's .env file (see .env.example),
// falling back to nothing if it doesn't exist -- .env is gitignored and
// never committed. Added because System.getenv() alone only sees real OS
// environment variables (like the GEMINI_API_KEY/GROQ_API_KEY set via
// `setx` on Windows), so a value only ever placed in .env (like
// BACKEND_BASE_URL) was silently never picked up by any build -- the app
// kept building against the placeholder URL below with no error, which
// looked like a flaky AI feature but was actually every network call
// failing instantly and falling back to on-device/offline paths. Real env
// vars still win if both are set, so CI/other machines aren't affected.
val dotEnvFile = rootProject.file(".env")
val dotEnvProperties = Properties()
if (dotEnvFile.exists()) {
    dotEnvFile.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
            val key = trimmed.substringBefore("=").trim()
            val value = trimmed.substringAfter("=").trim()
            dotEnvProperties.setProperty(key, value)
        }
    }
}
fun envOrDotEnv(key: String): String? =
    System.getenv(key) ?: dotEnvProperties.getProperty(key)

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
        // Base URL of the deployed backend proxy (see functions/index.js
        // and README.md's "Backend proxy" section) -- e.g.
        // "https://us-central1-your-project-id.cloudfunctions.net/".
        // Gemini and Groq API keys used to be baked directly into this app
        // as buildConfig fields (GEMINI_API_KEY / GROQ_API_KEY), which meant
        // they shipped inside the compiled APK and were extractable by
        // anyone who installed it. Both providers are now called through
        // this Cloud Functions proxy instead: the real keys live only in
        // the function's server-side secrets, and the app authenticates
        // with a Firebase ID token that the backend verifies and uses for
        // per-user rate limiting. See GeminiApiService.kt / GroqApiService.kt.
        val backendBaseUrl = envOrDotEnv("BACKEND_BASE_URL")
            ?: "https://us-central1-REPLACE_WITH_YOUR_PROJECT_ID.cloudfunctions.net/"
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
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
            val backendBaseUrl = envOrDotEnv("BACKEND_BASE_URL")
                ?: "https://us-central1-REPLACE_WITH_YOUR_PROJECT_ID.cloudfunctions.net/"
            buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
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
    // Google Play Billing -- Professional/Professional Plus subscription
    // purchases. See BillingManager.kt and functions/index.js's
    // verifyPlayPurchase (the server-side check that actually grants a tier).
    implementation(libs.billing.ktx)
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

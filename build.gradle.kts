plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    // Automatically re-runs a failed unit test up to twice before counting it as a real
    // failure. Added specifically for the known, non-deterministic "Firebase Background
    // Thread" flakiness under Robolectric (see EditProfileDialogTest.kt and
    // LoginValidationTest.kt doc comments) - a real bug still fails every retry and still
    // fails the build; this only smooths over noise that isn't actually broken.
    id("org.gradle.test-retry") version "1.6.1" apply false
}

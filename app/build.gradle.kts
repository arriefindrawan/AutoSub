plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.blay.autosub"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.blay.autosub"
        minSdk = 30   // takeScreenshot butuh Android 11+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
}

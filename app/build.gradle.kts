plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.hondaecuremap"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.hondaecuremap"
        minSdk = 26
        targetSdk = 32
        versionCode = 1
        versionName = "0.1-alpha"
    }
}


dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
}

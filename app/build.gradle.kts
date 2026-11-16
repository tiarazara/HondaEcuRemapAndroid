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
        versionCode = 2
        versionName = "0.2-alpha"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
}
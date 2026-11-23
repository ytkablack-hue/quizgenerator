plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "kz.quizgenerator"
    compileSdk = 36

    defaultConfig {
        applicationId = "kz.quizgenerator"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.8.1-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val uploadStore = providers.environmentVariable("TESTHELPER_UPLOAD_STORE_FILE").orNull
    if (uploadStore != null) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore)
            storePassword = providers.environmentVariable("TESTHELPER_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("TESTHELPER_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("TESTHELPER_UPLOAD_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (uploadStore != null) signingConfig = signingConfigs.getByName("upload")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":ui"))
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
}

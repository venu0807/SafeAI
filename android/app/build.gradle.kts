plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // ponytail: uses debug signing for dev builds. For Play Store release:
            // 1. Generate a keystore: keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
            // 2. Set env vars: SIGNING_KEY_PATH, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD, SIGNING_STORE_PASSWORD
            // 3. Uncomment below:
            //   signingConfig = signingConfigs.create("release") {
            //       storeFile = file(System.getenv("SIGNING_KEY_PATH") ?: "release.keystore")
            //       storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            //       keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "release"
            //       keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
            //   }
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        mlModelBinding = true
        compose = true
    }

    androidResources { noCompress += "tflite" }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Jetpack Compose (BOM)
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-test-junit4")
    
    // ML & AI
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-api:2.14.0")
    
    // Kotlin & Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // CameraX & Lifecycle Service
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
    
    // Location & Maps
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Security & Data
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.code.gson:gson:2.10.1")

    // Firebase (BOM)
    implementation(platform("com.google.firebase:firebase-bom:34.8.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")
    
    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
}

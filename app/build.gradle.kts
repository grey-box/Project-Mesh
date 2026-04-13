plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version "1.9.0-1.0.13"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.3"
    id("org.jetbrains.dokka") version "2.2.0-Beta"
}

android {
    namespace = "com.greybox.projectmesh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.greybox.projectmesh"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    lint {
        // affects gradle linter
        disable.add("UnusedResources")
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            isUniversalApk = true
        }
    }
}

dependencies {
    // ===============================
    // General
    // ===============================
    implementation(libs.accompanist.permissions)
    implementation(libs.acra.dialog)
    implementation(libs.acra.http)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.core.v111)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.ui)
    implementation(libs.coil.compose)
    implementation(libs.compose.qrpainter)
    implementation(libs.gson) // for crash screen
    implementation(libs.ipaddress)
    implementation(libs.jetbrains.kotlinx.serialization.json) // For JSON serialization
    implementation(libs.material)
    implementation(libs.meshrabiya)
    implementation(libs.nanohttp)
    implementation(libs.okhttp)
    implementation(libs.zxing.android.embedded)
    implementation(platform(libs.androidx.compose.bom))


    // ===============================
    // Unit testing (JVM) deps added
    // ===============================
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
    

    // ===============================
    // Kodein
    // ===============================
    // For Android-specific features
    implementation (libs.kodein.di.framework.android.x)

    // For Jetpack Compose support
    implementation (libs.kodein.di.framework.compose)


    // ===============================
    // Room
    // ===============================
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)

    // To use Kotlin annotation processing tool (kapt)
    // kapt("androidx.room:room-compiler:$room_version")
    // To use Kotlin Symbol Processing (KSP)
    ksp(libs.androidx.room.compiler)
    
    implementation(libs.androidx.room.guava) // optional - Guava support for Room, including Optional and ListenableFuture
    implementation(libs.androidx.room.ktx) // optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.androidx.room.paging)  // optional - Paging 3 Integration
    implementation(libs.androidx.room.rxjava2) // optional - RxJava2 support for Room
    implementation(libs.androidx.room.rxjava3) // optional - RxJava3 support for Room
    testImplementation(libs.androidx.room.testing) // optional - Test helpers
}

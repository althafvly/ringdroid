plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.ringdroid"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "org.thayyil.ringdroid"
        minSdk = 23
        targetSdk = 36
        versionCode = 20903
        versionName = "2.9.3"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("fdroid") { dimension = "distribution" }

        create("play") { dimension = "distribution" }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.named("preBuild") {
    dependsOn("createPreCommitHook")
    dependsOn("spotlessCheck")
}

dependencies {
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material.icons.extended)
    implementation(libs.lifecycle.runtime)
    implementation(libs.google.material)
}

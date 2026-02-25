plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ringdroid"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "org.thayyil.ringdroid"
        minSdk = 21
        targetSdk = 36
        versionCode = 20905
        versionName = "2.9.5"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isDebuggable = false
            isJniDebuggable = false
        }

        getByName("debug") {
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("fdroid") {
            dimension = "distribution"
        }

        create("play") {
            dimension = "distribution"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
}

dependencies {
    implementation(libs.support.annotations)
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ringdroid"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.ringdroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 20707
        versionName = "2.7.7"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.txt"
            )
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("github") {
            dimension = "distribution"
        }

        create("fdroid") {
            dimension = "distribution"
            // F-Droid package name
            applicationId = "org.thayyil.ringdroid"
        }
    }
}

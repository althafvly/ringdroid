plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.ringdroid"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.ringdroid"
        minSdk = 21
        targetSdk = 36
        versionCode = 20708
        versionName = "2.7.8"
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

spotless {
    java {
        removeUnusedImports()
        eclipse()
        leadingSpacesToTabs(2)
        leadingTabsToSpaces(4)
        target("src/*/java/**/*.java")
    }
    format("xml") {
        target("src/**/*.xml")
        targetExclude("**/build/", ".idea/")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
    }
}

tasks.named("preBuild") {
    dependsOn("spotlessCheck")
}

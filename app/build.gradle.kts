import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.android)
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
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
    kotlin {
        ktlint()
        leadingTabsToSpaces(2)
        target("src/*/java/**/*.kt")
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

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.ringdroid"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "org.thayyil.ringdroid"
        minSdk = 21
        targetSdk = 36
        versionCode = 20803
        versionName = "2.8.3"
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
        create("fdroid") {
            dimension = "distribution"
        }

        create("play") {
            dimension = "distribution"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

dependencies {
    implementation(libs.support.annotations)
}

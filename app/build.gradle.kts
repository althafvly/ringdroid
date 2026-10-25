plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ringdroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.thayyil.ringdroid"
        minSdk = 24
        targetSdk = 37
        versionCode = 30200
        versionName = "3.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            vcsInfo.include = false
        }

        getByName("debug") {
            applicationIdSuffix = ".dev"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
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
        includeInApk = false
        includeInBundle = true
    }

    packaging {
        resources {
            excludes +=
                listOf(
                    "**/*.kotlin_builtins",
                    "DebugProbesKt.bin",
                    "META-INF/*.version",
                    "META-INF/**/LICENSE.txt",
                )
        }
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

kotlin {
    compilerOptions {
        // Skip runtime null checks on parameters and platform calls to keep the APK small.
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

plugins {
    alias(libs.plugins.android.application)
    id("jacoco")
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
        versionCode = 30000
        versionName = "3.0.0"
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
        }

        getByName("debug") {
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
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

    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
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
        target("src/**/*.kt")
        ktfmt().kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.kts", "**/*.kts")
        ktfmt().kotlinlangStyle()
    }

    format("xml") {
        target("src/**/*.xml")
        targetExclude("**/build/", ".idea/")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
    }
}

tasks.named("preBuild") { dependsOn("spotlessCheck") }

dependencies {
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material.icons.extended)
    implementation(libs.lifecycle.runtime)
    implementation(libs.google.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("connectedFdroidDebugAndroidTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter =
        listOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
        )
    val debugTree =
        fileTree("${project.layout.buildDirectory}/tmp/kotlin-classes/fdroidDebug") {
            exclude(fileFilter)
        }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(project.layout.buildDirectory) {
            include("outputs/code_coverage/fdroidDebugAndroidTest/connected/*coverage.ec")
        }
    )
}

import com.android.build.gradle.internal.crash.afterEvaluate

// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

val ktlintOverrides = mapOf("max_line_length" to 100)

spotless {
    format("xml") {
        target("app/src/**/*.xml")
        targetExclude("**/build/", ".idea/")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    kotlin {
        target("app/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(ktlintOverrides)
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "**/build.gradle.kts")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(ktlintOverrides)
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
}

subprojects {
    project.tasks.register("createPreCommitHook") {
        description = "Creates the .git/hooks/pre-commit file with predefined content."

        doLast {
            val gitHooksDir = File(project.rootDir, ".git/hooks")
            if (!gitHooksDir.exists()) {
                gitHooksDir.mkdirs()
            }

            val preCommitFile = File(gitHooksDir, "pre-commit")
            if (preCommitFile.exists()) return@doLast
            val content =
                """
                #!/usr/bin/env bash

                [ -f ./hooks/pre-commit.sh ] && ./hooks/pre-commit.sh
                """.trimIndent()

            preCommitFile.writeText(content)
            preCommitFile.setExecutable(true)
        }
    }

    afterEvaluate { project ->
        project.tasks.named("preBuild") {
            dependsOn("spotlessCheck")
            dependsOn("createPreCommitHook")
        }
    }
}

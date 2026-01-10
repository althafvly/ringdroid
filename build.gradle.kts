import com.android.build.gradle.internal.crash.afterEvaluate

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.spotless)
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
                     """
                    .trimIndent()

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

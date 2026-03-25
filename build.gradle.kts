import Constants.GENERATED_BASH_OUTPUT_FILE
import Constants.GENERATED_ZSH_OUTPUT_FILE
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

plugins {
    id("base")
}

object Constants {
    const val GENERATED_BASH_OUTPUT_FILE = "gradle-completion.bash"
    const val GENERATED_ZSH_OUTPUT_FILE = "_gradle"
}

tasks {

    register<FetchLatestGradleVersionTask>("fetchLatestGradleVersion") {
        versionFile = layout.buildDirectory.file("gradle-version.txt")
    }

    val generateCompletionScripts = register<GenerateCompletionScriptsTask>("generateCompletionScripts") {
        bashTemplate = layout.projectDirectory.file("gradle-completion.bash.template")
        zshTemplate = layout.projectDirectory.file("_gradle.template")
        bashOutputFile = layout.projectDirectory.file(GENERATED_BASH_OUTPUT_FILE)
        zshOutputFile = layout.projectDirectory.file(GENERATED_ZSH_OUTPUT_FILE)
    }

    val distTar = register<Tar>("distTar") {
        val version = providers
            .gradleProperty("release-version").orElse("")
            // TODO: better ways to do this?
            .map { it.ifEmpty { error("No release-version property set") } }

        val tarName = version.map { "gradle-completion-${it}"}
        archiveFileName = tarName.map { "$it.tar.gz" }
        destinationDirectory = layout.buildDirectory
        compression = Compression.GZIP
        into(tarName) {
            from(
                "README.md",
                "LICENSE",
                "gradle-completion.plugin.zsh",
            )
            from(generateCompletionScripts.map { it.outputs.files })
        }
        doLast {
            println("Created archive: ${archiveFile.get()}")
        }
    }

    assemble {
        dependsOn(distTar)
    }

    val verifyCompletionScriptsUpToDate = register<Exec>("verifyCompletionScriptsUpToDate") {
        description = "Verify generated completion scripts match committed versions"
        group = VERIFICATION_GROUP
        dependsOn(generateCompletionScripts)
        executable = "git"
        args("diff", "--exit-code", GENERATED_BASH_OUTPUT_FILE, GENERATED_ZSH_OUTPUT_FILE)
        isIgnoreExitValue = true
        doLast {
            if (executionResult.get().exitValue != 0) {
                throw GradleException(
                    "Generated completion scripts differ from committed versions. " +
                    "Run './gradlew generateCompletionScripts' and commit the result."
                )
            }
        }
    }

    val integrationTest = register<Exec>("integrationTest") {
        description = "Run integration tests for cache generation and security"
        group = VERIFICATION_GROUP
        executable = "bash"
        args("tests/integration_test.sh")
    }

    val bashCompletionTest = register<Exec>("bashCompletionTest") {
        description = "Run bash completion function tests"
        group = VERIFICATION_GROUP
        args("tests/test_completion.sh")
        doFirst {
            executable = listOf("/opt/homebrew/bin/bash", "/usr/local/bin/bash")
                .map(::File).firstOrNull { it.canExecute() }?.absolutePath ?: "bash"
        }
    }

    val zshCompletionTest = register<Exec>("zshCompletionTest") {
        description = "Run zsh completion tests"
        group = VERIFICATION_GROUP
        executable = "bash"
        args("tests/zsh_test_runner.sh")
    }

    check {
        dependsOn(verifyCompletionScriptsUpToDate,zshCompletionTest, bashCompletionTest, integrationTest)
    }

}

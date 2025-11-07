plugins {
    id("base")
}

tasks {

    register<FetchLatestGradleVersionTask>("fetchLatestGradleVersion") {
        versionFile = layout.buildDirectory.file("gradle-version.txt")
    }

    val generateCompletionScripts = register<GenerateCompletionScriptsTask>("generateCompletionScripts") {
        bashTemplate = layout.projectDirectory.file("gradle-completion.bash.template")
        zshTemplate = layout.projectDirectory.file("_gradle.template")
        bashOutputFile = layout.projectDirectory.file("gradle-completion.bash")
        zshOutputFile = layout.projectDirectory.file("_gradle")
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

}

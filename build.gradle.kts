tasks.register<FetchLatestGradleVersionTask>("fetchLatestGradleVersion") {
    versionFile = layout.buildDirectory.file("gradle-version.txt")
}

tasks.register<GenerateCompletionScriptsTask>("generateCompletionScripts") {
    bashTemplate = layout.projectDirectory.file("gradle-completion.bash.template")
    zshTemplate = layout.projectDirectory.file("_gradle.template")
    bashOutputFile = layout.projectDirectory.file("gradle-completion.bash")
    zshOutputFile = layout.projectDirectory.file("_gradle")
}

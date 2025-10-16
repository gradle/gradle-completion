// Register the custom task and configure file properties
tasks.register<GenerateCompletionScriptsTask>("generateCompletionScripts") {
    bashTemplate = layout.projectDirectory.file("gradle-completion.bash.template")
    zshTemplate = layout.projectDirectory.file("_gradle.template")
    bashOutputFile = layout.projectDirectory.file("gradle-completion.bash")
    zshOutputFile = layout.projectDirectory.file("_gradle")
}

// Register the custom task and configure file properties
tasks.register<GenerateCompletionScriptsTask>("generateCompletionScripts") {
    bashTemplate.set(layout.projectDirectory.file("gradle-completion.bash.template"))
    zshTemplate.set(layout.projectDirectory.file("_gradle.template"))
    bashOutputFile.set(layout.projectDirectory.file("gradle-completion.bash"))
    zshOutputFile.set(layout.projectDirectory.file("_gradle"))
}

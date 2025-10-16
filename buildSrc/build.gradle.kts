plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

tasks.validatePlugins {
    enableStricterValidation = true
}
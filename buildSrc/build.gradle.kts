plugins {
    `kotlin-dsl`
    alias(libs.plugins.detekt)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mokk)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
}

detekt {
    buildUponDefaultConfig = true
    config.from(layout.settingsDirectory.file("../gradle/detekt.yml"))
}

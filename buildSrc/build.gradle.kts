plugins {
    `kotlin-dsl`
    id("dev.detekt") version "2.0.0-alpha.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
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
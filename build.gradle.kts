repositories {
    mavenCentral()
}

plugins {
    kotlin("multiplatform") apply false
    id("maven-publish")
}

subprojects {
    repositories {
        mavenCentral()
    }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "16.0.0"
}

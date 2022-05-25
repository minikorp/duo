pluginManagement {
    val kotlinVersion: String by settings
    val kspVersion: String by settings
    val composeMultiplatformVersion: String by settings

    plugins {
        kotlin("multiplatform") version kotlinVersion apply false
        id("com.google.devtools.ksp") version kspVersion apply false
        id("org.jetbrains.compose") version composeMultiplatformVersion apply false
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven { setUrl("https://jitpack.io") }
    }
}

include(":duo", ":drill", ":ksp", ":example")

rootProject.name = "duo"

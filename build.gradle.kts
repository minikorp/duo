import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        val kotlinVersion: String by project
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
        classpath("com.android.tools.build:gradle:4.1.3")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("maven-publish")
}

// Publishing configuration

val projectsToPublish = listOf("ksp", "duo", "drill")
val publicationsFromMainHost = listOf("jvm", "kotlinMultiplatform")

subprojects {
    val subproject = this
    if (this.name !in projectsToPublish) return@subprojects

    apply(plugin = "maven-publish")
    publishing {
        publications {
            val githubUsername: String? by gradleLocalProperties(rootDir)
            val githubToken: String? by gradleLocalProperties(rootDir)
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/minikorp/duo")
                    credentials {
                        username = githubUsername ?: System.getenv("GITHUB_USERNAME")
                        password = githubToken ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }

            matching { it.name in publicationsFromMainHost }.all {
                this as MavenPublication

                groupId = "com.minikorp.duo"
                artifactId = subproject.name
                version = "1.0.2"

                val targetPublication = this@all
                tasks.withType<AbstractPublishToMaven>()
                    .matching { it.publication == targetPublication }
                    .configureEach {}
            }
        }
    }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "16.0.0"
}

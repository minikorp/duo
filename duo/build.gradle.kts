import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }


    sourceSets {
        val coroutinesVersion: String by project
        val commonMain by getting {
            dependencies {
                api(project(":drill"))
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.3.3")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${coroutinesVersion}-native-mt")
            }
        }
        val jvmTest by getting
    }
}
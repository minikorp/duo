import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val composeMultiplatformVersion: String by project

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
    }
    sourceSets {
        all {
            dependencies {
                implementation(project(":duo"))
                implementation(project(":drill"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val jvmTest by getting
    }
}

dependencies {
    add("kspJvm", project(":ksp"))
    add("kspJvmTest", project(":ksp"))
    // Add as needed
//    add("kspCommonMainMetadata", project(":ksp"))
//    add("kspJs", project(":ksp"))
//    add("kspJsTest", project(":ksp"))
//    add("kspAndroidNativeX64", project(":ksp"))
//    add("kspAndroidNativeX64Test", project(":ksp"))
//    add("kspAndroidNativeArm64", project(":ksp"))
//    add("kspAndroidNativeArm64Test", project(":ksp"))
//    add("kspLinuxX64", project(":ksp"))
//    add("kspLinuxX64Test", project(":ksp"))
//    add("kspMingwX64", project(":ksp"))
//    add("kspMingwX64Test", project(":ksp"))
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "example"
            packageVersion = "1.0.0"
        }
    }
}

afterEvaluate {
    kotlin.sourceSets.forEach { sourceSet ->
        sourceSet.kotlin.srcDir("build/generated/ksp/jvm/${sourceSet.name}/kotlin")
    }
}

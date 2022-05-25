plugins {
    kotlin("multiplatform")
}

kotlin {
    val kspVersion: String by project

    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":duo"))
                implementation(project(":drill"))
                implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
                implementation("com.squareup:kotlinpoet-ksp:1.11.0")
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
    }
}
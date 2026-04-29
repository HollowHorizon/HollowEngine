plugins {
    kotlin("jvm")
}

val kotlinVersion: String by rootProject.properties

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib", kotlinVersion))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.0")

    testImplementation(kotlin("test", kotlinVersion))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

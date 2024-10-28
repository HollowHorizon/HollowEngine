import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
}

version = "1.3"

base {
    archivesName = "HollowEnginePlugin"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-serialization-compiler-plugin:2.0.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")

    // SCRIPTING //
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-script-runtime:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0")
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:2.0.0")

    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    testImplementation("dev.zacsweers.kctfork:core:0.5.1")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
//tasks {
//    test {
//        useJUnitPlatform()
//    }
//}
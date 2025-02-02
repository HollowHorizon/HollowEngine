plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.kikugie.dev/snapshots/")
    maven("https://maven.minecraftforge.net")
}

dependencies {
    implementation("architectury-plugin:architectury-plugin.gradle.plugin:3.4-SNAPSHOT")
    implementation("net.fabricmc:tiny-remapper:0.10.4")
    implementation("dev.architectury:architectury-loom:1.9-SNAPSHOT")
    implementation("dev.kikugie:stonecutter:0.6-alpha.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20-Beta2")
    implementation("me.fallenbreath.yamlang:me.fallenbreath.yamlang.gradle.plugin:1.3.1")
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
    }
}
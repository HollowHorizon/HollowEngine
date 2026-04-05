import org.gradle.jvm.tasks.Jar
import net.fabricmc.loom.extension.LoomGradleExtensionImpl
import net.fabricmc.loom.task.RemapJarTask

plugins {
    idea
    java
    `maven-publish`
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("me.fallenbreath.yamlang")
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

extra["hollow.mainSourceRoot"] = "runtime/src/main"
extra["hollow.generateIdeRuns"] = false

val runtimeShadowBundle = configurations.create("bundledLibraries") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val loom = extensions["loom"] as LoomGradleExtensionImpl

apply(from = rootProject.file("gradle/hollowengine-runtime-module.gradle.kts"))

loom.runConfigs.all {
    ideConfigGenerated(false)
}

tasks.matching {
    it.name in setOf("runClient", "runServer", "runGameTest", "runDatagen", "configureClientLaunch", "configureServerLaunch", "configureLaunch")
}.configureEach {
    enabled = false
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("dev")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("stonecutterGenerate")
}

tasks.matching { it.name == "validateAccessWidener" }.configureEach {
    dependsOn("stonecutterGenerate")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("HollowEngineRuntime")
    archiveClassifier.set("dev-shadow")
    configurations = listOf(runtimeShadowBundle)
    mergeServiceFiles()
    exclude("fabric.mod.json")
    exclude("architectury.common.marker")
    exclude("META-INF/mods.toml")
    exclude("META-INF/neoforge.mods.toml")
    exclude("*.mixins.json")
}

tasks.named<RemapJarTask>("remapJar") {
    enabled = true
    dependsOn("shadowJar")
    inputFile.set(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").flatMap { it.archiveFile })
    archiveBaseName.set("HollowEngineRuntime")
    archiveClassifier.set("")
    addNestedDependencies.set(false)
}

tasks.named("remapSourcesJar") {
    enabled = false
}

tasks.named("build") {
    dependsOn("remapJar")
}

tasks.named("buildAndCollect") {
    enabled = false
    setDependsOn(emptyList<Any>())
}

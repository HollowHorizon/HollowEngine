plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val neoforgeVersion = project.properties["neoforge_version"].toString()
val modName = project.properties["mod_name"].toString()

val common: Configuration by configurations.creating
val forgeLike: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating

architectury {
    platformSetupLoomIde()
    neoForge()
}

base {
    archivesName = "$modName-neoforge"
}

configurations {
    compileClasspath.get().extendsFrom(common, forgeLike)
    runtimeClasspath.get().extendsFrom(common, forgeLike)
    named("developmentNeoForge").get().extendsFrom(common)
    named("developmentForgeLike").get().extendsFrom(forgeLike)
}

dependencies {
    neoForge("net.neoforged:neoforge:$neoforgeVersion")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionNeoForge")) { isTransitive = false }
}

tasks {
    shadowJar {
        configurations = listOf(shadowCommon)
        archiveClassifier = "dev-shadow"
    }

    remapJar {
        inputFile.set(shadowJar.get().archiveFile)
    }
}

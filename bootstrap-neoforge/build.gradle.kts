import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import java.security.MessageDigest

plugins {
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("com.github.johnrengelman.shadow")
}

val modId: String by properties
val modName: String by properties
val modVersion: String by properties
val modAuthor: String by rootProject.properties
val license: String by properties
val modGroup: String by properties
val minecraftVersion: String by rootProject.properties
val neoForgeVersion: String by rootProject.properties
val architecturyApiVersion: String by rootProject.properties

layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("build/${project.path.removePrefix(":").replace(':', '/')}"))
group = modGroup
version = modVersion
base.archivesName.set("$modName-neoforge-$minecraftVersion")

val sourceSets = extensions.getByType<SourceSetContainer>()
val embeddedRuntimeDir = layout.buildDirectory.dir("generated/embedded-runtime")
val runtimeJarTask = project(":runtime").tasks.named<Jar>("jar")

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    silentMojangMappingsLicense()

    val accessWidener = rootProject.file("runtime/src/main/resources/$modId.accesswidener")
    if (accessWidener.exists()) {
        accessWidenerPath.set(accessWidener)
    }
}

configurations {
    create("common") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    named("compileClasspath") {
        extendsFrom(getByName("common"))
    }
    named("runtimeClasspath") {
        extendsFrom(getByName("common"))
    }
    named("developmentNeoForge") {
        extendsFrom(getByName("common"))
    }
    create("shadowBundle") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.architectury.dev/")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    mavenLocal()
    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"(loom.officialMojangMappings())

    "neoForge"("net.neoforged:neoforge:$neoForgeVersion")
    modImplementation("dev.architectury:architectury-neoforge:$architecturyApiVersion")
    implementation("io.github.llamalad7:mixinextras-neoforge:0.4.1")

    "common"(project(path = ":bridge", configuration = "namedElements")) { isTransitive = false }
    "shadowBundle"(project(path = ":bridge", configuration = "transformProductionNeoForge"))
}

val embedRuntimeJar = tasks.register("embedRuntimeJar") {
    group = "build"
    description = "Embeds the current runtime jar into bootstrap resources."

    dependsOn(runtimeJarTask)
    inputs.file(runtimeJarTask.flatMap { it.archiveFile })
    outputs.dir(embeddedRuntimeDir)

    doLast {
        val outputDir = embeddedRuntimeDir.get().dir("META-INF/hollowengine/runtime").asFile
        outputDir.mkdirs()

        val runtimeJar = runtimeJarTask.get().archiveFile.get().asFile
        val targetJar = outputDir.resolve("HollowEngineRuntime.jar")
        runtimeJar.copyTo(targetJar, overwrite = true)

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(targetJar.readBytes())
            .joinToString("") { "%02x".format(it) }
        outputDir.resolve("HollowEngineRuntime.sha256").writeText(sha256)
    }
}

sourceSets.named("main").configure {
    java.setSrcDirs(listOf(rootProject.file("bootstrap/src/main/java")))
    java.exclude(
        "ru/hollowhorizon/hollowengine/bootstrap/fabric/**",
        "ru/hollowhorizon/hollowengine/bootstrap/forge/**",
        "ru/hollowhorizon/hollowengine/bootstrap/mixins/fabric/**",
    )
    resources.setSrcDirs(
        listOf(
            rootProject.file("bootstrap/src/main/resources"),
            rootProject.file("bootstrap-neoforge/src/main/resources"),
            rootProject.file("runtime/src/main/resources"),
            embeddedRuntimeDir,
        )
    )
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(embedRuntimeJar)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "$modId.mixins.json", "$modId.bridge.mixins.json", "pack.mcmeta")) {
        expand(
            mapOf(
                "mod_id" to modId,
                "mod_name" to modName,
                "mod_version" to modVersion,
                "mod_author" to modAuthor,
                "license" to license,
                "minecraft_version" to minecraftVersion,
                "neo_version" to neoForgeVersion,
                "architectury_api_version" to architecturyApiVersion,
            )
        )
    }
    exclude("fabric.mod.json")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations.getByName("shadowBundle"))
    archiveClassifier.set("dev-shadow")
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    inputFile.set(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").flatMap { it.archiveFile })
}

tasks.named<JavaCompile>("compileJava") {
    setSource(sourceSets.named("main").get().java)
}

tasks.matching { it.name.startsWith("run") }.configureEach {
    dependsOn(embedRuntimeJar)
}

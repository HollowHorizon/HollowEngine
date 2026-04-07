import net.fabricmc.loom.extension.LoomGradleExtensionImpl
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import ru.hollowhorizon.gradle.ModProject
import ru.hollowhorizon.gradle.ResourcesSetup
import ru.hollowhorizon.gradle.StonecutterSetup
import ru.hollowhorizon.gradle.fabric.FabricSetup
import ru.hollowhorizon.gradle.forge.ForgeSetup
import ru.hollowhorizon.gradle.minecraft
import ru.hollowhorizon.gradle.modImplementation
import ru.hollowhorizon.gradle.neoforge.NeoForgeSetup
import ru.hollowhorizon.gradle.setupMappings
import java.security.MessageDigest

plugins {
    idea
    java
    `maven-publish`
    id("architectury-plugin")
    id("dev.architectury.loom")
}

val modId: String by properties
val modName: String by properties
val modVersion: String by properties
val license: String by properties

val minecraftVersion = project.name.substringBeforeLast('-')
val modPlatform = project.name.substringAfterLast('-')
val runtimeProjectPath = ":runtime:${project.name}"
val bridgeProjectPath = ":bridge:${project.name}"
val bootstrapSourceRoot = rootProject.file("bootstrap/src/main")
val runtimeResourceRoot = rootProject.file("runtime/src/main/resources")
val ideaRunConfigurationsDir = rootProject.file(".idea/runConfigurations")
val compiledSourceRoot = layout.buildDirectory.dir("generated/stonecutter/main")
val compiledTestSourceRoot = layout.buildDirectory.dir("generated/stonecutter/test")

evaluationDependsOn(runtimeProjectPath)
evaluationDependsOn(bridgeProjectPath)

val runtimeShadowJar = project(runtimeProjectPath).tasks.named<Jar>("shadowJar")
val runtimeEmbeddedJar = project(runtimeProjectPath).tasks.named<Jar>("remapJar")
val runtimeMergeLang = project(runtimeProjectPath).tasks.named("mergeLang")
val runtimeMergedLangOutput = project(runtimeProjectPath).layout.buildDirectory.dir("generated/lang/$modId")
val devRuntimeJar = layout.buildDirectory.file("dev-runtime/HollowEngineRuntime.jar")
val bridgeJar = project(bridgeProjectPath).tasks.named<Jar>("jar")
val syncedRuntimeResourcesDir = layout.buildDirectory.dir("generated/runtime-bootstrap-resources")
val runtimeChecksumFile = layout.buildDirectory.file("generated/runtime/HollowEngineRuntime.sha256")
val loom = extensions["loom"] as LoomGradleExtensionImpl
val bootstrapMod = ModProject(
    modId = modId,
    modName = modName,
    modVersion = modVersion,
    license = license,
    entryPoints = mapOf(
        "main" to listOf("ru.hollowhorizon.hollowengine.bootstrap.fabric.HCFabricBootstrap::onCommonInitialize"),
        "client" to listOf("ru.hollowhorizon.hollowengine.bootstrap.fabric.HCFabricBootstrap::onClientInitialize"),
    ),
    dependencies = mapOf(),
    mixinConfigs = listOf("$modId.mixins.json", "$modId.bridge.mixins.json"),
    username = "TheHollowHorizon",
)

group = properties["mod_group"].toString()
version = modVersion
base.archivesName = "$modName-$modPlatform-$minecraftVersion"
val generateIdeRuns = true
extra["hollow.generateIdeRuns"] = generateIdeRuns

fun embedBootstrapLibrary(dependencyNotation: String) {
    val dependency = dependencies.add("implementation", dependencyNotation) {
        isTransitive = false
    }
    dependencies.add("include", dependency)
    if (modPlatform == "forge" || modPlatform == "neoforge") {
        dependencies.add("forgeRuntimeLibrary", dependency)
    }
}

val syncRuntimeResources by tasks.registering(Sync::class) {
    dependsOn(runtimeMergeLang)

    from(runtimeResourceRoot) {
        include("assets/**")
        include("data/**")
        include("internal/**")
        include("pack.mcmeta")
        include("$modId.accesswidener")

        exclude("fabric.mod.json")
        exclude("META-INF/**")
        exclude("$modId.mixins.json")
        exclude("architectury.common.marker")
    }

    from(runtimeMergedLangOutput) {
        into("assets/$modId/lang")
    }

    into(syncedRuntimeResourcesDir)
}

val cleanLegacyRunConfigurations by tasks.registering(Delete::class) {
    delete(
        fileTree(ideaRunConfigurationsDir) {
            include("HollowEngine_*_runtime_*.xml")
            include("Minecraft_*bridge*.xml")
            include("HollowEngine_*_bootstrap_*.xml")
        }
    )
}

val syncDevRuntimeJar by tasks.registering(Copy::class) {
    dependsOn(runtimeShadowJar)

    from(runtimeShadowJar.map { it.archiveFile.get().asFile })
    into(devRuntimeJar.map { it.asFile.parentFile })
    rename { "HollowEngineRuntime.jar" }
}

extensions.getByType<SourceSetContainer>().named("main").configure {
    java.setSrcDirs(listOf(bootstrapSourceRoot.resolve("java")))
    resources.setSrcDirs(
        listOf(
            compiledSourceRoot.map { it.dir("resources") },
            syncedRuntimeResourcesDir,
        )
    )
}

extensions.getByType<SourceSetContainer>().named("test").configure {
    java.setSrcDirs(listOf(rootProject.file("bootstrap/src/test/java")))
    resources.setSrcDirs(listOf(compiledTestSourceRoot.map { it.dir("resources") }))
}

StonecutterSetup.setup(project, true)
ResourcesSetup.setupResources(project, bootstrapMod, minecraftVersion, modPlatform)

loom.apply {
    silentMojangMappingsLicense()
    if (modPlatform == "neoforge") generateSrgTiny = false
    mixin.useLegacyMixinAp.set(true)
    mixin.add(extensions.getByType<SourceSetContainer>().named("main").get(), "${modId}.refmap.json")

    when (modPlatform) {
        "forge" -> forge {
            convertAccessWideners.set(true)
            mixinConfig("${modId}.mixins.json")
            mixinConfig("${modId}.bridge.mixins.json")
        }

        "neoforge" -> neoForge {}
    }
}

architectury {
    minecraft = minecraftVersion
    platformSetupLoomIde()
    when (modPlatform) {
        "fabric" -> fabric()
        "forge" -> forge()
        "neoforge" -> neoForge()
    }
}

loom.runConfigs.all {
    ideConfigGenerated(generateIdeRuns)
    appendProjectPathToConfigName.set(false)
    if (configName == null) {
        val suffix = when (environment) {
            "client" -> "Client"
            "server" -> "Server"
            "data" -> "Data"
            "dataClient" -> "Client Data"
            "dataServer" -> "Server Data"
            else -> environment?.replaceFirstChar { it.titlecase() } ?: name.replaceFirstChar { it.titlecase() }
        }
        name("$modName $suffix ($minecraftVersion/$modPlatform)")
    }

    if (environment == "client") {
        programArgs("--username=${bootstrapMod.username}")
    }

    property("hollowengine.runtimeJar", devRuntimeJar.get().asFile.absolutePath)
    property("sodium.checks.issue2561", "false")
    runDir("../../../run")
}

tasks.matching { task ->
    task.name in setOf("runClient", "runServer", "runGameTest", "runDatagen")
            || task.name == "configureClientLaunch"
            || task.name == "configureServerLaunch"
            || task.name == "configureLaunch"
}.configureEach {
    dependsOn(syncDevRuntimeJar)
}

tasks.named("classes") {
    dependsOn(syncDevRuntimeJar)
}

tasks.named("ideaSyncTask") {
    dependsOn(cleanLegacyRunConfigurations)
}

loom {
    val accessWidener = runtimeResourceRoot.resolve("$modId.accesswidener")
    if (accessWidener.exists()) {
        accessWidenerPath.set(accessWidener)
    }
}

repositories {
    maven("https://jitpack.io")
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.parchmentmc.org")
    mavenLocal()
    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {
    minecraft(minecraftVersion)
    "mappings"(loom.setupMappings(minecraftVersion))
    "compileOnly"("io.github.llamalad7:mixinextras-common:0.4.1")
    "annotationProcessor"("io.github.llamalad7:mixinextras-common:0.4.1")

    when (modPlatform) {
        "fabric" -> {
            modImplementation("net.fabricmc:fabric-loader:${FabricSetup.fabricLoader(minecraftVersion)}")
            modImplementation("net.fabricmc.fabric-api:fabric-api:${FabricSetup.fabricApi(minecraftVersion)}")
            when (minecraftVersion) {
                "1.21.1" -> "modCompileOnly"("mods:iris-fabric:1.8.8+mc1.21.1")
                "1.20.1" -> "modCompileOnly"("mods:iris:1.7.2")
            }
        }

        "forge" -> {
            "forge"("net.minecraftforge:forge:${ForgeSetup.forgeVersion(minecraftVersion)}")
            if (minecraftVersion == "1.20.1") {
                compileOnly("mods:oculus-mc1.20.1:1.7.0")
            }
        }

        "neoforge" -> {
            "neoForge"("net.neoforged:neoforge:${NeoForgeSetup.forgeVersion(minecraftVersion)}")
            if (minecraftVersion == "1.21.1") {
                compileOnly("mods:iris-neoforge:1.8.12+mc1.21.1")
            }
        }
    }

    when (modPlatform) {
        "fabric" -> embedBootstrapLibrary("io.github.llamalad7:mixinextras-fabric:0.4.1")
        "forge" -> embedBootstrapLibrary("io.github.llamalad7:mixinextras-forge:0.4.1")
        "neoforge" -> embedBootstrapLibrary("io.github.llamalad7:mixinextras-neoforge:0.4.1")
    }

    implementation(project(path = bridgeProjectPath, configuration = "namedElements"))
    compileOnly("org.spongepowered:mixin:0.8.7")
    compileOnly("org.ow2.asm:asm-tree:9.7")
    compileOnly("org.apache.logging.log4j:log4j-api:2.20.0")
    compileOnly("org.jetbrains:annotations:24.1.0")
}

val writeRuntimeChecksum by tasks.registering {
    dependsOn(runtimeEmbeddedJar)
    inputs.file(runtimeEmbeddedJar.flatMap { it.archiveFile })
    outputs.file(runtimeChecksumFile)

    doLast {
        val runtimeJar = runtimeEmbeddedJar.get().archiveFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")

        runtimeJar.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }

        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        val output = runtimeChecksumFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(hash)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("stonecutterGenerate")
    dependsOn(syncRuntimeResources)
    destinationDir = layout.buildDirectory.dir("generated/bootstrap-resources/main").get().asFile
}

tasks.named<ProcessResources>("processTestResources") {
    dependsOn("stonecutterGenerateTest")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("stonecutterGenerate")
    setSource(compiledSourceRoot.map { it.dir("java") })
}

tasks.named<JavaCompile>("compileTestJava") {
    dependsOn("stonecutterGenerateTest")
    setSource(compiledTestSourceRoot.map { it.dir("java") })
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("")
    dependsOn(syncRuntimeResources)
    dependsOn(runtimeEmbeddedJar)
    dependsOn(bridgeJar)
    dependsOn(writeRuntimeChecksum)

    from(bridgeJar.map { zipTree(it.archiveFile.get().asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }

    from(runtimeEmbeddedJar.map { it.archiveFile.get().asFile }) {
        into("META-INF/hollowengine/runtime")
        rename { "HollowEngineRuntime.jar" }
    }

    from(writeRuntimeChecksum.map { runtimeChecksumFile.get().asFile }) {
        into("META-INF/hollowengine/runtime")
        rename { "HollowEngineRuntime.sha256" }
    }
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("stonecutterGenerate")
    dependsOn(syncRuntimeResources)
}

tasks.named<RemapJarTask>("remapJar") {
    addNestedDependencies.set(true)
}

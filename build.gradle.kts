import groovy.lang.Closure
import net.fabricmc.loom.api.remapping.RemapperExtension
import net.fabricmc.loom.api.remapping.RemapperParameters
import net.fabricmc.loom.extension.LoomGradleExtensionImpl
import net.fabricmc.loom.extension.RemapperExtensionHolder
import net.fabricmc.tinyremapper.TinyRemapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    java
    `maven-publish`
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.7-SNAPSHOT"
    id("me.fallenbreath.yamlang") version "1.3.1"
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val userConfig = Properties()
val cfg = rootProject.file("user.properties")
val hasConfig = cfg.exists()
if (hasConfig) userConfig.load(cfg.inputStream())

val modId = fromProperties("mod_id")
val javaVersion = fromProperties("java_version")
val minecraftVersion = stonecutter.current.project.substringBeforeLast('-')
val modPlatform = stonecutter.current.project.substringAfterLast('-')
val license = fromProperties("license")
val modName = fromProperties("mod_name")
val modVersion = minecraftVersion + "-" + fromProperties("mod_version")
val imguiVersion: String by rootProject

loom {
    silentMojangMappingsLicense()
    if (modPlatform == "neoforge") (this as LoomGradleExtensionImpl).generateSrgTiny = false
    val awFile = rootProject.file("src/main/resources/$modId.accesswidener")
    if (awFile.exists()) accessWidenerPath = awFile

    mixin.useLegacyMixinAp = true
    mixin.add(sourceSets.main.get(), "$modId.refmap.json")

    when (modPlatform) {
        "forge" -> forge {
            convertAccessWideners = true
            mixinConfig("$modId.mixins.json")
            (this@loom as LoomGradleExtensionImpl).remapperExtensions.add(ForgeFixer)
        }

        "neoforge" -> neoForge {
        }
    }

    runConfigs.all {
        programArgs("--username=HollowHorizon")
        runDir("../../run")
    }
}

architectury {
    minecraft = minecraftVersion
    platformSetupLoomIde()
    if (modPlatform == "neoforge") (loom as LoomGradleExtensionImpl).generateSrgTiny = false
    common(modPlatform)
    when (modPlatform) {
        "fabric" -> fabric()
        "forge" -> forge()
        "neoforge" -> neoForge()
    }
}

base {
    archivesName = "$modName-$modPlatform-$modVersion"
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.0mods.team/releases")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://maven.parchmentmc.org")
    maven("https://maven.blamejared.com")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.0mods.team/")
    maven("https://jitpack.io")
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.cleanroommc.com")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    flatDir { dirs(rootDir.resolve("libs")) }
}

configurations.configureEach {
    resolutionStrategy {
        force("net.sf.jopt-simple:jopt-simple:5.0.4")
        force("org.ow2.asm:asm-commons:9.5")
    }
}

dependencies {
    setupLoader(modPlatform, minecraftVersion)

    compileOnly("org.spongepowered:mixin:0.8.7")

    // KOTLIN //
    dependency("com.github.HollowHorizon.HollowCore:HollowCore-$modPlatform-$minecraftVersion:2.0.15a:dev")
    dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0")
    dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.0")
    dependency("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
    dependency("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
    dependency("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.1")
    dependency("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")

    // SCRIPTING //
    dependency("org.jetbrains.kotlin:kotlin-scripting-jvm:2.0.0", true)
    dependency("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.0.0", true)
    dependency("org.jetbrains.kotlin:kotlin-script-runtime:2.0.0", true)
    dependency("org.jetbrains.kotlin:kotlin-compiler-embeddable-mcfriendly:2.0.0", true)
    dependency("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:2.0.0", true)
    dependency("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:2.0.0", true)
    dependency("org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.0", true)
    dependency("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0", true)
    dependency("org.jetbrains.kotlin:kotlin-scripting-common:2.0.0", true)

    dependency("net.fabricmc:tiny-remapper:0.10.4", true)
    dependency("net.fabricmc:mapping-io:0.6.1", true)
    dependency("gnu.trove:trove:1.0.2", true)

    // CONFIG //
    dependency("com.akuleshov7:ktoml-core-jvm:0.5.1")

    // IMGUI //
    dependency("team.0mods:imgui-app:$imguiVersion")
    dependency("team.0mods:imgui-binding:$imguiVersion")
    dependency("team.0mods:imgui-lwjgl3:$imguiVersion")
    dependency("team.0mods:imgui-binding-natives:$imguiVersion")

    // OTHER
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.anarres:jcpp:1.4.14")
    implementation("io.github.douira:glsl-transformer:2.0.1")
    implementation("ru.hollowhorizon:HollowEnginePlugin:1.1c")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xplugin=${rootDir.resolve("libs/HollowEnginePlugin-1.1c.jar")}")
    }
}

afterEvaluate {
    stonecutter {
        val platform = loom.platform.get().id()
        stonecutter.const("fabric", platform == "fabric")
        stonecutter.const("forge", platform == "forge")
        stonecutter.const("neoforge", platform == "neoforge")
    }
}


val buildAndCollect = tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(
        tasks.remapJar.get().archiveFile,
        tasks.remapSourcesJar.get().archiveFile,
        tasks.jar.get().archiveFile
    )
    into(rootProject.layout.buildDirectory.file("libs/$minecraftVersion"))
    dependsOn("build")
}

if (stonecutter.current.isActive) {
    rootProject.tasks.register("buildActive") {
        group = "project"
        dependsOn(buildAndCollect)
    }

    rootProject.tasks.register("runActive") {
        group = "project"
        dependsOn(tasks.named("runClient"))
    }
}

stonecutter {
    val j21 = eval(minecraftVersion, ">=1.20.5")
    java {
        withSourcesJar()
        sourceCompatibility = if (j21) JavaVersion.VERSION_21 else JavaVersion.VERSION_17
        targetCompatibility = if (j21) JavaVersion.VERSION_21 else JavaVersion.VERSION_17

        toolchain {
            languageVersion = JavaLanguageVersion.of(if (j21) 21 else 17)
        }
    }

    kotlin {
        jvmToolchain(if (j21) 21 else 17)
    }

    arrayOf("gltf", "glb", "bin", "ttf", "so", "dll", "dylib", "ser", "efkefc", "obj", "mtl")
        .forEach { stonecutter.exclude("*.$it") }
}

tasks.processResources {
    from(project.sourceSets.main.get().resources)
    when (modPlatform) {
        "forge" -> exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
        "neoforge" -> exclude("fabric.mod.json", "META-INF/mods.toml")
        "fabric" -> exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
    }

    filesMatching(
        listOf(
            "META-INF/mods.toml",
            "fabric.mod.json",
            "META-INF/neoforge.mods.toml",
            "$modId.mixins.json"
        )
    ) {
        expand(
            mapOf(
                "mod_version" to modVersion,
                "mod_id" to modId,
                "mod_name" to modName,
                "license" to license,
                "mc_version" to minecraftVersion
            )
        )
    }
}

fun secretProperty(name: String) = providers.environmentVariable(name).orElse(userConfig.getProperty(name)).get()


yamlang {
    targetSourceSets.set(mutableListOf(sourceSets["main"]))
    inputDir.set("assets/${modId}/lang")
}

if (hasConfig) publishing {
    publications {
        create<MavenPublication>(modName) {
            groupId = "ru.hollowhorizon"
            artifactId = "$modName-$modPlatform-$minecraftVersion"
            version = fromProperties("mod_version")

            from(components["java"])
        }
    }

    repositories {
        maven("https://maven.0mods.team/releases/") {
            credentials {
                username = secretProperty("MAVEN_USER")
                password = secretProperty("MAVEN_PASSWORD")
            }
        }
    }
}

fun DependencyHandlerScope.includes(vararg libraries: String) {
    for (library in libraries) {
        "include"(library)
    }
}

fun fromProperties(id: String) = rootProject.properties[id].toString()


class KClosure<T : Any?>(val function: T.() -> Unit) : Closure<T>(null, null) {
    fun doCall(it: T): T {
        function(it)
        return it
    }
}

fun <T : Any> closure(function: T.() -> Unit): Closure<T> {
    return KClosure(function)
}

object ForgeFixer : RemapperExtensionHolder(object : RemapperParameters {}) {
    override fun getRemapperExtensionClass(): Property<Class<out RemapperExtension<*>>> {
        throw UnsupportedOperationException("How did you call this method?")
    }

    override fun apply(
        tinyRemapperBuilder: TinyRemapper.Builder,
        sourceNamespace: String,
        targetNamespace: String,
        objectFactory: ObjectFactory,
    ) {
        // For some strange reason there are errors with source name mapping, but that doesn't stop me from compiling the jar, does it?
        tinyRemapperBuilder.ignoreConflicts(true)
    }
}

fun DependencyHandlerScope.dependency(path: String, addToJar: Boolean = false) {
    val dependency = implementation(path) {
        exclude("org.jetbrains.kotlin")
        exclude("org.ow2.asm")
    }
    if(addToJar) include(dependency)

    dependency.takeIf { modPlatform == "forge" || modPlatform == "neoforge" }?.let {
        "forgeRuntimeLibrary"(it)
    }
}

fun DependencyHandlerScope.minecraft(version: String) = "minecraft"("com.mojang:minecraft:$version")

@Suppress("UnstableApiUsage")
fun setupMappings(version: String): Dependency = loom.layered {
    officialMojangMappings()
    val mappingsVer = when (version) {
        "1.21" -> "2024.07.28"
        "1.20.1" -> "2023.09.03"
        "1.19.2" -> "2022.11.27"
        else -> throw IllegalStateException("Unknown mappings for version $version!")
    }
    parchment("org.parchmentmc.data:parchment-$version:$mappingsVer")
}

fun DependencyHandlerScope.setupLoader(loader: String, version: String) {
    minecraft(version)
    "mappings"(setupMappings(version))

    when (loader) {
        "fabric" -> {
            when (version) {
                "1.21" -> {
                    modImplementation("net.fabricmc:fabric-loader:0.15.11")
                    modImplementation("net.fabricmc.fabric-api:fabric-api:0.101.2+$version")
                    compileOnly("mods:sodium:0.6.0")
                    compileOnly("mods:iris:1.8.0")
                }

                "1.20.1" -> {
                    "modImplementation"("net.fabricmc:fabric-loader:0.15.11")
                    "modImplementation"("net.fabricmc.fabric-api:fabric-api:0.92.2+$version")
                    compileOnly("mods:sodium:0.5.11")
                    compileOnly("mods:iris:1.7.2")
                }

                "1.19.2" -> {
                    "modImplementation"("net.fabricmc:fabric-loader:0.15.11")
                    "modImplementation"("net.fabricmc.fabric-api:fabric-api:0.77.0+$version")
                    "modImplementation"("mods:sodium:0.4.4")
                    "modImplementation"("mods:iris:1.6.11")
                    dependency("org.joml:joml:1.10.8")
                }

                else -> throw IllegalStateException("Unsupported $loader version $version!")
            }
            dependency("io.github.classgraph:classgraph:4.8.173")
        }

        "forge" -> {
            when (version) {
                "1.21" -> "forge"("net.minecraftforge:forge:$version-51.0.8")
                "1.20.1" -> "forge"("net.minecraftforge:forge:$version-47.3.6")
                "1.19.2" -> {
                    dependency("org.joml:joml:1.10.8")
                    "forge"("net.minecraftforge:forge:$version-43.4.2")
                }

                else -> throw IllegalStateException("Unsupported $loader version $version!")
            }
            // Мне надоело каждый раз постоянно вырезать руками лишние jar из classpath
            if (minecraftVersion != "1.19.2") implementation("ru.hollowhorizon:forgefixer:1.0.0")
        }

        "neoforge" -> {
            when (version) {
                "1.21" -> "neoForge"("net.neoforged:neoforge:21.0.14-beta")
                else -> throw IllegalStateException("Unsupported $loader version $version!")
            }
        }
    }
}

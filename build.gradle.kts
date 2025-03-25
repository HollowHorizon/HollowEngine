plugins {
    idea
    java
    `maven-publish`
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("me.fallenbreath.yamlang")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val compiler_plugin: String by properties
val hollowcore: String by properties
val modId: String by properties
val modName: String by properties
val modVersion: String by properties
val license: String by properties

val container = ModContainer(
    minecraftVersion = stonecutter.current.project.substringBeforeLast('-'),
    modPlatform = stonecutter.current.project.substringAfterLast('-'),
    modId = modId, modName = modName, license = license, modVersion = modVersion,
)

val koolVersion: String by rootProject.properties
val kotlinVersion: String by properties
val imguiVersion: String by rootProject

group = properties["mod_group"].toString()
version = modVersion
base.archivesName = "$modName-${container.modPlatform}-${container.minecraftVersion}"

setupEnviroment(container, kotlinVersion, "TheHollowHorizon", includeKotlin = true)

repositories {
    maven("https://jitpack.io")
    maven("https://maven.blamejared.com/")

    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {
    install("ru.hollowhorizon:HollowCore-${container.modPlatform}-${container.minecraftVersion}:$hollowcore:dev", includeInJar = false, isMod = container.modPlatform == "forge")
    include("ru.hollowhorizon:HollowCore-${container.modPlatform}-${container.minecraftVersion}:$hollowcore")

    setupScripting()

    // CONFIG //
    install("com.akuleshov7:ktoml-core-jvm:0.5.1", false)

    // GRAPHICS //
    install("de.fabmax.kool:kool-core:$koolVersion", false)
    install("com.facebook:ktfmt:0.54")

    install("ru.hollowhorizon:HollowEnginePlugin:$compiler_plugin", true)

    install("ru.hollowhorizon:hollowengine-docs-jvm:1.0")

    kotlinCompilerPluginClasspath("ru.hollowhorizon:HollowEnginePlugin:$compiler_plugin")
    kotlinCompilerPluginClasspath("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

    val modPlatform = container.modPlatform
    val jei = "15.20.0.105"
    modCompileOnly("mezz.jei:jei-1.20.1-${modPlatform}-api:$jei")
    //modRuntimeOnly("mezz.jei:jei-1.20.1-${modPlatform}:$jei")
}

fun DependencyHandlerScope.setupScripting() {
    install("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-compiler-embeddable-mcfriendly:$kotlinVersion", true) // I Hate forge modules system...
    install("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion", true)
    install("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0", true)
    install("org.jetbrains.kotlin:kotlin-scripting-common:$kotlinVersion", true)
    install("gnu.trove:trove:1.0.2", true)
}

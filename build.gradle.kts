import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    java
    `maven-publish`
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("me.fallenbreath.yamlang")
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
}

val hollowcore: String by properties
val modId: String by properties
val modName: String by properties
val modVersion: String by properties
val license: String by properties

val container = ModProject(
    modId = modId,
    modName = modName,
    modVersion = modVersion,
    license = license,

    entryPoints = mapOf(
        "main" to listOf("ru.hollowhorizon.hollowengine.fabric.HCFabric::onCommonInitialize"),
        "client" to listOf("ru.hollowhorizon.hollowengine.fabric.HCFabric::onClientInitialize")
    ),
    dependencies = mapOf(),

    username = "TheHollowHorizon"
)

val koolVersion: String by rootProject.properties
val kotlinVersion: String by properties

setupEnviroment(container, kotlinVersion, includeKotlin = false)

repositories {
    maven("https://jitpack.io")
    maven("https://maven.blamejared.com/")

    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {
    ksp(project(":ksp"))

    setupScripting()

    // TODO: Может сделать чтобы он и зависимости зависимостей сразу в jar упаковывал? Но как?
    install("io.ktor:ktor-client-cio-jvm:3.1.3", true)
    install("io.ktor:ktor-client-content-negotiation-jvm:3.1.3", true)
    install("io.ktor:ktor-client-core-jvm:3.1.3", true)
    install("io.ktor:ktor-client-logging-jvm:3.1.3", true)
    install("io.ktor:ktor-events-jvm:3.1.3", true)
    install("io.ktor:ktor-http-cio-jvm:3.1.3", true)
    install("io.ktor:ktor-http-jvm:3.1.3", true)
    install("io.ktor:ktor-io-jvm:3.1.3", true)
    install("io.ktor:ktor-network-jvm:3.1.3", true)
    install("io.ktor:ktor-network-tls-jvm:3.1.3", true)
    install("io.ktor:ktor-serialization-jvm:3.1.3", true)
    install("io.ktor:ktor-serialization-kotlinx-json-jvm:3.1.3", true)
    install("io.ktor:ktor-serialization-kotlinx-jvm:3.1.3", true)
    install("io.ktor:ktor-sse-jvm:3.1.3", true)
    install("io.ktor:ktor-utils-jvm:3.1.3", true)
    install("io.ktor:ktor-websocket-serialization-jvm:3.1.3", true)
    install("io.ktor:ktor-websockets-jvm:3.1.3", true)

    // CONFIG //
    install("com.akuleshov7:ktoml-core-jvm:0.5.1", false)

    // GRAPHICS //
    install("de.fabmax.kool:kool-core:$koolVersion", false)
    include("com.github.weisj:jsvg:2.0.0")
    install("com.facebook:ktfmt:0.54")

    val modPlatform = stonecutter.modPlatform
    val jei = "15.20.0.105"
    modCompileOnly("mezz.jei:jei-1.20.1-${modPlatform}-api:$jei")

    compileOnly("lib:bbs:1.2.6-${stonecutter.minecraftVersion}-deobf")

    install("org.jetbrains.exposed:exposed-core:0.37.3")
    install("org.jetbrains.exposed:exposed-dao:0.37.3")
    install("org.jetbrains.exposed:exposed-jdbc:0.37.3")
    install("com.h2database:h2:1.4.200")

    install("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    install("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")
    install("org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin:$kotlinVersion")

    //modRuntimeOnly("mezz.jei:jei-1.20.1-${modPlatform}:$jei")
}

fun DependencyHandlerScope.setupScripting() {
    install("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion", true)
    install("libs:kotlin-compiler-embeddable-mcfriendly:2.2.0", true) // I Hate forge modules system...

    install("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:$kotlinVersion", true)
    install("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion", true)
    install("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0", true)
    install("org.jetbrains.kotlin:kotlin-scripting-common:$kotlinVersion", true)
    install("gnu.trove:trove:1.0.2", true)
    install("org.jetbrains.kotlin:kotlin-serialization-compiler-plugin:$kotlinVersion", true)

}


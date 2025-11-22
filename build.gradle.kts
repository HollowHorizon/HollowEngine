plugins {
    java
    `maven-publish`
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("me.fallenbreath.yamlang")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

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

val kotlinVersion: String by rootProject.properties
val koolVersion: String by rootProject.properties
val intellijVersion = "241.19416.19"

setupEnviroment(container, kotlinVersion, includeKotlin = false)

repositories {
    maven("https://jitpack.io")
    maven("https://maven.blamejared.com/")

    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {

    // CONFIG //
    install("net.peanuuutz.tomlkt:tomlkt:0.5.0", true)

    // GRAPHICS //
    install("de.fabmax.kool:kool-core:$koolVersion", true)
    install("com.github.weisj:jsvg:2.0.0")
    install("com.facebook:ktfmt:0.54")

    val modPlatform = stonecutter.modPlatform
    val jei = "15.20.0.105"
    modCompileOnly("mezz.jei:jei-1.20.1-${modPlatform}-api:$jei")

    compileOnly("lib:bbs:1.2.6-${stonecutter.minecraftVersion}-deobf")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    install("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    install("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    install("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    install("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    install("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
    install("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    install("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    install("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")




    //    implementation("org.jetbrains.intellij.deps.jna:jna:5.9.0.26") { isTransitive = false }
//    implementation("org.jetbrains.intellij.deps.jna:jna-platform:5.9.0.26") { isTransitive = false }
//    implementation("org.jetbrains.intellij.deps:log4j:1.2.17.2") { isTransitive = false }
//    implementation("org.jetbrains:annotations:24.1.0")
    // install("io.javaslang:javaslang:2.0.6")
}


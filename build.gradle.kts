

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import ru.hollowhorizon.gradle.*
import ru.hollowhorizon.gradle.tasks.GenerateAssetsTask


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
    maven("https://repo.mineinabyss.com/releases")
    mavenLocal()
    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {

    // CONFIG //
    install("net.peanuuutz.tomlkt:tomlkt:0.5.0", true)

    // GRAPHICS //
    install("de.fabmax.kool:kool-core-desktop:$koolVersion", true)
    install("com.github.weisj:jsvg:2.0.0")
    install("com.facebook:ktfmt:0.54")

    install("org.jetbrains:markdown:0.7.3")

    val modPlatform = stonecutter.modPlatform
    if(stonecutter.minecraftVersion == "1.20.1") {
        val jei = "15.20.0.105"
        modCompileOnly("mezz.jei:jei-1.20.1-${modPlatform}-api:$jei")
        compileOnly("lib:bbs:1.2.6-1.20.1-deobf")
    } else {
        val jei = "19.25.1.332"
        modCompileOnly("mezz.jei:jei-1.21.1-${modPlatform}-api:$jei")
        compileOnly("lib:bbs:1.2.6-1.20.1-deobf") // TODO: А BBS вообще будет на 1.21.1?
    }


    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("reflect"))

    install("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    install("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    install("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    install("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    install("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
    install("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    install("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    install("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    install("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion")
    install("org.jetbrains.kotlinx:atomicfu:0.30.0-beta")

    install("io.github.quillraven.fleks:Fleks:2.12")


//    install("de.fabmax.kool:kool-physics-desktop:$koolVersion", true)
//    install("de.fabmax:physx-jni:2.7.1")
//    install("de.fabmax:physx-jni:2.7.1:natives-windows")
//    install("de.fabmax:physx-jni:2.7.1:natives-linux")
//    install("de.fabmax:physx-jni:2.7.1:natives-macos")
//    install("de.fabmax:physx-jni:2.7.1:natives-macos-arm64")
}

val generateAssets by tasks.registering(GenerateAssetsTask::class) {
    generatedPackage.set("ru.hollowhorizon.hollowengine.generated")
    assetsDirectory.set(rootProject.file("src/main/resources/assets"))
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/assets/kotlin"))
}

sourceSets {
    main {
        java.srcDir(generateAssets.map { it.outputDirectory })
    }
}

tasks.withType<KotlinCompile> {
    dependsOn(generateAssets)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
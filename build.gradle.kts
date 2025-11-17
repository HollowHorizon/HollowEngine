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
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")

    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {

    // CONFIG //
    install("net.peanuuutz.tomlkt:tomlkt:0.5.0", true)

    // GRAPHICS //
    install("de.fabmax.kool:kool-core:$koolVersion", true)
    include("com.github.weisj:jsvg:2.0.0")
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
    install("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion", true)

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.4")

    listOf(
        "com.jetbrains.intellij.platform:util-rt",
        "com.jetbrains.intellij.platform:util-class-loader",
        "com.jetbrains.intellij.platform:util",
        "com.jetbrains.intellij.platform:util-base",
        "com.jetbrains.intellij.platform:util-xml-dom",
        "com.jetbrains.intellij.platform:core",
        "com.jetbrains.intellij.platform:core-impl",
        "com.jetbrains.intellij.platform:extensions",
        "com.jetbrains.intellij.java:java-frontback-psi",
        "com.jetbrains.intellij.java:java-frontback-psi-impl",
        "com.jetbrains.intellij.java:java-psi",
        "com.jetbrains.intellij.java:java-psi-impl",
        "com.jetbrains.intellij.platform:diagnostic",
        "com.jetbrains.intellij.platform:diagnostic-telemetry",
        "com.jetbrains.intellij.platform:util-progress",
        "com.jetbrains.intellij.platform:util-coroutines",
    )
        .forEach {
            install("$it:$intellijVersion")
            implementation("$it:$intellijVersion:sources") { isTransitive = false }
        }

    install("io.opentelemetry:opentelemetry-api:1.44.1")

    listOf(
        "org.jetbrains.kotlin:analysis-api-k2-for-ide",
        "org.jetbrains.kotlin:analysis-api-for-ide",
        "org.jetbrains.kotlin:low-level-api-fir-for-ide",
        "org.jetbrains.kotlin:analysis-api-platform-interface-for-ide",
        "org.jetbrains.kotlin:symbol-light-classes-for-ide",
        "org.jetbrains.kotlin:analysis-api-standalone-for-ide",
        "org.jetbrains.kotlin:analysis-api-impl-base-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-common-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-fir-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-fe10-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-ir-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-cli-for-ide",
        "org.jetbrains.kotlin:kotlin-scripting-common",
        "org.jetbrains.kotlin:kotlin-scripting-jvm",
        "org.jetbrains.kotlin:kotlin-scripting-jvm-host",
        "org.jetbrains.kotlin:kotlin-scripting-compiler-impl",
        "org.jetbrains.kotlin:kotlin-script-runtime",
        "org.jetbrains.kotlin:kotlin-scripting-compiler",
        "org.jetbrains.kotlin:assignment-compiler-plugin-for-ide",
    )
        .forEach {
            install("$it:$kotlinVersion")
            implementation("$it:$kotlinVersion:sources") { isTransitive = false }
        }

//     implementation("one.util:streamex:0.7.2")
     implementation("org.jetbrains.intellij.deps:asm-all:9.0")
    install("org.codehaus.woodstox:stax2-api:4.2.1")
    install("com.fasterxml:aalto-xml:1.3.0")
    install("com.github.ben-manes.caffeine:caffeine:2.9.3")
//    implementation("org.jetbrains.intellij.deps.jna:jna:5.9.0.26") { isTransitive = false }
//    implementation("org.jetbrains.intellij.deps.jna:jna-platform:5.9.0.26") { isTransitive = false }
    install("org.jetbrains.intellij.deps:trove4j:1.0.20200330")
//    implementation("org.jetbrains.intellij.deps:log4j:1.2.17.2") { isTransitive = false }
//    implementation("org.jetbrains.intellij.deps:jdom:2.0.6") { isTransitive = false }
    install("io.vavr:vavr:0.10.7")
    // install("io.javaslang:javaslang:2.0.6")
//    implementation("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.13-jb4") { isTransitive = false }
//    implementation("org.jetbrains:annotations:24.1.0")


}


import ru.hollowhorizon.gradle.StonecutterSetup

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

base.archivesName = "HollowEngineCompiler"
version = "1.0.0"

repositories {
    mavenCentral()
}

val kotlinVersion: String by rootProject.properties
val intellijVersion = "241.19416.19"
val runtime = checkNotNull(stonecutter.node.sibling("runtime"))
val runtimeProjectPath = if (name.contains('-')) ":runtime:$name" else runtime.hierarchy.toString()

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://repo.spongepowered.org/repository/maven-public/")

}

dependencies {
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    api(project(path = runtimeProjectPath, configuration = "namedElements"))

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.4") { isTransitive = false }

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
        "com.jetbrains.intellij.platform:analysis"
    )
        .forEach {
            implementation("$it:$intellijVersion") { isTransitive = false }
            implementation("$it:$intellijVersion:sources") { isTransitive = false }
        }

    implementation("io.opentelemetry:opentelemetry-api:1.44.1") { isTransitive = false }

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
            implementation("$it:$kotlinVersion") { isTransitive = false }
            implementation("$it:$kotlinVersion:sources") { isTransitive = false }
        }

    implementation("one.util:streamex:0.7.2") { isTransitive = false }
    implementation("org.jetbrains.intellij.deps:asm-all:9.0") { isTransitive = false }
    implementation("org.codehaus.woodstox:stax2-api:4.2.1") { isTransitive = false }
    implementation("com.fasterxml:aalto-xml:1.3.0") { isTransitive = false }
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3") { isTransitive = false }
    implementation("org.jetbrains.intellij.deps:trove4j:1.0.20200330") { isTransitive = false }
    implementation("io.vavr:vavr:0.10.7") { isTransitive = false }

    implementation("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.13-jb4") { isTransitive = false }
    implementation("org.jetbrains.intellij.deps:jdom:2.0.6") { isTransitive = false }


//    compileOnly("org.ow2.asm:asm:9.7") { isTransitive = false }
//    compileOnly("org.ow2.asm:asm-commons:9.7") { isTransitive = false }
//    compileOnly("org.ow2.asm:asm-tree:9.7") { isTransitive = false }
}

StonecutterSetup.setup(project, false)

tasks.named<Jar>("jar") {
    archiveClassifier.set("dev")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    mergeServiceFiles()

    dependencies {
        exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:.*"))
        exclude(dependency("org.ow2.asm:.*"))
        exclude(project(runtimeProjectPath))
    }

    // 1. FastUtil (Критично! Версия JetBrains несовместима с ванильной)
    relocate("it.unimi.dsi.fastutil", "ru.hollowhorizon.hollowengine.repackaged.fastutil")
    // 2. Caffeine (Minecraft тоже использует его, возможен конфликт версий)
    relocate("com.github.benmanes.caffeine", "ru.hollowhorizon.hollowengine.repackaged.caffeine")
    // 3. StreamEx (Вроде редкий, но для надежности)
    relocate("one.util.streamex", "ru.hollowhorizon.hollowengine.repackaged.streamex")
    // 4. VAVR (Аналогично)
    relocate("io.vavr", "ru.hollowhorizon.hollowengine.repackaged.vavr")
    // 5. Trove4j (В старых майнкрафтах он был, в новых может не быть или быть другой)
    relocate("gnu.trove", "ru.hollowhorizon.hollowengine.repackaged.gnu.trove")
}

tasks.build {
    dependsOn("shadowJar")
}


import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.gradleup.shadow")
}

val modVersion: String by rootProject.properties

base {
    archivesName = "HollowEngineCompiler"
}

version = modVersion

repositories {
    mavenCentral()
}

val kotlinVersion: String by rootProject.properties

val ijPlatform = "261.25134.147"
val ijJava = "261.25134.137"

val runtimeProjectPath = ":runtime"
val lightTreeClasses = project.projectDir.resolve("lightThree")
val patchedLightTreeJar = tasks.register<ShadowJar>("patchedLightTreeJar") {
    archiveBaseName.set("hollowengine-light-tree")
    archiveClassifier.set("patched")
    archiveVersion.set(kotlinVersion)
    from(lightTreeClasses)
    relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")
    relocate("org.jetbrains.kotlin.com.google", "com.google")
}

repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    maven("https://repo.spongepowered.org/repository/maven-public/")

}

dependencies {
    compileOnlyApi("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    api(project(path = runtimeProjectPath, configuration = "namedElements"))

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.4") { isTransitive = false }

    listOf(
        "com.jetbrains.intellij.platform:util-rt",
        "com.jetbrains.intellij.platform:util-class-loader",
        "com.jetbrains.intellij.platform:util",
        "com.jetbrains.intellij.platform:util-base",
        "com.jetbrains.intellij.platform:util-xml-dom",
        "com.jetbrains.intellij.platform:util-base-multiplatform",
        "com.jetbrains.intellij.platform:util-multiplatform",
        "com.jetbrains.intellij.platform:plugin-system-parser-impl",
        "com.jetbrains.intellij.platform:plugins-parser-impl:253.33813.35",
        "com.jetbrains.intellij.platform:util-jdom",
        "com.jetbrains.intellij.platform:core",
        "com.jetbrains.intellij.platform:core-impl",
        "com.jetbrains.intellij.platform:extensions",
        "com.jetbrains.intellij.platform:syntax-psi",
        "com.jetbrains.intellij.platform:syntax-i18-n",
        "com.jetbrains.intellij.platform:syntax",
        "com.jetbrains.intellij.platform:syntax-util",
        "com.intellij.platform:kotlinx-coroutines-core-jvm:1.10.1-intellij-5",
        "com.jetbrains.intellij.java:java-frontback-psi",
        "com.jetbrains.intellij.java:java-frontback-psi-impl",
        "com.jetbrains.intellij.java:java-indexing",
        "com.jetbrains.intellij.java:java-indexing-impl:261.25134.95",
        "com.jetbrains.intellij.java:java-psi",
        "com.jetbrains.intellij.java:java-psi-impl",
        "com.jetbrains.intellij.java:java-syntax",
        "com.jetbrains.intellij.java:java-impl:261.25134.95",
        "com.jetbrains.intellij.platform:diagnostic",
        "com.jetbrains.intellij.platform:diagnostic-telemetry",
        "com.jetbrains.intellij.platform:util-progress",
        "com.jetbrains.intellij.platform:util-coroutines",
        "com.jetbrains.intellij.platform:analysis"
    )
        .forEach {
            val version =
                when {
                    it.count(':'::equals) > 1 -> {
                        implementation(it) { isTransitive = false }
                        return@forEach
                    }

                    it.startsWith("com.jetbrains.intellij.platform") -> ijPlatform
                    else -> ijJava
                }

            implementation("$it:$version") { isTransitive = false }
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
    implementation("com.google.guava:guava:33.6.0-jre") { isTransitive = false }

    implementation("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.18-jb1") { isTransitive = false }
    implementation("org.benf:cfr:0.152") { isTransitive = false }

    runtimeOnly(files(patchedLightTreeJar.flatMap { it.archiveFile }))

    testImplementation(kotlin("test"))
    testRuntimeOnly(files(patchedLightTreeJar.flatMap { it.archiveFile }))
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    testRuntimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testRuntimeOnly("org.apache.logging.log4j:log4j-api:2.23.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("dev")
}

tasks.named<ProcessResources>("processResources") {
    filesMatching("hollowengine.addon.json") {
        expand("version" to version)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    mergeServiceFiles()

    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-script-runtime:.*"))
        exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:.*"))
        exclude(dependency("org.ow2.asm:.*"))
        exclude(project(runtimeProjectPath))
    }

    dependsOn(patchedLightTreeJar)
    from({ zipTree(patchedLightTreeJar.flatMap { it.archiveFile }) })

    relocate("com.github.benmanes.caffeine", "ru.hollowhorizon.hollowengine.repackaged.caffeine")
    relocate("one.util.streamex", "ru.hollowhorizon.hollowengine.repackaged.streamex")
    relocate("io.vavr", "ru.hollowhorizon.hollowengine.repackaged.vavr")
    relocate("gnu.trove", "ru.hollowhorizon.hollowengine.repackaged.gnu.trove")
    relocate("org.benf.cfr", "ru.hollowhorizon.hollowengine.repackaged.cfr")
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xopt-in=kotlin.contracts.ExperimentalContracts",
            "-Xopt-in=org.jetbrains.kotlin.analysis.api.KaExperimentalApi",
            "-Xopt-in=org.jetbrains.kotlin.analysis.api.KaImplementationDetail",
            "-Xopt-in=org.jetbrains.kotlin.analysis.api.KaPlatformInterface",
            "-Xopt-in=org.jetbrains.kotlin.analysis.api.KaIdeApi",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "2g"
}

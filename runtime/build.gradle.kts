
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    java
    `maven-publish`
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("com.gradleup.shadow")
    id("com.google.devtools.ksp")
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

val modId: String by properties
val modName: String by properties
val modVersion: String by properties
val modGroup: String by properties
val minecraftVersion: String by rootProject.properties
val enabledPlatforms = (rootProject.property("enabledPlatforms") as String).split(',').map(String::trim).toTypedArray()
val fabricLoaderVersion: String by rootProject.properties
val architecturyApiVersion: String by rootProject.properties
val parchmentVersion: String by rootProject.properties
val kotlinVersion: String by rootProject.properties
val koolVersion: String by rootProject.properties
val hollowcore: String by rootProject.properties

group = modGroup
version = modVersion
base.archivesName.set("${modName}Runtime")

apply(from = rootProject.file("gradle/assets-generator.gradle"))
apply(from = rootProject.file("gradle/lang-merge.gradle"))

val sourceSets = extensions.getByType<SourceSetContainer>()
val generatedAssetsDir = layout.buildDirectory.dir("generated/sources/assets/kotlin")
val mergedLangDir = layout.buildDirectory.dir("generated/lang/assets/$modId/lang")
val runtimeMappingAttribute = Attribute.of("hollowengine.runtime.mapping", String::class.java)
val shadowBundle = configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = true
    exclude(group = "org.jetbrains", module = "annotations")
    exclude(group = "org.checkerframework", module = "checker-qual")
    exclude(group = "com.google.code.findbugs", module = "jsr305")
    exclude(group = "com.google.errorprone", module = "error_prone_annotations")
}

configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME) {
    extendsFrom(shadowBundle)
}

configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) {
    extendsFrom(shadowBundle)
}

configurations.named(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME) {
    extendsFrom(shadowBundle)
}

fun DependencyHandler.addShadow(
    notation: String,
    configure: ExternalModuleDependency.() -> Unit = {},
) {
    add("shadowBundle", notation, configure)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.parchmentmc.org")
    maven("https://maven.blamejared.com/")
    maven("https://jitpack.io")
    maven("https://maven.google.com/")
    flatDir { dirs(rootProject.file("libs")) }

}

architectury {
    common(*enabledPlatforms)
}

loom {
    silentMojangMappingsLicense()

    val accessWidener = rootProject.file("runtime/src/main/resources/$modId.accesswidener")
    if (accessWidener.exists()) {
        accessWidenerPath.set(accessWidener)
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-$minecraftVersion:$parchmentVersion")
    })

    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("lib:iris-fabric:1.8.8+mc1.21.1")
    modImplementation("lib:sodium-fabric:0.6.13+mc1.21.1")

    implementation(project(":bridge"))
    ksp(project(":katari-binding-processor"))
    compileOnly("org.jetbrains:annotations:24.1.0")

    addShadow("net.peanuuutz.tomlkt:tomlkt:0.5.0")
    addShadow("com.github.weisj:jsvg:2.0.0")
    addShadow("org.jetbrains:markdown:0.7.3")
    addShadow("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    addShadow("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    addShadow("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
    addShadow("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    addShadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    addShadow("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion")
    addShadow("io.github.classgraph:classgraph:4.8.173")
    addShadow("lib:kermit-core-mcfriendly:2.0.4")
    addShadow("lib:katari-jvm:1.2.0")
    addShadow("lib:katari-stdlib-jvm:1.2.0")

    addShadow("androidx.compose.runtime:runtime:1.10.3")
    addShadow("androidx.compose.foundation:foundation:1.7.3")
    addShadow("androidx.collection:collection:1.4.0")
    addShadow("org.jetbrains.kotlinx:atomicfu:0.33.0")
    addShadow("org.jetbrains.kotlinx:kotlinx-io-core:0.9.0")
    addShadow("org.jetbrains.kotlinx:kotlinx-io-bytestring:0.9.0")

    addShadow("de.fabmax.kool:kool-core-desktop:$koolVersion") {
        exclude(group = "org.lwjgl")
        exclude(group = "org.lwjglx")
    }

    val jeiVersion = "19.25.1.332"
    add("modCompileOnly", "mezz.jei:jei-$minecraftVersion-fabric-api:$jeiVersion")
    compileOnly("lib:bbs:1.2.6-1.20.1-deobf")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.named("main").configure {
    java.setSrcDirs(
        listOf(
            rootProject.file("runtime/src/main/java"),
            generatedAssetsDir,
        )
    )
    resources.setSrcDirs(
        listOf(
            rootProject.file("runtime/src/main/resources"),
            mergedLangDir,
        )
    )
    resources.exclude("assets/$modId/lang/*.json")
}

sourceSets.named("test").configure {
    java.setSrcDirs(listOf(rootProject.file("runtime/src/test/kotlin")))
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("mergeLang")
    filesMatching(listOf("pack.mcmeta")) {
        expand(
            mapOf(
                "mod_name" to modName,
                "mod_id" to modId,
                "mod_version" to modVersion,
            )
        )
    }
    from(mergedLangDir) {
        into("assets/$modId/lang")
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn("generateAssets")
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn("generateAssets")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("generateAssets")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("dev-thin")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("dev")
    configurations = listOf(shadowBundle)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<RemapJarTask>("remapJar") {
    inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    archiveClassifier.set("fabric")
}

tasks.matching { it.name == "transformProductionFabric" || it.name == "transformProductionNeoForge" }.configureEach {
    enabled = false
}

val embeddedRuntimeElements = configurations.create("embeddedRuntimeElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    isVisible = false

    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(runtimeMappingAttribute, "named")
    }

    outgoing.artifact(tasks.named<ShadowJar>("shadowJar"))
}

val embeddedFabricRuntimeElements = configurations.create("embeddedFabricRuntimeElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    isVisible = false

    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(runtimeMappingAttribute, "fabric")
    }

    outgoing.artifact(tasks.named<RemapJarTask>("remapJar"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
            "-Xexplicit-context-arguments",
            "-Xintrinsic-const-evaluation",
            "-Xskip-prerelease-check",
        )
    }
}
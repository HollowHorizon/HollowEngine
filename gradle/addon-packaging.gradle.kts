import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.process.CommandLineArgumentProvider
import java.util.Properties

val hollowScriptsDirectory = "scripts"
val hollowCompiledScriptsPath = "META-INF/hollowengine/scripts"
val hollowScriptPrecompiler = "ru.hollowhorizon.hollowengine.common.compiler.tools.ScriptPrecompiler"
val hollowScriptCompilerConfiguration = "hollowengineScriptCompiler"

/**
 * Runtime a precompiled script artifact is valid for. It has to match what the game computes, otherwise
 * the artifact is ignored and the script is compiled again on first use.
 */
val neoforgeScriptIdentity = "neoforge/official/production"
val fabricScriptIdentity = "fabric/intermediary/production"

/** Whether a project ships the sources of its scripts next to the compiled artifacts. */
fun Project.shipsScriptSources(): Boolean =
    (findProperty("hollowengine.scripts.includeSources") as String?)?.toBooleanStrictOrNull() ?: true

fun Project.readHollowAddonId(): String {
    val descriptor = projectDir.resolve("src/main/resources/META-INF/plugin.properties")
    if (!descriptor.isFile) return name

    val properties = Properties()
    descriptor.inputStream().use(properties::load)
    return properties.getProperty("id")?.trim()?.takeIf(String::isNotEmpty) ?: name
}

/**
 * Everything the ahead-of-time script compiler needs to run: the compiler addon with its own
 * dependencies, plus this project's classes and runtime classpath, which is what the scripts compile
 * against.
 */
fun Project.hollowScriptCompilerClasspath(): FileCollection {
    val compilerProject = rootProject.project(":addons:compiler")
    val kotlinVersion = rootProject.property("kotlinVersion") as String
    val serializationVersion = rootProject.property("serializationVersion") as String
    val composeRuntimeVersion = rootProject.property("composeRuntimeVersion") as String
    val configuration = configurations.findByName(hollowScriptCompilerConfiguration)
        ?: configurations.create(hollowScriptCompilerConfiguration) {
            isCanBeResolved = true
            isCanBeConsumed = false
            isTransitive = true
        }.also { created ->
            // The packaged compiler, exactly the artifact the game loads. Taking the jar rather than the
            // project dependency keeps the IntelliJ repositories out of every addon build.
            dependencies.add(
                created.name,
                files(compilerProject.tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }),
            )
            // Deliberately not packaged into that jar, because the game already provides them.
            listOf(
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion",
                // Remapping rewrites Kotlin metadata alongside the bytecode.
                "org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
                "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion",
                // UI scripts are Compose, and the Compose plugin refuses to run without its runtime.
                "androidx.compose.runtime:runtime:$composeRuntimeVersion",
                "org.apache.logging.log4j:log4j-api:2.23.1",
                "org.apache.logging.log4j:log4j-core:2.23.1",
                "org.ow2.asm:asm-commons:9.7.1",
            ).forEach { notation -> dependencies.add(created.name, notation) }
        }
    val ownSources = extensions.getByType<SourceSetContainer>().named("main")
    return files(
        ownSources.map { it.output },
        // What the scripts themselves compile against: Minecraft, the engine runtime, Kotlin.
        ownSources.map { it.compileClasspath },
        configuration,
    )
}

/**
 * Compiles `src/main/resources/scripts` with the same compiler and remapping the game uses, so the
 * artifacts are usable by players who never install the compiler addon.
 */
fun Project.registerHollowScriptCompilation(
    variant: String,
    scriptsDirectory: File,
    namespace: String,
    fingerprint: String,
    identity: String,
    remap: Boolean,
): TaskProvider<JavaExec> {
    val outputDirectory = layout.buildDirectory.dir("hollowengine/scripts/$variant")
    val scriptFiles = fileTree(scriptsDirectory) { include("**/*.kts") }
    val gameVersion = rootProject.property("minecraftVersion") as String
    val mappings = rootProject.file("addons/compiler/src/main/resources/mappings-$gameVersion.tiny")
    return tasks.register<JavaExec>("compile${variant.replaceFirstChar(Char::titlecase)}Scripts") {
        group = "build"
        description = "Compiles this project's scripts for the $variant mapping namespace."
        mainClass.set(hollowScriptPrecompiler)
        classpath = hollowScriptCompilerClasspath()
        // The engine resolves its own directory relative to the working directory, and a build has no
        // business creating one next to the sources.
        workingDir = layout.buildDirectory.dir("hollowengine/precompiler").get().asFile
        onlyIf { !scriptFiles.isEmpty }
        inputs.files(scriptFiles).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("namespace", namespace)
        inputs.property("fingerprint", fingerprint)
        inputs.property("identity", identity)
        outputs.dir(outputDirectory)
        argumentProviders.add(CommandLineArgumentProvider {
            listOf(
                "--scripts", scriptsDirectory.absolutePath,
                "--output", outputDirectory.get().asFile.absolutePath,
                "--namespace", namespace,
                "--fingerprint", fingerprint,
                "--identity", identity,
                "--remap", remap.toString(),
                "--mappings", if (remap) mappings.absolutePath else "",
            )
        })
        doFirst {
            outputDirectory.get().asFile.deleteRecursively()
            workingDir.mkdirs()
        }
    }
}

/** Adds a namespace's scripts and their compiled artifacts to a jar. */
fun Jar.includeHollowScripts(scriptsDirectory: File, compiled: TaskProvider<JavaExec>) {
    if (project.shipsScriptSources()) {
        from(scriptsDirectory) { into(hollowScriptsDirectory) }
    }
    from(compiled) { into(hollowCompiledScriptsPath) }
}

fun isHostProvidedAddonLibrary(fileName: String): Boolean = listOf(
    "kotlin-stdlib",
    "kotlin-reflect",
    "kotlinx-coroutines",
    "koin-core",
    "slf4j-",
    "log4j-",
    "annotations-",
    "lwjgl",
    "jemalloc",
    "glfw",
    "openal",
    "opengl",
    "stb",
    "tinyfd",
    "shaderc",
    "vulkan",
    "jinput",
    "jna-",
    "jna-platform-",
    "netty-",
    "oshi-core",
).any(fileName::startsWith)

fun isHostNativeAddonLibrary(fileName: String): Boolean = listOf(
    "lwjgl",
    "jemalloc",
    "glfw",
    "openal",
    "opengl",
    "stb",
    "tinyfd",
    "shaderc",
    "vulkan",
    "jinput",
    "jna-",
    "jna-platform-",
    "netty-",
    "oshi-core",
).any(fileName.lowercase()::startsWith)

val addonLibraries = configurations.getByName("addonLibraries")
val addonRuntimeLibraries = configurations.getByName("addonRuntimeLibraries")
val addonBootstrapLibraries = configurations.getByName("addonBootstrapLibraries")

val processAddonResources = tasks.named<ProcessResources>("processResources") {
    filesMatching("META-INF/plugin.properties") {
        expand("version" to version)
    }
}
val namedClassesJar = tasks.named<Jar>("jar")
val intermediaryClassesJar = tasks.named<AbstractArchiveTask>("remapJar")

val scriptsDirectory = projectDir.resolve("src/main/resources/$hollowScriptsDirectory")
val addonNamespace = readHollowAddonId()
val compileNamedScripts = registerHollowScriptCompilation(
    variant = "named",
    scriptsDirectory = scriptsDirectory,
    namespace = addonNamespace,
    fingerprint = version.toString(),
    identity = neoforgeScriptIdentity,
    remap = false,
)
val compileIntermediaryScripts = registerHollowScriptCompilation(
    variant = "intermediary",
    scriptsDirectory = scriptsDirectory,
    namespace = addonNamespace,
    fingerprint = version.toString(),
    identity = fabricScriptIdentity,
    remap = true,
)

// Scripts and the artifacts compiled from them live inside the variant jar, because compiled script
// bytecode is remapped for one namespace exactly like the addon's own classes are.
val namedVariantJar = tasks.register<Jar>("namedVariantJar") {
    archiveClassifier.set("variant-named")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(zipTree(namedClassesJar.flatMap { it.archiveFile }))
    includeHollowScripts(scriptsDirectory, compileNamedScripts)
}
val intermediaryVariantJar = tasks.register<Jar>("intermediaryVariantJar") {
    archiveClassifier.set("variant-intermediary")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(zipTree(intermediaryClassesJar.flatMap { it.archiveFile }))
    includeHollowScripts(scriptsDirectory, compileIntermediaryScripts)
}

val addonJar = tasks.register<Jar>("addonJar") {
    dependsOn(processAddonResources, namedVariantJar, intermediaryVariantJar)
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes(
        "HollowEngine-Addon-Format" to "2",
        "HollowEngine-Variant-Common-Named" to "META-INF/hollowengine/variants/named.jar",
        "HollowEngine-Variant-Fabric-Intermediary" to "META-INF/hollowengine/variants/intermediary.jar",
        "HollowEngine-Variant-Neoforge-Official" to "META-INF/hollowengine/variants/named.jar",
    )
    from(processAddonResources) {
        exclude("$hollowScriptsDirectory/**")
    }
    from(namedVariantJar.flatMap { it.archiveFile }) {
        into("META-INF/hollowengine/variants")
        rename { "named.jar" }
    }
    from(intermediaryVariantJar.flatMap { it.archiveFile }) {
        into("META-INF/hollowengine/variants")
        rename { "intermediary.jar" }
    }
    from(addonLibraries) {
        into("hollowengine-addon-libs")
        exclude { details -> isHostProvidedAddonLibrary(details.file.name) }
    }
    from(addonRuntimeLibraries) {
        into("hollowengine-addon-libs")
        exclude { details -> isHostProvidedAddonLibrary(details.file.name) }
    }
    from(addonBootstrapLibraries) {
        into("hollowengine-addon-bootstrap")
        exclude { details -> isHostProvidedAddonLibrary(details.file.name) }
    }
    doFirst {
        val forbiddenLibraries = (addonLibraries + addonRuntimeLibraries + addonBootstrapLibraries)
            .files
            .filter { file -> isHostNativeAddonLibrary(file.name) }
        check(forbiddenLibraries.isEmpty()) {
            "Addons must use Minecraft's native libraries instead of bundling: " +
                forbiddenLibraries.joinToString { file -> file.name }
        }
    }
}

tasks.named("assemble") {
    dependsOn(addonJar)
}

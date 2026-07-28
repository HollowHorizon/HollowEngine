
import dev.architectury.plugin.ArchitectPluginExtension
import me.modmuss50.mpp.ReleaseType
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    base
    idea
    id("architectury-plugin") apply false
    id("dev.architectury.loom") apply false
    id("com.gradleup.shadow") apply false
    id("me.modmuss50.mod-publish-plugin")
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
    kotlin("plugin.compose") apply false
}

tasks.register<Sync>("buildAndCollect") {
    group = "build"
    into(layout.projectDirectory.dir("merged"))
}

fun Project.configureHollowAddon() {
    plugins.apply("base")
    plugins.apply("java-library")
    plugins.apply("org.jetbrains.kotlin.jvm")
    plugins.apply("org.jetbrains.kotlin.plugin.serialization")
    plugins.apply("architectury-plugin")
    plugins.apply("dev.architectury.loom")

    val modVersion = rootProject.property("modVersion") as String
    val modGroup = rootProject.property("modGroup") as String
    val minecraftVersion = rootProject.property("minecraftVersion") as String
    val parchmentVersion = rootProject.property("parchmentVersion") as String
    val fabricLoaderVersion = rootProject.property("fabricLoaderVersion") as String
    val kotlinVersion = rootProject.property("kotlinVersion") as String
    val serializationVersion = rootProject.property("serializationVersion") as String
    val koinVersion = rootProject.property("koinVersion") as String

    group = "$modGroup.addons"
    version = modVersion

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

    extensions.configure<ArchitectPluginExtension>("architectury") {
        common("fabric", "neoforge")
    }
    val loom = extensions.getByType<LoomGradleExtensionAPI>()
    loom.silentMojangMappingsLicense()

    fun Configuration.configureBundledLibraries() {
        isCanBeResolved = true
        isCanBeConsumed = false
        isTransitive = true
    }
    val addonLibraries = configurations.create("addonLibraries") {
        configureBundledLibraries()
    }
    val addonRuntimeLibraries = configurations.create("addonRuntimeLibraries") {
        configureBundledLibraries()
    }
    val addonBootstrapLibraries = configurations.create("addonBootstrapLibraries") {
        configureBundledLibraries()
    }
    configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME) {
        extendsFrom(addonLibraries, addonBootstrapLibraries)
    }
    configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) {
        extendsFrom(addonLibraries, addonRuntimeLibraries, addonBootstrapLibraries)
    }
    configurations.named(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME) {
        extendsFrom(addonLibraries, addonBootstrapLibraries)
    }
    configurations.named(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME) {
        extendsFrom(addonLibraries, addonRuntimeLibraries, addonBootstrapLibraries)
    }

    dependencies {
        add("minecraft", "com.mojang:minecraft:$minecraftVersion")
        add("mappings", loom.layered {
            officialMojangMappings()
            parchment("org.parchmentmc.data:parchment-$minecraftVersion:$parchmentVersion")
        })
        add("modCompileOnly", "net.fabricmc:fabric-loader:$fabricLoaderVersion")
        add("compileOnly", project(path = ":runtime", configuration = "namedElements"))
        add("testImplementation", project(path = ":runtime", configuration = "namedElements"))
        add("compileOnly", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
        add("compileOnly", "org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
        add("compileOnly", "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
        add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        add("compileOnly", "io.insert-koin:koin-core:$koinVersion")
        add("testImplementation", kotlin("test"))
    }

    val processAddonResources = tasks.named<ProcessResources>("processResources") {
        filesMatching("META-INF/plugin.properties") {
            expand("version" to version)
        }
    }
    val namedClassesJar = tasks.named<Jar>("jar") {
        archiveClassifier.set("classes-named")
        include("**/*.class")
    }
    val intermediaryClassesJar = tasks.named<RemapJarTask>("remapJar") {
        dependsOn(namedClassesJar)
        inputFile.set(namedClassesJar.flatMap { it.archiveFile })
        archiveClassifier.set("classes-intermediary")
    }

    val scriptsDirectory = projectDir.resolve("src/main/resources/$HOLLOW_SCRIPTS_DIRECTORY")
    val addonNamespace = readHollowAddonId()
    val compileNamedScripts = registerHollowScriptCompilation(
        variant = "named",
        scriptsDirectory = scriptsDirectory,
        namespace = addonNamespace,
        fingerprint = version.toString(),
        identity = NEOFORGE_SCRIPT_IDENTITY,
        remap = false,
    )
    val compileIntermediaryScripts = registerHollowScriptCompilation(
        variant = "intermediary",
        scriptsDirectory = scriptsDirectory,
        namespace = addonNamespace,
        fingerprint = version.toString(),
        identity = FABRIC_SCRIPT_IDENTITY,
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
            exclude("$HOLLOW_SCRIPTS_DIRECTORY/**")
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
    tasks.matching { it.name.startsWith("transformProduction") }.configureEach {
        enabled = false
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
    }
}

val HOLLOW_SCRIPTS_DIRECTORY = "scripts"
val HOLLOW_COMPILED_SCRIPTS_PATH = "META-INF/hollowengine/scripts"
val HOLLOW_SCRIPT_PRECOMPILER = "ru.hollowhorizon.hollowengine.common.compiler.tools.ScriptPrecompiler"
val HOLLOW_SCRIPT_COMPILER_CONFIGURATION = "hollowengineScriptCompiler"

/**
 * Runtime a precompiled script artifact is valid for. It has to match what the game computes, otherwise
 * the artifact is ignored and the script is compiled again on first use.
 */
val NEOFORGE_SCRIPT_IDENTITY = "neoforge/official/production"
val FABRIC_SCRIPT_IDENTITY = "fabric/intermediary/production"

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
    val configuration = configurations.findByName(HOLLOW_SCRIPT_COMPILER_CONFIGURATION)
        ?: configurations.create(HOLLOW_SCRIPT_COMPILER_CONFIGURATION) {
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
    val gameVersion = rootProject.property("minecraftVersion") as String
    val mappings = rootProject.file("addons/compiler/src/main/resources/mappings-$gameVersion.tiny")
    return tasks.register<JavaExec>("compile${variant.replaceFirstChar(Char::titlecase)}Scripts") {
        group = "build"
        description = "Compiles this project's scripts for the $variant mapping namespace."
        mainClass.set(HOLLOW_SCRIPT_PRECOMPILER)
        classpath = hollowScriptCompilerClasspath()
        // The engine resolves its own directory relative to the working directory, and a build has no
        // business creating one next to the sources.
        workingDir = layout.buildDirectory.dir("hollowengine/precompiler").get().asFile
        onlyIf { scriptsDirectory.isDirectory && scriptsDirectory.walkTopDown().any { it.extension == "kts" } }
        inputs.dir(scriptsDirectory).withPathSensitivity(PathSensitivity.RELATIVE).optional()
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
        from(scriptsDirectory) { into(HOLLOW_SCRIPTS_DIRECTORY) }
    }
    from(compiled) { into(HOLLOW_COMPILED_SCRIPTS_PATH) }
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

val specializedAddonProjectPaths = setOf(":addons:compiler")

fun Project.isHollowAddonProject(): Boolean =
    projectDir.resolve("build.gradle.kts").isFile &&
        projectDir.toPath().normalize().startsWith(rootProject.file("addons").toPath().normalize())

fun Project.usesHollowAddonConvention(): Boolean =
    isHollowAddonProject() && path !in specializedAddonProjectPaths

subprojects {
    plugins.apply("idea")

    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("build/${project.path.removePrefix(":").replace(':', '/')}")
    )

    idea {
        module {
            inheritOutputDirs = false
            outputDir = layout.buildDirectory.dir("idea/classes/main").get().asFile
            testOutputDir = layout.buildDirectory.dir("idea/classes/test").get().asFile
        }
    }

    if (usesHollowAddonConvention()) {
        configureHollowAddon()
    }
}

val enabledPlatforms = (property("enabledPlatforms") as String).split(',').map(String::trim).filter(String::isNotEmpty)
val modName = property("modName") as String
val modVersion = property("modVersion") as String
val minecraftVersion = property("minecraftVersion") as String
val modrinthProjectId = providers.gradleProperty("publish.modrinth")
val curseforgeProjectId = providers.gradleProperty("publish.curseforge")
val publishChangelogFile = layout.projectDirectory.file("CHANGELOG.md").asFile
val userProperties = Properties()
val userPropertiesFile = layout.projectDirectory.file("user.properties").asFile

if (userPropertiesFile.isFile) {
    userPropertiesFile.inputStream().use(userProperties::load)
}

fun userPropertyProvider(propertyName: String): Provider<String> {
    return providers.provider { userProperties.getProperty(propertyName) }
}

fun tokenProvider(
    gradlePropertyName: String,
    userPropertyName: String,
    vararg environmentNames: String,
): Provider<String> {
    val explicitProperty = providers.gradleProperty(gradlePropertyName)
        .orElse(userPropertyProvider(userPropertyName))
    return environmentNames.fold(explicitProperty) { provider, environmentName ->
        provider.orElse(providers.environmentVariable(environmentName))
    }
}

fun releaseTypeProvider(): Provider<ReleaseType> {
    return providers.gradleProperty("publish.releaseType")
        .orElse(providers.environmentVariable("RELEASE_TYPE"))
        .map { type ->
            when (type.lowercase()) {
                "alpha" -> ReleaseType.ALPHA
                "beta" -> ReleaseType.BETA
                "release", "stable" -> ReleaseType.STABLE
                else -> error("Unsupported publish.releaseType value '$type'. Use alpha, beta, or stable.")
            }
        }
        .orElse(ReleaseType.STABLE)
}

publishMods {
    changelog.set(providers.gradleProperty("publish.changelog").orElse(providers.provider {
        if (publishChangelogFile.exists()) {
            publishChangelogFile.readText().trim()
        } else {
            "$modName $modVersion"
        }
    }))
    version.set(modVersion)
    type.set(releaseTypeProvider())
    dryRun.set(providers.gradleProperty("publish.dryRun").map(String::toBooleanStrict).orElse(true))

    val curseforgeOptions = curseforgeOptions {
        accessToken.set(tokenProvider("publish.curseforge.token", "curseforgeToken", "CURSEFORGE_TOKEN", "CURSEFORGE_API_KEY"))
        projectId.set(curseforgeProjectId)
        minecraftVersions.add(minecraftVersion)
        javaVersions.add(JavaVersion.VERSION_21)
        clientRequired.set(true)
        serverRequired.set(true)
    }

    val modrinthOptions = modrinthOptions {
        accessToken.set(tokenProvider("publish.modrinth.token", "modrinthToken", "MODRINTH_TOKEN", "MODRINTH_API_KEY"))
        projectId.set(modrinthProjectId)
        minecraftVersions.add(minecraftVersion)
    }

    curseforge("curseforgeFabric") {
        from(curseforgeOptions)
        file(project(":bootstrap:fabric"))
        displayName.set("$modName $modVersion Fabric $minecraftVersion")
        modLoaders.add("fabric")
        requires("fabric-api")
    }

    curseforge("curseforgeNeoForge") {
        from(curseforgeOptions)
        file(project(":bootstrap:neoforge"))
        displayName.set("$modName $modVersion NeoForge $minecraftVersion")
        modLoaders.add("neoforge")
    }

    modrinth("modrinthFabric") {
        from(modrinthOptions)
        file(project(":bootstrap:fabric"))
        displayName.set("$modName $modVersion Fabric $minecraftVersion")
        modLoaders.add("fabric")
        requires("fabric-api")
    }

    modrinth("modrinthNeoForge") {
        from(modrinthOptions)
        file(project(":bootstrap:neoforge"))
        displayName.set("$modName $modVersion NeoForge $minecraftVersion")
        modLoaders.add("neoforge")
    }
}

tasks.named<Sync>("buildAndCollect") {
    enabledPlatforms.forEach { platform ->
        val projectPath = ":bootstrap:$platform"
        val bootstrapProject = project(projectPath)
        val remapJar = bootstrapProject.tasks.named<RemapJarTask>("remapJar")
        dependsOn(remapJar)
        from(remapJar.flatMap { it.archiveFile })
    }
    val compilerJar = project(":addons:compiler").tasks.named<Jar>("addonJar")
    dependsOn(compilerJar)
    from(compilerJar.flatMap { it.archiveFile })
}

val buildAddons = tasks.register<Sync>("buildAddons") {
    group = "build"
    description = "Builds every addon discovered under addons/ and collects universal jars."
    into(layout.buildDirectory.dir("addon-jars"))
}

gradle.projectsEvaluated {
    subprojects
        .filter(Project::usesHollowAddonConvention)
        .forEach { addonProject ->
            val addonJar = addonProject.tasks.named<Jar>("addonJar")
            buildAddons.configure {
                dependsOn(addonJar)
                from(addonJar.flatMap { it.archiveFile })
            }
        }

    val compilerJar = project(":addons:compiler").tasks.named<Jar>("addonJar")
    buildAddons.configure {
        dependsOn(compilerJar)
        from(compilerJar.flatMap { it.archiveFile })
    }
}

tasks.named("buildAndCollect") {
    dependsOn(buildAddons)
}


import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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
    id("com.google.devtools.ksp") apply false
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
        add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        add("compileOnly", "io.insert-koin:koin-core:$koinVersion")
        add("testImplementation", kotlin("test"))
    }

    tasks.named<ProcessResources>("processResources") {
        filesMatching("META-INF/plugin.properties") {
            expand("version" to version)
        }
    }
    val addonJar = tasks.named<Jar>("jar") {
        archiveClassifier.set("neoforge")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest.attributes["HollowEngine-Mapping-Namespace"] = "official"
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
    tasks.named<RemapJarTask>("remapJar") {
        dependsOn(addonJar)
        inputFile.set(addonJar.flatMap { it.archiveFile })
        archiveClassifier.set("fabric")
        manifest.attributes["HollowEngine-Mapping-Namespace"] = "intermediary"
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
    val compilerJar = project(":addons:compiler").tasks.named<ShadowJar>("shadowJar")
    dependsOn(compilerJar)
    from(compilerJar.flatMap { it.archiveFile })
}

val buildAddons = tasks.register<Sync>("buildAddons") {
    group = "build"
    description = "Builds every addon discovered under addons/ and collects platform jars."
    into(layout.buildDirectory.dir("addon-jars"))
}

gradle.projectsEvaluated {
    subprojects
        .filter(Project::usesHollowAddonConvention)
        .forEach { addonProject ->
            val addonJar = addonProject.tasks.named<Jar>("jar")
            val fabricJar = addonProject.tasks.named<RemapJarTask>("remapJar")
            buildAddons.configure {
                dependsOn(addonJar, fabricJar)
                from(addonJar.flatMap { it.archiveFile })
                from(fabricJar.flatMap { it.archiveFile })
            }
        }

    val compilerJar = project(":addons:compiler").tasks.named<ShadowJar>("shadowJar")
    buildAddons.configure {
        dependsOn(compilerJar)
        from(compilerJar.flatMap { it.archiveFile })
    }
}

tasks.named("buildAndCollect") {
    dependsOn(buildAddons)
}

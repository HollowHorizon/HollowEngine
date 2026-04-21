import org.gradle.jvm.tasks.Jar
import java.security.MessageDigest

plugins {
    id("architectury-plugin")
    id("dev.architectury.loom")
    id("com.github.johnrengelman.shadow")
}

val modId: String by properties
val modName: String by properties
val modVersion: String by properties
val modAuthor: String by rootProject.properties
val license: String by properties
val modGroup: String by properties
val minecraftVersion: String by rootProject.properties
val fabricLoaderVersion: String by rootProject.properties
val fabricApiVersion: String by rootProject.properties

layout.buildDirectory.set(
    rootProject.layout.projectDirectory.dir(
        "build/${
            project.path.removePrefix(":").replace(':', '/')
        }"
    )
)
group = modGroup
version = modVersion
base.archivesName.set("$modName-fabric-$minecraftVersion")

val sourceSets = extensions.getByType<SourceSetContainer>()
val embeddedRuntimeDir = layout.buildDirectory.dir("generated/embedded-runtime")
val generatedMetadataDir = layout.buildDirectory.dir("generated/mod-metadata")
val mergedRuntimeLangDir = rootProject.layout.projectDirectory.dir("build/runtime/generated/lang/")

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()

    val accessWidener = rootProject.file("runtime/src/main/resources/$modId.accesswidener")
    if (accessWidener.exists()) {
        accessWidenerPath.set(accessWidener)
    }

    mixin.useLegacyMixinAp.set(true)
    mixin.add(sourceSets.named("main").get(), "$modId-fabric.refmap.json")
}

configurations {
    create("common") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    named("compileClasspath") {
        extendsFrom(getByName("common"))
    }
    named("runtimeClasspath") {
        extendsFrom(getByName("common"))
    }
    named("developmentFabric") {
        extendsFrom(getByName("common"))
    }
    create("shadowBundle") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
}

val embeddedRuntime by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    mavenLocal()
    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("lib:iris-fabric:1.8.8+mc1.21.1")
    modImplementation("lib:sodium-fabric:0.6.13+mc1.21.1")
    modImplementation("io.github.llamalad7:mixinextras-fabric:0.4.1")

    implementation("org.anarres:jcpp:1.4.14")
    implementation("io.github.douira:glsl-transformer:2.0.1")

    add("embeddedRuntime", project(path = ":runtime", configuration = "embeddedRuntimeElements"))
    "common"(project(path = ":bridge", configuration = "namedElements")) { isTransitive = false }
    "shadowBundle"(project(path = ":bridge", configuration = "transformProductionFabric"))
}

val embedRuntimeJar = tasks.register("embedRuntimeJar") {
    group = "build"
    description = "Embeds the isolated runtime jar into bootstrap resources."

    inputs.files(embeddedRuntime)
    outputs.dir(embeddedRuntimeDir)

    doLast {
        val outputDir = embeddedRuntimeDir.get().dir("META-INF/hollowengine/runtime").asFile
        outputDir.mkdirs()

        val runtimeJar = embeddedRuntime.singleFile
        val targetJar = outputDir.resolve("HollowEngineRuntime.jar")
        runtimeJar.copyTo(targetJar, overwrite = true)

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(targetJar.readBytes())
            .joinToString("") { "%02x".format(it) }
        outputDir.resolve("HollowEngineRuntime.sha256").writeText(sha256)
    }
}

val generateFabricModMetadata = tasks.register<ProcessResources>("generateFabricModMetadata") {
    group = "build"
    description = "Generates the expanded fabric.mod.json for IDE and Loom consumption."

    val properties = mapOf(
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_version" to modVersion,
        "mod_author" to modAuthor,
        "license" to license,
        "minecraft_version" to minecraftVersion,
        "fabric_loader_version" to fabricLoaderVersion,
    )

    inputs.properties(properties)
    from(rootProject.file("bootstrap-fabric/src/main/templates"))
    into(generatedMetadataDir)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

sourceSets.named("main").configure {
    java.setSrcDirs(
        listOf(
            rootProject.file("bootstrap/src/main/java"),
            rootProject.file("bootstrap-fabric/src/main/java"),
        )
    )
    resources.setSrcDirs(
        listOf(
            rootProject.file("bootstrap/src/main/resources"),
            rootProject.file("bootstrap-fabric/src/main/resources"),
            rootProject.file("runtime/src/main/resources"),
            generatedMetadataDir,
            mergedRuntimeLangDir,
            embeddedRuntimeDir,
        )
    )
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(embedRuntimeJar, generateFabricModMetadata, ":runtime:mergeLang")
    filesMatching(listOf("$modId.mixins.json", "$modId.bridge.mixins.json", "pack.mcmeta")) {
        expand(
            mapOf(
                "mod_id" to modId,
                "mod_name" to modName,
                "mod_version" to modVersion,
                "mod_author" to modAuthor,
                "license" to license,
                "minecraft_version" to minecraftVersion,
                "fabric_loader_version" to fabricLoaderVersion,
            )
        )
    }
    exclude("META-INF/neoforge.mods.toml")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    dependsOn(embedRuntimeJar)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    configurations = listOf(project.configurations.getByName("shadowBundle"))
    archiveClassifier.set("dev-shadow")
}

val bootstrapDevJar = tasks.register<Jar>("bootstrapDevJar") {
    group = "build"
    description = "Packages the shaded bootstrap jar with the isolated runtime payload."

    dependsOn("shadowJar", embedRuntimeJar)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set(base.archivesName.get())
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("dev")

    from(
        zipTree(
            tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
                .flatMap { it.archiveFile })
    )
    from(embeddedRuntimeDir)
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    inputFile.set(bootstrapDevJar.flatMap { it.archiveFile })
}

tasks.named<JavaCompile>("compileJava") {
    setSource(sourceSets.named("main").get().java)
}

tasks.matching { it.name.startsWith("run") }.configureEach {
    dependsOn("classes")
}

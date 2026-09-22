import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.process.CommandLineArgumentProvider

val minecraftVersion = rootProject.property("minecraftVersion") as String
val sourceSets = extensions.getByType<SourceSetContainer>()
val payloadMappings = rootProject.file("addons/compiler/src/main/resources/mappings-$minecraftVersion.tiny")
val remapWorkDirectory = layout.buildDirectory.dir("hollowengine/remap")
val remapTableFile = remapWorkDirectory.map { it.file("payload-remap-fabric.tbl.gz") }

val fabricRelocation = rootProject.property("fabricRelocation") as String

val generatePayloadRemapTable = tasks.register<JavaExec>("generatePayloadRemapTable") {
    group = "build"
    description = "Generates Fabric remap table for runtime payload and verifies it against remapJar."

    val payloadJar = tasks.named<AbstractArchiveTask>("shadowJar").flatMap { it.archiveFile }
    val referenceJar = tasks.named<AbstractArchiveTask>("remapJar").flatMap { it.archiveFile }
    val mainSources = sourceSets.named("main")

    mainClass.set("ru.hollowhorizon.hollowengine.runtime.remap.PayloadRemapTool")
    classpath = files(mainSources.map { it.output }, mainSources.map { it.runtimeClasspath })
    maxHeapSize = "4g"

    inputs.file(payloadJar).withPathSensitivity(PathSensitivity.NONE)
    inputs.file(referenceJar).withPathSensitivity(PathSensitivity.NONE)
    inputs.file(payloadMappings).withPathSensitivity(PathSensitivity.NONE)
    inputs.property("relocation", fabricRelocation)
    outputs.file(remapTableFile)

    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--payload", payloadJar.get().asFile.absolutePath,
            "--mappings", payloadMappings.absolutePath,
            "--classpath", mainSources.get().compileClasspath.asPath,
            "--from", "named",
            "--to", "intermediary",
            "--output", remapTableFile.get().asFile.absolutePath,
            "--work", remapWorkDirectory.get().asFile.resolve("work").absolutePath,
            "--relocate", fabricRelocation,
            "--reference", referenceJar.get().asFile.absolutePath,
        )
    })
}

tasks.named("build") {
    dependsOn(generatePayloadRemapTable)
}

configurations.create("payloadRemapTableElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    isVisible = false

    outgoing.artifact(remapTableFile) {
        builtBy(generatePayloadRemapTable)
    }
}

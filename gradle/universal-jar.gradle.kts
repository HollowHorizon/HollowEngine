import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.process.CommandLineArgumentProvider
import java.util.zip.ZipFile

val modId = property("modId") as String
val modName = property("modName") as String
val modVersion = property("modVersion") as String
val minecraftVersion = property("minecraftVersion") as String

val relocationRules = (property("fabricRelocation") as String)
    .split(',')
    .filter(String::isNotBlank)
    .map { rule -> rule.substringBefore('=').trim() to rule.substringAfter('=').trim() }

val configRenames = mapOf(
    "$modId.mixins.json" to "$modId-fabric-common.mixins.json",
    "$modId.bridge.mixins.json" to "$modId-fabric-bridge.mixins.json",
)

fun architecturyRelocation(jar: File): List<Pair<String, String>> = ZipFile(jar).use { archive ->
    archive.entries().asSequence()
        .map { it.name.substringBefore('/') }
        .filter { it.startsWith("architectury_inject_") }
        .distinct()
        .map { it to "ru.hollowhorizon.hollowengine.loader.$it" }
        .toList()
}

val universalJarFile = layout.buildDirectory.file("universal/$modName-$minecraftVersion-$modVersion.jar")

val universalJar = tasks.register<JavaExec>("universalJar") {
    group = "build"
    description = "Merges NeoForge and Fabric jars into a single jar for both loaders."

    val neoforgeJar = project(":bootstrap:neoforge").tasks.named<AbstractArchiveTask>("remapJar").flatMap { it.archiveFile }
    val fabricJar = project(":bootstrap:fabric").tasks.named<AbstractArchiveTask>("remapJar").flatMap { it.archiveFile }
    val runtimeSources = project(":runtime").extensions.getByType<SourceSetContainer>().named("main")

    mainClass.set("ru.hollowhorizon.hollowengine.runtime.remap.UniversalJarTool")
    classpath = files(runtimeSources.map { it.output }, runtimeSources.map { it.runtimeClasspath })
    maxHeapSize = "2g"

    inputs.file(neoforgeJar).withPathSensitivity(PathSensitivity.NONE)
    inputs.file(fabricJar).withPathSensitivity(PathSensitivity.NONE)
    outputs.file(universalJarFile)

    argumentProviders.add(CommandLineArgumentProvider {
        val rules = relocationRules + architecturyRelocation(fabricJar.get().asFile)
        listOf(
            "--neoforge", neoforgeJar.get().asFile.absolutePath,
            "--fabric", fabricJar.get().asFile.absolutePath,
            "--output", universalJarFile.get().asFile.absolutePath,
            "--relocate", rules.joinToString(",") { (from, to) -> "$from=$to" },
            "--rename", configRenames.entries.joinToString(",") { (from, to) -> "$from=$to" },
            "--override", "fabric.mod.json,META-INF/MANIFEST.MF",
        )
    })
}

tasks.named<Sync>("buildAndCollect") {
    dependsOn(universalJar)
    from(universalJarFile)
}

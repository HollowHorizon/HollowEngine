import org.gradle.jvm.tasks.Jar
import java.util.jar.JarFile

val modName = property("modName") as String
val modVersion = property("modVersion") as String
val minecraftVersion = property("minecraftVersion") as String
val serializationVersion = property("serializationVersion") as String
val kotlinVersion = property("kotlinVersion") as String

val developmentJar = tasks.register<Jar>("developmentJar") {
    group = "build"
    description = "Packages the named engine API and its libraries for compiling external addons."
    archiveBaseName.set("$modName-$minecraftVersion")
    archiveVersion.set(modVersion)
    archiveClassifier.set("dev")
    destinationDirectory.set(layout.buildDirectory.dir("development"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    includeEmptyDirs = false

    val runtimeJar = project(":runtime").tasks.named<Jar>("shadowJar")
    val bridgeJar = project(":bridge").tasks.named<Jar>("jar")
    dependsOn(runtimeJar, bridgeJar)
    from(runtimeJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) })
    from(bridgeJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) })
    from(rootProject.file("LICENSE.MD"))
    // Kotlin package metadata is needed for top-level functions and type aliases.
    // Keep dependency notices alongside the classes copied from the runtime.
    include(
        "**/*.class", "META-INF/*.kotlin_module", "**/*.kotlin_builtins",
        "**/LICENSE*", "**/NOTICE*", "**/license*", "**/notice*",
    )
    exclude("module-info.class", "META-INF/versions/**/module-info.class")

    manifest.attributes(
        "HollowEngine-Version" to modVersion,
        "HollowEngine-Mappings" to "mojang",
        // The serialization compiler plugin reads these from the jar containing KSerializer.
        "Implementation-Version" to serializationVersion,
        "Require-Kotlin-Version" to kotlinVersion,
    )

    doLast {
        JarFile(archiveFile.get().asFile).use { archive ->
            val required = listOf(
                "ru/hollowhorizon/hollowengine/common/addons/HollowAddonEntrypoint.class",
                "ru/hollowhorizon/hollowengine/common/addons/HollowAddonContext.class",
                "ru/hollowhorizon/hollowengine/common/addons/HollowAddonApiKt.class",
                "ru/hollowhorizon/hollowengine/client/ui/screen/HollowComposeUiScreen.class",
                "ru/hollowhorizon/hollowengine/bootstrap/runtime/RuntimeBridge.class",
                "kotlinx/coroutines/CoroutineScope.class",
                "org/koin/core/Koin.class",
            )
            val missing = required.filter { archive.getEntry(it) == null }
            check(missing.isEmpty()) { "Development jar is missing compile-time API classes: $missing" }
            check(archive.entries().asSequence().any { it.name.endsWith(".kotlin_module") }) {
                "Development jar is missing Kotlin package metadata"
            }
            check(archive.getEntry("fabric.mod.json") == null && archive.getEntry("META-INF/neoforge.mods.toml") == null) {
                "Development jar must be a compile-only library"
            }
        }
    }
}

tasks.named("assemble") {
    dependsOn(developmentJar)
}

tasks.named<Sync>("buildAndCollect") {
    from(developmentJar)
}

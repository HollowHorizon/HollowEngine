import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.process.CommandLineArgumentProvider

val engineScriptsDirectory = rootProject.file("runtime/src/main/resources/scripts")
val engineScriptFiles = fileTree(engineScriptsDirectory) { include("**/*.kts") }

if (!engineScriptFiles.isEmpty) evaluationDependsOn(":addons:compiler")

val minecraftVersion = rootProject.property("minecraftVersion") as String
val kotlinVersion = rootProject.property("kotlinVersion") as String
val modVersion = property("modVersion") as String
val sourceSets = extensions.getByType<SourceSetContainer>()

fun registerEngineScriptCompilation(
    variant: String,
    identity: String,
    remap: Boolean,
): TaskProvider<JavaExec> {
    val outputDirectory = layout.buildDirectory.dir("hollowengine/scripts/$variant")
    val compilerProject = rootProject.project(":addons:compiler")
    val mappings = rootProject.file("addons/compiler/src/main/resources/mappings-$minecraftVersion.tiny")
    val toolClasspath = configurations.maybeCreate("hollowengineScriptCompiler$variant").apply {
        isCanBeResolved = true
        isCanBeConsumed = false
        isTransitive = true
    }
    dependencies.add(
        toolClasspath.name,
        files(compilerProject.tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }),
    )
    listOf(
        "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
        "org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion",
        "org.apache.logging.log4j:log4j-core:2.23.1",
        "org.ow2.asm:asm-commons:9.7.1",
    ).forEach { notation -> dependencies.add(toolClasspath.name, notation) }

    val mainSources = sourceSets.named("main")
    return tasks.register<JavaExec>("compile${variant.replaceFirstChar(Char::titlecase)}EngineScripts") {
        group = "build"
        description = "Compiles the engine's own scripts for the $variant mapping namespace."
        mainClass.set("ru.hollowhorizon.hollowengine.common.compiler.tools.ScriptPrecompiler")
        classpath = files(
            mainSources.map { it.output },
            mainSources.map { it.compileClasspath },
            mainSources.map { it.runtimeClasspath },
            toolClasspath,
        )
        workingDir = layout.buildDirectory.dir("hollowengine/precompiler").get().asFile
        inputs.files(engineScriptFiles).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.dir(outputDirectory)
        argumentProviders.add(CommandLineArgumentProvider {
            listOf(
                "--scripts", engineScriptsDirectory.absolutePath,
                "--output", outputDirectory.get().asFile.absolutePath,
                "--namespace", "hollowengine",
                "--fingerprint", modVersion,
                "--identity", identity,
                "--remap", remap.toString(),
                "--mappings", if (remap) mappings.absolutePath else "",
            )
        })
        doFirst {
            workingDir.mkdirs()
        }
    }
}

fun registerEngineScriptRemapping(
    variant: String,
    identity: String,
    input: TaskProvider<JavaExec>,
): TaskProvider<JavaExec> {
    val inputDirectory = layout.buildDirectory.dir("hollowengine/scripts/named")
    val outputDirectory = layout.buildDirectory.dir("hollowengine/scripts/$variant")
    val compilerProject = rootProject.project(":addons:compiler")
    val mappings = rootProject.file("addons/compiler/src/main/resources/mappings-$minecraftVersion.tiny")
    val toolClasspath = configurations.maybeCreate("hollowengineScriptCompiler$variant").apply {
        isCanBeResolved = true
        isCanBeConsumed = false
        isTransitive = true
    }
    dependencies.add(
        toolClasspath.name,
        files(compilerProject.tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }),
    )
    listOf(
        "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
        "org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion",
        "org.apache.logging.log4j:log4j-core:2.23.1",
        "org.ow2.asm:asm-commons:9.7.1",
    ).forEach { notation -> dependencies.add(toolClasspath.name, notation) }

    val mainSources = sourceSets.named("main")
    return tasks.register<JavaExec>("compile${variant.replaceFirstChar(Char::titlecase)}EngineScripts") {
        group = "build"
        description = "Remaps the engine's compiled scripts for the $variant mapping namespace."
        dependsOn(input)
        mainClass.set("ru.hollowhorizon.hollowengine.common.compiler.tools.ScriptArtifactRemapper")
        classpath = files(
            mainSources.map { it.output },
            mainSources.map { it.compileClasspath },
            mainSources.map { it.runtimeClasspath },
            toolClasspath,
        )
        workingDir = layout.buildDirectory.dir("hollowengine/precompiler").get().asFile
        inputs.files(engineScriptFiles).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(inputDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(mappings).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("identity", identity)
        outputs.dir(outputDirectory)
        argumentProviders.add(CommandLineArgumentProvider {
            listOf(
                "--scripts", engineScriptsDirectory.absolutePath,
                "--input", inputDirectory.get().asFile.absolutePath,
                "--output", outputDirectory.get().asFile.absolutePath,
                "--namespace", "hollowengine",
                "--fingerprint", modVersion,
                "--identity", identity,
                "--mappings", mappings.absolutePath,
            )
        })
        doFirst { workingDir.mkdirs() }
    }
}

val generateEngineScriptIndex = tasks.register("generateEngineScriptIndex") {
    val outputDirectory = layout.buildDirectory.dir("hollowengine/script-index")
    inputs.files(engineScriptFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDirectory)
    doLast {
        val index = engineScriptFiles.files
            .map { engineScriptsDirectory.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
            .sorted()
        val target = outputDirectory.get().asFile.resolve("META-INF/hollowengine/scripts.index")
        target.parentFile.mkdirs()
        target.writeText(index.joinToString("\n"))
    }
}

if (!engineScriptFiles.isEmpty) {
    val compileNamedEngineScripts =
        registerEngineScriptCompilation("named", "neoforge/official/production", remap = false)
    registerEngineScriptRemapping(
        "intermediary",
        "fabric/intermediary/production",
        compileNamedEngineScripts,
    )
}

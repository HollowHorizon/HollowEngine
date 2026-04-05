import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import ru.hollowhorizon.gradle.ModProject
import ru.hollowhorizon.gradle.install
import ru.hollowhorizon.gradle.minecraftVersion
import ru.hollowhorizon.gradle.modPlatform
import ru.hollowhorizon.gradle.setupEnviroment
import ru.hollowhorizon.gradle.tasks.GenerateAssetsTask
import ru.hollowhorizon.gradle.tasks.GenerateLangTask
import ru.hollowhorizon.gradle.tasks.MergeLangTask
import java.security.MessageDigest

val modId: String by properties
val modName: String by properties
val modVersion: String by properties
val license: String by properties

val container = ModProject(
    modId = modId,
    modName = modName,
    modVersion = modVersion,
    license = license,
    entryPoints = mapOf(
        "main" to listOf("ru.hollowhorizon.hollowengine.fabric.HCFabric::onCommonInitialize"),
        "client" to listOf("ru.hollowhorizon.hollowengine.fabric.HCFabric::onClientInitialize"),
    ),
    dependencies = mapOf(),
    username = "TheHollowHorizon",
)

val kotlinVersion: String by rootProject.properties
val koolVersion: String by rootProject.properties
val sourceRootPath = (extra.properties["hollow.mainSourceRoot"] as String?) ?: "runtime/src/main"
val authoringSourceRoot = rootProject.file(sourceRootPath)
val compiledSourceRoot = layout.buildDirectory.dir("generated/stonecutter/main")
val authoringTestSourceRoot = rootProject.file("runtime/src/test")
val compiledTestSourceRoot = layout.buildDirectory.dir("generated/stonecutter/test")
val embedRuntime = (extra.properties["hollow.embedRuntime"] as? Boolean) == true
val stonecutter = extensions["stonecutter"] as StonecutterBuildExtension
val minecraftVersion = project.minecraftVersion
val modPlatform = project.modPlatform
val bridgeModulePath = ":bridge:${project.name}"
val runtimeModulePath = if (project.path.startsWith(":runtime:")) project.path else ":runtime:${project.name}"
val runtimeChecksumFile = layout.buildDirectory.file("generated/runtime/HollowEngineRuntime.sha256")
val runtimeShadowJar = if (embedRuntime) runtimeModulePath?.let {
    evaluationDependsOn(it)
    project.project(it).tasks.named("shadowJar", Jar::class.java)
} else null

fun bundle(dependencyNotation: String, transitive: Boolean = false) {
    dependencies.add("implementation", dependencyNotation) {
        isTransitive = transitive
    }
    dependencies.add("bundledLibraries", dependencyNotation) {
        isTransitive = transitive
    }
}

setupEnviroment(container, kotlinVersion, includeKotlin = false)

repositories {
    maven("https://jitpack.io")
    maven("https://maven.blamejared.com/")
    mavenLocal()
    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {
    add("compileOnly", project.project(bridgeModulePath))

    bundle("net.peanuuutz.tomlkt:tomlkt:0.5.0")
    bundle("de.fabmax.kool:kool-core-desktop:$koolVersion")
    bundle("com.github.weisj:jsvg:2.0.0")
    bundle("com.facebook:ktfmt:0.54")
    bundle("org.jetbrains:markdown:0.7.3")

    val jeiConfiguration = if (modPlatform == "fabric") "modCompileOnly" else "compileOnly"
    if (minecraftVersion == "1.20.1") {
        val jei = "15.20.0.105"
        add(jeiConfiguration, "mezz.jei:jei-1.20.1-${modPlatform}-api:$jei")
        add("compileOnly", "lib:bbs:1.2.6-1.20.1-deobf")
    } else {
        val jei = "19.25.1.332"
        add(jeiConfiguration, "mezz.jei:jei-1.21.1-${modPlatform}-api:$jei")
        add("compileOnly", "lib:bbs:1.2.6-1.20.1-deobf")
    }

    add("testImplementation", kotlin("test"))
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.1")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    add("testImplementation", kotlin("reflect"))

    bundle("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    bundle("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    bundle("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    bundle("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    bundle("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
    bundle("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    bundle("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    bundle("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    bundle("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion")
    bundle("org.jetbrains.kotlinx:atomicfu:0.30.0-beta")
    bundle("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
    bundle("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.10.0-RC")
    bundle("com.squareup.okio:okio:3.9.0")
    bundle("it.krzeminski:snakeyaml-engine-kmp:4.0.1")
    bundle("net.thauvin.erik.urlencoder:urlencoder-lib:1.6.0")
    bundle("androidx.compose.runtime:runtime:1.10.3")

    val gearyVersion = "0.28"

    bundle("io.insert-koin:koin-core:4.0.0")
    bundle("co.touchlab:kermit-core-mcfriendly:2.0.4")
    bundle("androidx.collection:collection:1.4.0")
    bundle("org.roaringbitmap:RoaringBitmap:1.0.6")
    bundle("com.charleskorn.kaml:kaml:0.104.0")
    bundle("com.mineinabyss:geary-core:$gearyVersion")
    bundle("com.mineinabyss:geary-prefabs:$gearyVersion")
    bundle("com.mineinabyss:geary-actions:$gearyVersion")
    bundle("com.mineinabyss:geary-serialization:$gearyVersion")
    bundle("org.jetbrains.kotlinx:kotlinx-io-bytestring:0.8.2")

    when (modPlatform) {
        "fabric" -> bundle("io.github.llamalad7:mixinextras-fabric:0.4.1")
        "forge" -> bundle("io.github.llamalad7:mixinextras-forge:0.4.1")
        "neoforge" -> bundle("io.github.llamalad7:mixinextras-neoforge:0.4.1")
    }

    bundle("io.github.classgraph:classgraph:4.8.173")
}

val generateAssets by tasks.registering(GenerateAssetsTask::class) {
    generatedPackage.set("ru.hollowhorizon.hollowengine.generated")
    assetsDirectory.set(authoringSourceRoot.resolve("resources/assets"))
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/assets/kotlin"))
}

val mergeLang by tasks.registering(MergeLangTask::class) {
    val splitLangInput = authoringSourceRoot.resolve("lang")
    if (splitLangInput.exists()) {
        splitLangDirectory.set(splitLangInput)
    }

    val legacyLangInput = authoringSourceRoot.resolve("resources/assets/$modId/lang")
    if (legacyLangInput.exists()) {
        legacyLangDirectory.set(legacyLangInput)
    }

    outputDirectory.set(layout.buildDirectory.dir("generated/lang/$modId"))
}

val generateLang by tasks.registering(GenerateLangTask::class) {
    generatedPackage.set("ru.hollowhorizon.hollowengine.generated")
    langDirectory.set(mergeLang.flatMap { it.outputDirectory })
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/hollowengine/lang"))
    dependsOn(mergeLang)
}

val writeRuntimeChecksum = if (embedRuntime) {
    tasks.register("writeRuntimeChecksum") {
        val shadowJarTask = checkNotNull(runtimeShadowJar) { "Runtime shadow jar is required when embedding runtime" }
        dependsOn(shadowJarTask)
        inputs.file(shadowJarTask.flatMap { it.archiveFile })
        outputs.file(runtimeChecksumFile)

        doLast {
            val runtimeJar = shadowJarTask.get().archiveFile.get().asFile
            val digest = MessageDigest.getInstance("SHA-256")

            runtimeJar.inputStream().buffered().use { input: java.io.InputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }

            val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            val output = runtimeChecksumFile.get().asFile
            output.parentFile.mkdirs()
            output.writeText(hash)
        }
    }
} else {
    null
}

extensions.getByType<SourceSetContainer>().named("main").configure {
    java.setSrcDirs(
        listOf(
            authoringSourceRoot.resolve("java"),
            generateAssets.map { it.outputDirectory },
            generateLang.map { it.outputDirectory },
        )
    )
    resources.setSrcDirs(listOf(compiledSourceRoot.map { it.dir("resources") }))
    resources.exclude("assets/$modId/lang/*.json")
}

extensions.getByType<SourceSetContainer>().named("test").configure {
    java.setSrcDirs(
        listOf(
            authoringTestSourceRoot.resolve("java"),
            authoringTestSourceRoot.resolve("kotlin"),
        )
    )
    resources.setSrcDirs(listOf(authoringTestSourceRoot.resolve("resources")))
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("stonecutterGenerate")
    dependsOn(mergeLang)

    from(mergeLang.map { it.outputDirectory }) {
        into("assets/$modId/lang")
    }

    if (embedRuntime) {
        val shadowJarTask = checkNotNull(runtimeShadowJar)
        dependsOn(shadowJarTask)
        dependsOn(checkNotNull(writeRuntimeChecksum))

        from(shadowJarTask.map { it.archiveFile.get().asFile }) {
            into("META-INF/hollowengine/runtime")
            rename { "HollowEngineRuntime.jar" }
        }
        from(checkNotNull(writeRuntimeChecksum).map { runtimeChecksumFile.get().asFile }) {
            into("META-INF/hollowengine/runtime")
            rename { "HollowEngineRuntime.sha256" }
        }
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn("stonecutterGenerate")
    dependsOn(mergeLang)
    dependsOn(generateAssets)
    dependsOn(generateLang)
    setSource(
        listOf(
            compiledSourceRoot.map { it.dir("java") },
            generateAssets.map { it.outputDirectory },
            generateLang.map { it.outputDirectory },
        )
    )
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    dependsOn("stonecutterGenerateTest")
    setSource(listOf(compiledTestSourceRoot.map { it.dir("java") }))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("stonecutterGenerate")
    setSource(compiledSourceRoot.map { it.dir("java") })
}

tasks.named<JavaCompile>("compileTestJava") {
    dependsOn("stonecutterGenerateTest")
    setSource(compiledTestSourceRoot.map { it.dir("java") })
}

tasks.named<ProcessResources>("processTestResources") {
    dependsOn("stonecutterGenerateTest")

    from(compiledTestSourceRoot.map { it.dir("resources") })
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

import dev.architectury.plugin.ArchitectPluginExtension
import dev.kikugie.stonecutter.build.StonecutterBuild
import me.fallenbreath.yamlang.YamlangExtension
import net.fabricmc.loom.extension.LoomGradleExtensionImpl
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("UnstableApiUsage")
fun Project.setupEnviroment(
    container: ModContainer,
    kotlinVersion: String,
    userName: String = "Dev",
    includeKotlin: Boolean = false,
    enablePublishing: Boolean = false,
) {
    container.apply {
        val loom = extensions["loom"] as LoomGradleExtensionImpl
        val architectury = extensions["architectury"] as ArchitectPluginExtension
        val stonecutter = extensions["stonecutter"] as StonecutterBuild
        val java = extensions["java"] as JavaPluginExtension
        val kotlin = extensions["kotlin"] as KotlinJvmProjectExtension
        val publishing = extensions["publishing"] as PublishingExtension
        val sourceSets = extensions["sourceSets"] as SourceSetContainer

        isForgelike = modPlatform == "forge" || modPlatform == "neoforge"

        setupArchitectutyLoom(loom, this, this@setupEnviroment, sourceSets, userName, architectury)
        setupStonecutter(this@setupEnviroment, stonecutter, loom, this, java, kotlin)
        setupResources(this@setupEnviroment, sourceSets, this)
        if(enablePublishing) setupPublishing(publishing, this, this@setupEnviroment)

        configurations.configureEach {
            resolutionStrategy {
                force("net.sf.jopt-simple:jopt-simple:5.0.4")
                force("org.ow2.asm:asm-commons:9.5")
            }
        }

        repositories {
            mavenCentral()
            mavenLocal()
            flatDir { dirs(rootDir.resolve("libs")) }

            maven("https://repo.spongepowered.org/repository/maven-public/")
            maven("https://maven.0mods.team/releases")
            maven("https://maven.parchmentmc.org")
            maven("https://maven.architectury.dev/")
            maven("https://jitpack.io")
            maven("https://maven.neoforged.net/releases")
            maven("https://maven.fabricmc.net/")
            maven("https://cursemaven.com")
        }

        dependencies {
            setupLoader(loom, modPlatform, minecraftVersion)

            "compileOnly"("org.spongepowered:mixin:0.8.7")

            install("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion", includeKotlin)
            install("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion", includeKotlin)
            install("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion", includeKotlin)
            install("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion", includeKotlin)
            install("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0", includeKotlin)
            install("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0", includeKotlin)
            install("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0", includeKotlin)
            install("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1", includeKotlin)

            "implementation"("org.ow2.asm:asm:9.7")
            "implementation"("org.ow2.asm:asm-tree:9.7")
            "implementation"("org.anarres:jcpp:1.4.14")
            "implementation"("io.github.douira:glsl-transformer:2.0.1")
        }

        setupRenderDoc(this, this@setupEnviroment)
    }
}

private fun setupRenderDoc(modContainer: ModContainer, project: Project) {
    if (modContainer.modPlatform == "fabric") project.tasks.register<Exec>("run + RenderDoc") {
        val javaHome = Jvm.current().javaHome

        commandLine = listOf(
            "C:\\Program Files\\RenderDoc\\renderdoccmd.exe",
            "capture",
            "--opt-api-validation",
            "--opt-api-validation-unmute",
            "--opt-hook-children",
            "--wait-for-exit",
            "--working-dir",
            ".",
            "$javaHome/bin/java.exe",
            "-Xmx64m",
            "-Xms64m",
            "-Dorg.gradle.appname=gradlew",
            "-Dorg.gradle.java.home=$javaHome",
            "-classpath",
            project.rootProject.file("gradle/wrapper/gradle-wrapper.jar").absolutePath,
            "org.gradle.wrapper.GradleWrapperMain",
            ":1.20.1-fabric:runClient",
        )
    }
}

private fun setupPublishing(
    publishing: PublishingExtension,
    modContainer: ModContainer,
    project: Project,
) {
    publishing.apply {
        publications {
            create(modContainer.modName, MavenPublication::class.java) {
                groupId = "ru.hollowhorizon"
                artifactId = "${modContainer.modName}-${modContainer.modPlatform}-${modContainer.minecraftVersion}"
                version = modContainer.modVersion

                artifact(project.tasks.named<Jar>("remapJar"))
                artifact(project.tasks.named<Jar>("remapSourcesJar"))
                artifact(project.tasks.named<Jar>("jar"))
            }
        }

        repositories {
            if (System.getenv("MAVEN_PASSWORD") != null && false) maven {
                name = "GitHubPackages"
                url = project.uri("https://maven.pkg.github.com/HollowHorizon/${modContainer.modName}")

                credentials {
                    username = System.getenv("MAVEN_USER") // Имя пользователя
                    password = System.getenv("MAVEN_PASSWORD") // Токен
                }
            }
            if (System.getenv("MAVEN_PASSWORD_ZM") != null) maven {
                name = "ZeroModsMaven"
                url = project.uri("https://maven.0mods.team/releases")

                credentials {
                    username = System.getenv("MAVEN_USER_ZM") // Имя пользователя
                    password = System.getenv("MAVEN_PASSWORD_ZM") // Токен
                }
            }
            mavenLocal()
        }

    }
}

private fun setupResources(
    project: Project,
    sourceSets: SourceSetContainer,
    modContainer: ModContainer,
) {
    val yamlang = project.extensions["yamlang"] as YamlangExtension

    project.tasks.named<ProcessResources>("processResources") {
        from(sourceSets.main.get().resources)
        when (modContainer.modPlatform) {
            "forge" -> exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
            "neoforge" -> exclude("fabric.mod.json", "META-INF/mods.toml")
            "fabric" -> exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
        }

        val excl = if (modContainer.modPlatform == "fabric") "tsrg" else "tiny"

        val resFile = project.rootProject.file("src/main/resources")
        if (resFile.isDirectory) {
            resFile.listFiles()?.forEach {
                if (it.name.contains("mappings")) {
                    val splittedName = it.name.split('/').last()

                    // Check current minecraft version
                    if (!splittedName.split('-')[1].contains(modContainer.minecraftVersion)) exclude(splittedName)

                    // Check environment
                    if (splittedName.endsWith(excl)) exclude(splittedName)
                }
            }
        }

        exclude("architectury.common.marker")

        filesMatching(
            listOf(
                "META-INF/mods.toml",
                "fabric.mod.json",
                "META-INF/neoforge.mods.toml",
                "${modContainer.modId}.mixins.json"
            )
        ) {
            expand(
                mapOf(
                    "mod_version" to modContainer.modVersion,
                    "mod_id" to modContainer.modId,
                    "mod_name" to modContainer.modName,
                    "license" to modContainer.license,
                    "mc_version" to modContainer.minecraftVersion
                )
            )
        }
    }

    yamlang.apply {
        targetSourceSets.set(mutableListOf(sourceSets["main"]))
        inputDir.set("assets/${modContainer.modId}/lang")
    }
}

private fun setupStonecutter(
    project: Project,
    stonecutter: StonecutterBuild,
    loom: LoomGradleExtensionImpl,
    modContainer: ModContainer,
    java: JavaPluginExtension,
    kotlin: KotlinJvmProjectExtension,
) {
    project.afterEvaluate {
        stonecutter.apply {
            val platform = loom.platform.get().id()
            stonecutter.const("fabric", platform == "fabric")
            stonecutter.const("forge", platform == "forge")
            stonecutter.const("neoforge", platform == "neoforge")
        }
    }

    val buildAndCollect = project.tasks.register<Copy>("buildAndCollect") {
        group = "build"
        from(project.tasks.named<Jar>("remapJar").get().archiveFile)
        into(project.rootProject.layout.buildDirectory.file("../merged"))

        dependsOn("build")
    }

    if (stonecutter.current.isActive) {
        project.rootProject.tasks.register("buildActive") {
            group = "project"
            dependsOn(buildAndCollect)
        }

        project.rootProject.tasks.register("runActive") {
            group = "project"
            dependsOn(project.tasks.named("runClient"))
        }
    }

    stonecutter.apply {
        val j21 = eval(modContainer.minecraftVersion, ">=1.20.5")

        java.apply {
            withSourcesJar()
            sourceCompatibility = if (j21) JavaVersion.VERSION_21 else JavaVersion.VERSION_17
            targetCompatibility = if (j21) JavaVersion.VERSION_21 else JavaVersion.VERSION_17

            toolchain {
                languageVersion.set(JavaLanguageVersion.of(if (j21) 21 else 17))
            }
        }

        kotlin.apply {
            jvmToolchain(if (j21) 21 else 17)
        }
    }
}

private fun setupArchitectutyLoom(
    loom: LoomGradleExtensionImpl,
    modContainer: ModContainer,
    project: Project,
    sourceSets: SourceSetContainer,
    userName: String,
    architectury: ArchitectPluginExtension,
) {
    loom.apply {
        silentMojangMappingsLicense()
        if (modContainer.modPlatform == "neoforge") generateSrgTiny = false
        val awFile = project.rootProject.file("src/main/resources/${modContainer.modId}.accesswidener")
        if (awFile.exists()) accessWidenerPath.set(awFile)

        mixin.useLegacyMixinAp.set(true)
        mixin.add(sourceSets.main.get(), "${modContainer.modId}.refmap.json")

        when (modContainer.modPlatform) {
            "forge" -> forge {
                convertAccessWideners.set(true)
                mixinConfig("${modContainer.modId}.mixins.json")
            }

            "neoforge" -> neoForge {
            }
        }

        runConfigs.all {
            if (environment == "client") programArgs("--username=$userName")
            val javaVendor = System.getProperty("java.vendor")
            project.logger.info("Java vendor: $javaVendor")
            if(javaVendor.contains("JetBrains")) programArgs("-XX:+AllowEnhancedClassRedefinition")
            property("sodium.checks.issue2561", "false")
            runDir("../../run")
        }
    }

    architectury.apply {
        minecraft = modContainer.minecraftVersion
        platformSetupLoomIde()
        if (modContainer.modPlatform == "neoforge") loom.generateSrgTiny = false
        common(modContainer.modPlatform)
        when (modContainer.modPlatform) {
            "fabric" -> fabric()
            "forge" -> forge()
            "neoforge" -> neoForge()
        }
    }
}

private val SourceSetContainer.main get() = named<SourceSet>("main")
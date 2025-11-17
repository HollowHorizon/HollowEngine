import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.*

@Suppress("UnstableApiUsage")
fun Project.setupEnviroment(
    container: ModProject,
    kotlinVersion: String,
    includeKotlin: Boolean = false,
    vararg publications: Publication
) {

    val stonecutter = project.extensions["stonecutter"] as StonecutterBuildExtension

    container.apply {
        val minecraftVersion = stonecutter.minecraftVersion
        val modPlatform = stonecutter.modPlatform

        logger.warn("Loading environment for Minecraft $minecraftVersion on $modPlatform")

        group = properties["mod_group"].toString()
        version = container.modVersion
        (extensions["base"] as BasePluginExtension).archivesName = "$modName-$modPlatform-$minecraftVersion"

        isForgelike = modPlatform == "forge" || modPlatform == "neoforge"

        LoomSetup.setup(project, container, minecraftVersion, modPlatform)
        StonecutterSetup.setup(this@setupEnviroment, this)
        ResourcesSetup.setupResources(this@setupEnviroment, this, minecraftVersion, modPlatform)
        if(publications.isNotEmpty()) PublishingSetup.setupPublishing(this@setupEnviroment, this, minecraftVersion, modPlatform, *publications)

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
            maven("https://maven.parchmentmc.org")
            maven("https://maven.architectury.dev/")
            maven("https://jitpack.io")
            maven("https://maven.neoforged.net/releases")
            maven("https://maven.fabricmc.net/")
            maven("https://cursemaven.com")
        }

        dependencies {
            "compileOnly"("org.spongepowered:mixin:0.8.7")


            "implementation"("org.ow2.asm:asm:9.7")
            "implementation"("org.ow2.asm:asm-tree:9.7")
            "implementation"("org.anarres:jcpp:1.4.14")
            "implementation"("io.github.douira:glsl-transformer:2.0.1")
        }
    }
}
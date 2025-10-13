// import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import net.fabricmc.loom.extension.LoomGradleExtensionImpl
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

object StonecutterSetup {
    fun setup(
        project: Project,
        modProject: ModProject,
    ) {
        val stonecutter = project.extensions["stonecutter"] as StonecutterBuildExtension
        val java = project.extensions["java"] as JavaPluginExtension
        val kotlin = project.extensions["kotlin"] as KotlinJvmProjectExtension

        project.afterEvaluate {
            val loom = project.extensions["loom"] as LoomGradleExtensionImpl
            val platform = loom.platform.get().id()

            stonecutter.apply {
                constants["fabric"] = platform == "fabric"
                constants["forge"] = platform == "forge"
                constants["neoforge"] = platform == "neoforge"
            }
        }

        val buildAndCollect = project.tasks.register<Copy>("buildAndCollect") {
            group = "build"
            from(project.tasks.named<Jar>("remapJar").map { it.archiveFile.get().asFile })
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
            val j21 = eval(stonecutter.minecraftVersion, ">=1.20.5")

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
}
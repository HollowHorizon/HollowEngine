package ru.hollowhorizon.gradle

import dev.architectury.plugin.ArchitectPluginExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.extension.LoomGradleExtensionImpl
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import ru.hollowhorizon.gradle.fabric.FabricSetup
import ru.hollowhorizon.gradle.forge.ForgeSetup
import ru.hollowhorizon.gradle.neoforge.NeoForgeSetup

object LoomSetup {
    fun setup(
        project: Project,
        modProject: ModProject,
        minecraftVersion: String,
        modPlatform: String
    ) {
        val sourceSets = project.extensions["sourceSets"] as SourceSetContainer
        val loom = project.extensions["loom"] as LoomGradleExtensionImpl
        val architectury = project.extensions["architectury"] as ArchitectPluginExtension
        val generateIdeRuns = (project.findProperty("hollow.generateIdeRuns") as? String)?.toBooleanStrictOrNull()
            ?: ((project.extensions.extraProperties.properties["hollow.generateIdeRuns"] as? Boolean) ?: project == project.rootProject)

        loom.apply {
            silentMojangMappingsLicense()
            if (modPlatform == "neoforge") generateSrgTiny = false
            sourceSets.main.get().resources.srcDirs
                .map { it.resolve("${modProject.modId}.accesswidener") }
                .firstOrNull { it.exists() }
                ?.let(accessWidenerPath::set)

            mixin.useLegacyMixinAp.set(true)
            mixin.add(sourceSets.main.get(), "${modProject.modId}.refmap.json")

            when (modPlatform) {
                "forge" -> forge {
                    convertAccessWideners.set(true)
                    mixinConfig("${modProject.modId}.mixins.json")
                }

                "neoforge" -> neoForge {
                }
            }

            runConfigs.all {
                ideConfigGenerated(generateIdeRuns)
                appendProjectPathToConfigName.set(false)
                if (configName == null) {
                    val suffix = when (environment) {
                        "client" -> "Client"
                        "server" -> "Server"
                        "data" -> "Data"
                        "dataClient" -> "Client Data"
                        "dataServer" -> "Server Data"
                        else -> environment?.replaceFirstChar { it.titlecase() } ?: name.replaceFirstChar { it.titlecase() }
                    }
                    name("${modProject.modName} $suffix")
                }
                if (environment == "client") programArgs("--username=${modProject.username}")
                val javaVendor = System.getProperty("java.vendor")
                project.logger.info("Java vendor: $javaVendor")
                if (javaVendor.contains("JetBrains")) programArgs("-XX:+AllowEnhancedClassRedefinition")
                property("sodium.checks.issue2561", "false")
                runDir("../../run")
            }
        }

        architectury.apply {
            minecraft = minecraftVersion
            platformSetupLoomIde()
            if (modPlatform == "neoforge") loom.generateSrgTiny = false
            common(modPlatform)
            when (modPlatform) {
                "fabric" -> fabric()
                "forge" -> forge()
                "neoforge" -> neoForge()
            }
        }

        project.dependencies {
            setupLoader(loom, modPlatform, minecraftVersion)
        }
    }

    private fun DependencyHandlerScope.setupLoader(loom: LoomGradleExtensionAPI, loader: String, version: String) {
        minecraft(version)
        "mappings"(loom.setupMappings(version))

        "compileOnly"("io.github.llamalad7:mixinextras-common:0.4.1")
        "annotationProcessor"("io.github.llamalad7:mixinextras-common:0.4.1")

        when (loader) {
            "fabric" -> FabricSetup
            "forge" -> ForgeSetup
            "neoforge" -> NeoForgeSetup
            else -> error("Unsupported loader $loader")
        }.apply {
            setup(version)
        }
    }
}

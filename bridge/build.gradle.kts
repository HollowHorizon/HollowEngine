import net.fabricmc.loom.extension.LoomGradleExtensionImpl
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.tasks.Jar
import ru.hollowhorizon.gradle.StonecutterSetup
import ru.hollowhorizon.gradle.fabric.FabricSetup
import ru.hollowhorizon.gradle.forge.ForgeSetup
import ru.hollowhorizon.gradle.minecraft
import ru.hollowhorizon.gradle.modImplementation
import ru.hollowhorizon.gradle.neoforge.NeoForgeSetup
import ru.hollowhorizon.gradle.setupMappings

plugins {
    java
    id("architectury-plugin")
    id("dev.architectury.loom")
}

val modId: String by properties
extra["hollow.generateIdeRuns"] = false

base.archivesName = "HollowEngineBridge"
version = "1.0.0"

val minecraftVersion = project.name.substringBeforeLast('-')
val modPlatform = project.name.substringAfterLast('-')
val loom = extensions["loom"] as LoomGradleExtensionImpl

loom.apply {
    silentMojangMappingsLicense()
    if (modPlatform == "neoforge") generateSrgTiny = false
    mixin.useLegacyMixinAp.set(true)
    mixin.add(extensions.getByType(SourceSetContainer::class.java).named("main").get(), "${modId}.bridge.refmap.json")

    when (modPlatform) {
        "forge" -> forge {}
        "neoforge" -> neoForge {}
    }
}

loom.runConfigs.all {
    ideConfigGenerated(false)
}

tasks.matching {
    it.name in setOf("runClient", "runServer", "runGameTest", "runDatagen", "configureClientLaunch", "configureServerLaunch", "configureLaunch")
}.configureEach {
    enabled = false
}

architectury {
    minecraft = minecraftVersion
    platformSetupLoomIde()
    when (modPlatform) {
        "fabric" -> fabric()
        "forge" -> forge()
        "neoforge" -> neoForge()
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.parchmentmc.org")
    mavenLocal()
    flatDir { dirs(rootProject.file("libs")) }
}

dependencies {
    minecraft(minecraftVersion)
    "mappings"(loom.setupMappings(minecraftVersion))

    when (modPlatform) {
        "fabric" -> {
            modImplementation("net.fabricmc:fabric-loader:${FabricSetup.fabricLoader(minecraftVersion)}")
            when (minecraftVersion) {
                "1.21.1" -> "modCompileOnly"("mods:iris-fabric:1.8.8+mc1.21.1")
                "1.20.1" -> "modCompileOnly"("mods:iris:1.7.2")
            }
        }

        "forge" -> {
            "forge"("net.minecraftforge:forge:${ForgeSetup.forgeVersion(minecraftVersion)}")
            if (minecraftVersion == "1.20.1") {
                compileOnly("mods:oculus-mc1.20.1:1.7.0")
            }
        }

        "neoforge" -> {
            "neoForge"("net.neoforged:neoforge:${NeoForgeSetup.forgeVersion(minecraftVersion)}")
            if (minecraftVersion == "1.21.1") {
                compileOnly("mods:iris-neoforge:1.8.12+mc1.21.1")
            }
        }
    }

    compileOnly("org.spongepowered:mixin:0.8.7")
    compileOnly("org.jetbrains:annotations:24.1.0")
}

StonecutterSetup.setup(project, false)

tasks.named<ProcessResources>("processResources") {
    filesMatching("hollowengine.bridge.mixins.json") {
        expand("mod_id" to modId)
    }
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("")
}

tasks.named("buildAndCollect") {
    enabled = false
    setDependsOn(emptyList<Any>())
}

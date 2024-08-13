import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    java
    `maven-publish`
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val modId = fromProperties("mod_id")
val javaVersion = fromProperties("java_version")
val minecraftVersion = fromProperties("minecraft_version")
val parchmentVersion = fromProperties("parchment_version")
val modName = fromProperties("mod_name")
val modVersion = fromProperties("version")
val imguiVersion: String by project
val kotlinVersion: String by project

architectury {
    minecraft = minecraftVersion
}

subprojects {
    apply(plugin = "architectury-plugin")
    apply(plugin = "dev.architectury.loom")

    val loom: LoomGradleExtensionAPI = project.extensions.getByName<LoomGradleExtensionAPI>("loom")

    loom.apply {
        silentMojangMappingsLicense()
        val awFile = project(":common").file("src/main/resources/$modId.accesswidener")
        if (awFile.exists()) accessWidenerPath = awFile
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }

    repositories {
        maven("https://maven.parchmentmc.org")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://maven.blamejared.com")
        maven("https://thedarkcolour.github.io/KotlinForForge/")
        maven("https://maven.shedaniel.me/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.terraformersmc.com/releases/")
        maven("https://jitpack.io")
        maven("https://maven.neoforged.net/releases")
    }

    dependencies {
        "minecraft"("com.mojang:minecraft:${minecraftVersion}")

        @Suppress("unstableapiusage")
        "mappings"(loom.layered {
            officialMojangMappings()
            parchment("org.parchmentmc.data:parchment-1.20.6:$parchmentVersion") //бля парчмент нужен
        })
    }

    tasks.processResources {
        val replace = mapOf(
            "version" to version,
            "group" to project.group,
            "minecraft_version" to minecraftVersion,
            "forge_version" to fromProperties("forge_version"),
            "forge_loader_version_range" to fromProperties("forge_loader_version_range"),
            "forge_version_range" to fromProperties("forge_version_range"),
            "minecraft_version_range" to fromProperties("minecraft_version_range"),
            "fabric_version" to fromProperties("fabric_version"),
            "fabric_loader_version" to fromProperties("fabric_loader_version"),
            "mod_name" to modName,
            "mod_author" to fromProperties("mod_author"),
            "mod_id" to modId,
            "license" to fromProperties("license"),
            "description" to project.description,
            "neoforge_version" to fromProperties("neoforge_version"),
            "neoforge_loader_version_range" to fromProperties("neoforge_loader_version_range"),
            "credits" to fromProperties("credits"),
            "java_version" to fromProperties("java_version")
        )

        from(project(":common").sourceSets.main.get().resources)
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta", "*.mixins.json", "fabric.mod.json")) {
            expand(replace)
        }
        inputs.properties(replace)
    }

    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    group = fromProperties("group")
    version = "${minecraftVersion}-$modVersion"

    base {
        archivesName = modName
    }

    repositories {
        mavenCentral()
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://maven.0mods.team/releases")
        maven("https://maven.cleanroommc.com")
        flatDir { dirs("libs") }
    }

    dependencies {
        compileOnly("org.spongepowered:mixin:0.8.5")

        if(this@allprojects == findProject(":common")) {
            implementation("ru.hollowhorizon:HollowCore:${minecraftVersion}-1.0.0-dev")
        }

        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0")
        implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0-RC")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
        implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.0.0")
        implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.0.0")
        implementation("org.jetbrains.kotlin:kotlin-script-runtime:2.0.0")
        implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.0")
        implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:2.0.0")
        implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:2.0.0")
        implementation("org.ow2.asm:asm:9.7")

        implementation("net.fabricmc:tiny-remapper:0.10.4")
        implementation("net.fabricmc:mapping-io:0.6.1")

        implementation("com.akuleshov7:ktoml-core:0.5.1")

        implementation("team.0mods:imgui-app:$imguiVersion")
        implementation("team.0mods:imgui-binding:$imguiVersion")
        implementation("team.0mods:imgui-lwjgl3:$imguiVersion")
        implementation("team.0mods:imgui-binding-natives:$imguiVersion")

        implementation("org.anarres:jcpp:1.4.14")
        implementation("io.github.douira:glsl-transformer:2.0.1")
        implementation("org.ow2.asm:asm:9.7")
        implementation("io.github.classgraph:classgraph:4.8.173")
    }

    tasks {
        jar {
            from("LICENSE") {
                rename { "${it}_${modName}" }
            }
        }

        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release.set(javaVersion.toInt())
        }

        compileKotlin {
            useDaemonFallbackStrategy.set(false)
            compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
        }

        java {
            withSourcesJar()
        }
    }
}

fun fromProperties(id: String) = project.properties[id].toString()

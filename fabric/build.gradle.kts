@file:Suppress("UnstableApiUsage")

val minecraft_version: String by project
val mod_id: String by project
val fabric_loader_version: String by project
val fabric_version: String by project
val imguiVersion: String by project

plugins {
    id("multiloader-loader")
    id("fabric-loom").version("1.6-SNAPSHOT")
}

repositories {
    maven("https://maven.cleanroommc.com")
    maven("https://cursemaven.com")
    flatDir {
        dirs("libs")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())

    modImplementation("ru.hollowhorizon:hollowcore-fabric:$minecraft_version-1.0.3")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.11.0+kotlin.2.0.0")
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")
    modImplementation("sodium:sodium-fabric:0.5.8+mc1.20.6")
    modImplementation("iris:iris:1.7.0+mc1.20.6")

    implementation("org.anarres:jcpp:1.4.14")
    implementation("io.github.douira:glsl-transformer:2.0.1")
    implementation("org.ow2.asm:asm:9.7")

    fun shadow(dependency: String) {
        include(dependency)
        implementation(dependency)
    }

    shadow("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
    shadow("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    shadow("io.github.classgraph:classgraph:4.8.173")
    shadow("javassist:javassist:3.12.1.GA")
    shadow("io.github.spair:imgui-java-binding:$imguiVersion")
    shadow("io.github.spair:imgui-java-lwjgl3:$imguiVersion")
    shadow("io.github.spair:imgui-java-natives-windows:$imguiVersion")
    shadow("io.github.spair:imgui-java-natives-linux:$imguiVersion")
    shadow("io.github.spair:imgui-java-natives-macos:$imguiVersion")
}

loom {
    val aw = project(":common").file("src/main/resources/$mod_id.accesswidener")
    if (aw.exists()) accessWidenerPath.set(aw)

    mixin {
        defaultRefmapName.set("${mod_id}.refmap.json")
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("runs/client")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("runs/server")
        }
    }
}
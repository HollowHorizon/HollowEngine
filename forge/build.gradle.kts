plugins {
    id("multiloader-loader")
    id("net.minecraftforge.gradle").version("[6.0,6.2)")
    id("org.spongepowered.mixin").version("0.7-SNAPSHOT")
    id("org.parchmentmc.librarian.forgegradle").version("1.+")
}

val minecraft_version: String by project
val mod_name: String by project
val mod_id: String by project
val forge_version: String by project
val imguiVersion: String by project

base {
    archivesName = "$mod_name-forge-$minecraft_version"
}

mixin {
    add(sourceSets.main.get(), "$mod_id.refmap.json")

    config("$mod_id.mixins.json")
    config("$mod_id.forge.mixins.json")
}

minecraft {
    mappings("parchment", "2024.05.01-$minecraft_version")

    copyIdeResources = true

    val at = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) accessTransformer(at)

    runs {
        create("client") {
            workingDirectory(file("runs/client"))
            ideaModule("${rootProject.name}.${project.name}.main")
            taskName("Client")
            mods {
                create("modClientRun") {
                    source(sourceSets.main.get())
                }
            }
        }

        create("server") {
            workingDirectory(file("runs/server"))
            ideaModule("${rootProject.name}.${project.name}.main")
            taskName("Server")
            mods {
                create("modServerRun") {
                    source(sourceSets.main.get())
                }
            }
        }

        create("data") {
            workingDirectory(file("runs/data"))
            ideaModule("${rootProject.name}.${project.name}.main")
            args(
                "--mod", mod_id, "--all",
                "--output", file("src/generated/resources/"),
                "--existing", file("src/main/resources/")
            )
            taskName("Data")
            mods {
                create("modDataRun") {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")
repositories {
    mavenCentral()
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content { includeGroup("thedarkcolour") }
    }
    flatDir { dirs("libs") }
}
dependencies {
    minecraft("net.minecraftforge:forge:${minecraft_version}-${forge_version}")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")

    // Hack fix for now, force jopt-simple to be exactly 5.0.4 because Mojang ships that version, but some transtive dependencies request 6.0+
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4") { version { strictly("5.0.4") } }

    implementation("thedarkcolour:kotlinforforge:4.10.0")
    implementation("ru.hollowhorizon:hollowcore-forge:$minecraft_version-1.0.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.reflections:reflections:0.10.2")

    implementation("io.github.spair:imgui-java-binding:$imguiVersion")
    implementation("io.github.spair:imgui-java-lwjgl3:$imguiVersion")
    implementation("io.github.spair:imgui-java-natives-windows:$imguiVersion")
    implementation("io.github.spair:imgui-java-natives-linux:$imguiVersion")
    implementation("io.github.spair:imgui-java-natives-macos:$imguiVersion")
}

publishing {
    publications {
        create<MavenPublication>(mod_name) {
            fg.component(this)
        }
    }
}

sourceSets.forEach { src: SourceSet ->
    val dir = layout.buildDirectory.dir("sourceSets/${src.name}")
    src.output.setResourcesDir(dir)
    src.java.destinationDirectory = dir
}
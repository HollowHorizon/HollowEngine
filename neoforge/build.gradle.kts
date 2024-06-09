import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    id("multiloader-loader")
    id("net.neoforged.gradle.userdev").version("7.0.133")
}

val minecraft_version: String by project
val mod_name: String by project
val mod_id: String by project
val neoforge_version: String by project
val imguiVersion: String by project

val at = file("src/main/resources/META-INF/accesstransformer.cfg")
if (at.exists()) minecraft.accessTransformers.file(at)

val library by configurations.creating
configurations {
    implementation.get().extendsFrom(library)
}

runs {
    configureEach {
        modSource(project.sourceSets.main.get())
        dependencies {
            runtime(library)
        }
    }
    create("client") {
        systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
    }
    create("server") {
        systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        programArgument("--nogui")
    }

    create("gameTestServer") {
        systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
    }

    create("data") {
        programArguments.addAll(
            "--mod", mod_id, "--all", "--output",
            file("src/generated/resources/").absolutePath,
            "--existing", file("src/main/resources/").absolutePath
        )
    }
}

sourceSets.main.get().resources { srcDir("src/generated/resources") }

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
    implementation("net.neoforged:neoforge:$neoforge_version")

    implementation("thedarkcolour:kotlinforforge-neoforge:5.2.0")
    implementation("ru.hollowhorizon:hollowcore-neoforge:$minecraft_version-1.0.0")

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    library("org.reflections:reflections:0.10.2")

    library("io.github.spair:imgui-java-binding:$imguiVersion")
    library("io.github.spair:imgui-java-lwjgl3:$imguiVersion")
    library("io.github.spair:imgui-java-natives-windows:$imguiVersion")
    library("io.github.spair:imgui-java-natives-linux:$imguiVersion")
    library("io.github.spair:imgui-java-natives-macos:$imguiVersion")
}
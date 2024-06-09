plugins {
    id("multiloader-common")
    id("org.spongepowered.gradle.vanilla").version("0.2.1-SNAPSHOT")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val minecraft_version: String by project
val mod_id: String by project
val imguiVersion: String by project

minecraft {
    version(minecraft_version)

    val aw = file("src/main/resources/$mod_id.accesswidener")
    if (aw.exists()) accessWideners(aw)
}

repositories {
    mavenCentral()
    flatDir { dirs("libs") }
}

dependencies {
    compileOnly("org.spongepowered:mixin:0.8.5")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.reflections:reflections:0.10.2")

    implementation("ru.hollowhorizon:hollowcore-common:$minecraft_version-1.0.0")

    implementation("io.github.spair:imgui-java-binding:$imguiVersion")
    implementation("io.github.spair:imgui-java-lwjgl3:$imguiVersion")
    implementation("io.github.spair:imgui-java-natives-windows:$imguiVersion")
    implementation("io.github.spair:imgui-java-natives-linux:$imguiVersion")
    implementation("io.github.spair:imgui-java-natives-macos:$imguiVersion")
}

val commonJava: Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}

val commonKotlin: Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}

val commonResources: Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}

artifacts {
    add("commonJava", sourceSets.main.get().java.sourceDirectories.singleFile)
    sourceSets.main.get().kotlin.sourceDirectories.forEach { add("commonKotlin", it) }
    add("commonResources", sourceSets.main.get().resources.sourceDirectories.singleFile)
}


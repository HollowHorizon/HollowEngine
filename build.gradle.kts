plugins {
    base
    idea
    id("architectury-plugin") apply false
    id("dev.architectury.loom") apply false
    id("com.github.johnrengelman.shadow") apply false
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
}

tasks.register<Sync>("buildAndCollect") {
    group = "build"
    into(layout.projectDirectory.dir("merged"))
}

subprojects {
    plugins.apply("idea")

    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("build/${project.path.removePrefix(":").replace(':', '/')}")
    )

    idea {
        module {
            inheritOutputDirs = false
            outputDir = layout.buildDirectory.dir("idea/classes/main").get().asFile
            testOutputDir = layout.buildDirectory.dir("idea/classes/test").get().asFile
        }
    }
}

val enabledPlatforms = (property("enabledPlatforms") as String).split(',').map(String::trim).filter(String::isNotEmpty)

tasks.named<Sync>("buildAndCollect") {
    enabledPlatforms.forEach { platform ->
        val projectPath = ":bootstrap:$platform"
        val bootstrapProject = project(projectPath)
        val remapJar = bootstrapProject.tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar")
        dependsOn(remapJar)
        from(remapJar.flatMap { it.archiveFile })
    }
}

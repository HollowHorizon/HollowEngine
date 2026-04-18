plugins {
    base
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

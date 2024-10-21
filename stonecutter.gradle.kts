plugins {
    id("dev.kikugie.stonecutter")
    kotlin("jvm") version "2.0.0" apply false
    kotlin("plugin.serialization") version "2.0.0" apply false
    id("co.uzzu.dotenv.gradle") version "4.0.0"
    id("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.5.+" apply false
}

stonecutter active "1.20.1-fabric" /* [SC] DO NOT EDIT */

stonecutter registerChiseled tasks.register("chiseledBuildAndCollect", stonecutter.chiseled) {
    group = "project"
    ofTask("buildAndCollect")
}

stonecutter registerChiseled tasks.register("chiseledPublishMods", stonecutter.chiseled) {
    group = "project"
    ofTask("publishMods")
}

stonecutter registerChiseled tasks.register("chiseledPublishMaven", stonecutter.chiseled) {
    group = "project"
    ofTask("publish")
}

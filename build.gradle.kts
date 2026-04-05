import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.Copy

plugins {
    base
}

val bootstrapModulePath = if (name.contains('-')) ":bootstrap:$name" else null
val bootstrapBuildProjects = file("bootstrap/versions")
    .listFiles()
    ?.filter { it.isDirectory }
    ?.map { ":bootstrap:${it.name}" }
    .orEmpty()
val compilerBuildProjects = file("compiler/versions")
    .listFiles()
    ?.filter { it.isDirectory }
    ?.map { ":compiler:${it.name}" }
    .orEmpty()

if (bootstrapModulePath != null) {
    evaluationDependsOn(bootstrapModulePath)
    val compilerModulePath = if (file("compiler/versions/$name").exists()) ":compiler:$name" else null
    if (compilerModulePath != null) {
        evaluationDependsOn(compilerModulePath)
    }

    tasks.named("assemble") {
        dependsOn("$bootstrapModulePath:assemble")
        if (compilerModulePath != null) dependsOn("$compilerModulePath:assemble")
    }

    tasks.named("build") {
        dependsOn("$bootstrapModulePath:build")
        if (compilerModulePath != null) dependsOn("$compilerModulePath:build")
    }

    tasks.register<Copy>("buildAndCollect") {
        group = "build"
        val mergedDir = rootProject.layout.projectDirectory.dir("merged")
        into(mergedDir)

        val bootstrapRemapJar = project(bootstrapModulePath).tasks.named("remapJar")
        dependsOn(bootstrapRemapJar)
        from(bootstrapRemapJar)

        if (compilerModulePath != null) {
            val compilerShadowJar = project(compilerModulePath).tasks.named("shadowJar")
            dependsOn(compilerShadowJar)
            from(compilerShadowJar)
        }

        doFirst {
            delete(
                fileTree(mergedDir) {
                    include("HollowEngineBridge-*.jar")
                    include("HollowEngineRuntime-*.jar")
                }
            )
        }
    }

    tasks.register("runActive") {
        group = "project"
        dependsOn("$bootstrapModulePath:runActive")
    }
} else {
    bootstrapBuildProjects.forEach(::evaluationDependsOn)
    compilerBuildProjects.forEach(::evaluationDependsOn)

    tasks.named("assemble") {
        dependsOn(bootstrapBuildProjects.map { "$it:assemble" })
        dependsOn(compilerBuildProjects.map { "$it:assemble" })
    }

    tasks.named("build") {
        dependsOn(bootstrapBuildProjects.map { "$it:build" })
        dependsOn(compilerBuildProjects.map { "$it:build" })
    }

    tasks.register<Sync>("buildAndCollect") {
        group = "build"
        into(layout.projectDirectory.dir("merged"))

        bootstrapBuildProjects.forEach { path ->
            val remapJar = project(path).tasks.named("remapJar")
            dependsOn(remapJar)
            from(remapJar)
        }

        compilerBuildProjects.forEach { path ->
            val shadowJar = project(path).tasks.named("shadowJar")
            dependsOn(shadowJar)
            from(shadowJar)
        }
    }
}

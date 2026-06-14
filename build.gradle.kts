
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import me.modmuss50.mpp.ReleaseType
import net.fabricmc.loom.task.RemapJarTask
import java.util.*

plugins {
    base
    idea
    id("architectury-plugin") apply false
    id("dev.architectury.loom") apply false
    id("com.gradleup.shadow") apply false
    id("com.google.devtools.ksp") apply false
    id("me.modmuss50.mod-publish-plugin")
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
    kotlin("plugin.compose") apply false
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
val modName = property("modName") as String
val modVersion = property("modVersion") as String
val minecraftVersion = property("minecraftVersion") as String
val modrinthProjectId = providers.gradleProperty("publish.modrinth")
val curseforgeProjectId = providers.gradleProperty("publish.curseforge")
val publishChangelogFile = layout.projectDirectory.file("CHANGELOG.md").asFile
val userProperties = Properties()
val userPropertiesFile = layout.projectDirectory.file("user.properties").asFile

if (userPropertiesFile.isFile) {
    userPropertiesFile.inputStream().use(userProperties::load)
}

fun userPropertyProvider(propertyName: String): Provider<String> {
    return providers.provider { userProperties.getProperty(propertyName) }
}

fun tokenProvider(
    gradlePropertyName: String,
    userPropertyName: String,
    vararg environmentNames: String,
): Provider<String> {
    val explicitProperty = providers.gradleProperty(gradlePropertyName)
        .orElse(userPropertyProvider(userPropertyName))
    return environmentNames.fold(explicitProperty) { provider, environmentName ->
        provider.orElse(providers.environmentVariable(environmentName))
    }
}

fun releaseTypeProvider(): Provider<ReleaseType> {
    return providers.gradleProperty("publish.releaseType")
        .orElse(providers.environmentVariable("RELEASE_TYPE"))
        .map { type ->
            when (type.lowercase()) {
                "alpha" -> ReleaseType.ALPHA
                "beta" -> ReleaseType.BETA
                "release", "stable" -> ReleaseType.STABLE
                else -> error("Unsupported publish.releaseType value '$type'. Use alpha, beta, or stable.")
            }
        }
        .orElse(ReleaseType.STABLE)
}

publishMods {
    changelog.set(providers.gradleProperty("publish.changelog").orElse(providers.provider {
        if (publishChangelogFile.exists()) {
            publishChangelogFile.readText().trim()
        } else {
            "$modName $modVersion"
        }
    }))
    version.set(modVersion)
    type.set(releaseTypeProvider())
    dryRun.set(providers.gradleProperty("publish.dryRun").map(String::toBooleanStrict).orElse(true))

    val curseforgeOptions = curseforgeOptions {
        accessToken.set(tokenProvider("publish.curseforge.token", "curseforgeToken", "CURSEFORGE_TOKEN", "CURSEFORGE_API_KEY"))
        projectId.set(curseforgeProjectId)
        minecraftVersions.add(minecraftVersion)
        javaVersions.add(JavaVersion.VERSION_21)
        clientRequired.set(true)
        serverRequired.set(true)
    }

    val modrinthOptions = modrinthOptions {
        accessToken.set(tokenProvider("publish.modrinth.token", "modrinthToken", "MODRINTH_TOKEN", "MODRINTH_API_KEY"))
        projectId.set(modrinthProjectId)
        minecraftVersions.add(minecraftVersion)
    }

    curseforge("curseforgeFabric") {
        from(curseforgeOptions)
        file(project(":bootstrap:fabric"))
        displayName.set("$modName $modVersion Fabric $minecraftVersion")
        modLoaders.add("fabric")
        requires("fabric-api")
    }

    curseforge("curseforgeNeoForge") {
        from(curseforgeOptions)
        file(project(":bootstrap:neoforge"))
        displayName.set("$modName $modVersion NeoForge $minecraftVersion")
        modLoaders.add("neoforge")
    }

    modrinth("modrinthFabric") {
        from(modrinthOptions)
        file(project(":bootstrap:fabric"))
        displayName.set("$modName $modVersion Fabric $minecraftVersion")
        modLoaders.add("fabric")
        requires("fabric-api")
    }

    modrinth("modrinthNeoForge") {
        from(modrinthOptions)
        file(project(":bootstrap:neoforge"))
        displayName.set("$modName $modVersion NeoForge $minecraftVersion")
        modLoaders.add("neoforge")
    }
}

tasks.named<Sync>("buildAndCollect") {
    enabledPlatforms.forEach { platform ->
        val projectPath = ":bootstrap:$platform"
        val bootstrapProject = project(projectPath)
        val remapJar = bootstrapProject.tasks.named<RemapJarTask>("remapJar")
        dependsOn(remapJar)
        from(remapJar.flatMap { it.archiveFile })
    }
    val compilerJar = project(":compiler").tasks.shadowJar
    dependsOn(compilerJar)
    from(compilerJar.flatMap { it.archiveFile })
}

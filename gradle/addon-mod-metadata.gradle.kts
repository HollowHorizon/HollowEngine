import java.util.Properties

val addonModMetadataDir = layout.buildDirectory.dir("hollowengine/mod-metadata")

fun addonDescriptorProperty(name: String): String? {
    val descriptor = projectDir.resolve("src/main/resources/META-INF/plugin.properties")
    if (!descriptor.isFile) return null

    val properties = Properties()
    descriptor.inputStream().use(properties::load)
    return properties.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)
}

fun addonModId(addonId: String): String {
    val folded = addonId.lowercase().map { character ->
        if (character.isLetterOrDigit() || character == '_') character else '_'
    }.joinToString("")
    return if (folded.firstOrNull()?.isLetter() == true) folded else "a$folded"
}

val generateAddonModMetadata = tasks.register("generateAddonModMetadata") {
    group = "build"
    description = "Generates mod metadata that let this addon live in the mods folder."

    val engineModId = rootProject.property("modId") as String
    val license = rootProject.property("license") as String
    val addonId = addonDescriptorProperty("id") ?: project.name
    val modId = addonModId(addonId)
    val displayName = addonDescriptorProperty("name") ?: addonId
    val addonVersion = version.toString()
    val environment = addonDescriptorProperty("environment")?.lowercase() ?: "common"
    val fabricEnvironment = when (environment) {
        "client" -> "client"
        "server" -> "server"
        else -> "*"
    }

    inputs.property("modId", modId)
    inputs.property("version", addonVersion)
    inputs.property("name", displayName)
    inputs.property("environment", environment)
    inputs.property("engineModId", engineModId)
    inputs.property("license", license)
    outputs.dir(addonModMetadataDir)

    doLast {
        val root = addonModMetadataDir.get().asFile
        root.resolve("META-INF").mkdirs()

        root.resolve("fabric.mod.json").writeText(
            """
            {
              "schemaVersion": 1,
              "id": "$modId",
              "version": "$addonVersion",
              "name": "$displayName",
              "environment": "$fabricEnvironment",
              "depends": {
                "$engineModId": "*"
              }
            }
            """.trimIndent() + "\n"
        )

        root.resolve("META-INF/neoforge.mods.toml").writeText(
            """
            modLoader = "lowcodefml"
            loaderVersion = "[1,)"
            license = "$license"

            [[mods]]
            modId = "$modId"
            version = "$addonVersion"
            displayName = "$displayName"
            # The addon is loaded by HollowEngine, not by the loader, so a side that only has it
            # installed must not be kicked over a mod list mismatch.
            displayTest = "IGNORE_ALL_VERSION"

            [[dependencies.$modId]]
            modId = "$engineModId"
            type = "required"
            versionRange = "[1,)"
            ordering = "NONE"
            side = "BOTH"
            """.trimIndent() + "\n"
        )
    }
}

package ru.hollowhorizon.hollowengine.common.addons

import java.io.File
import java.util.Properties
import java.util.jar.JarFile

internal object HollowAddonDescriptorReader {
    private const val DESCRIPTOR_PATH = "META-INF/plugin.properties"
    private const val MAPPING_NAMESPACE_ATTRIBUTE = "HollowEngine-Mapping-Namespace"
    private val addonIdPattern = Regex("[a-z0-9_.-]+")

    fun read(file: File): HollowAddonDescriptor = JarFile(file).use { jar ->
        val entry = jar.getJarEntry(DESCRIPTOR_PATH)
            ?: throw IllegalArgumentException("Missing $DESCRIPTOR_PATH")
        val properties = Properties().apply {
            jar.getInputStream(entry).use(::load)
        }
        val id = properties.required("id")
        val mappingNamespace = jar.manifest?.mainAttributes
            ?.getValue(MAPPING_NAMESPACE_ATTRIBUTE)
            ?.let(HollowAddonMappingNamespace::parse)
            ?: HollowAddonMappingNamespace.AGNOSTIC
        HollowAddonDescriptor(
            id = id,
            version = properties.getProperty("version", "1.0.0").trim(),
            entrypoint = properties.required("entry"),
            dependencies = properties.list("dependsOn"),
            name = properties.getProperty("name", id).trim(),
            environment = properties.getProperty("environment", "common")
                .trim()
                .uppercase()
                .let(HollowAddonEnvironment::valueOf),
            requiredClasses = properties.list("requiredClasses"),
            mappingNamespace = mappingNamespace,
        ).also(::validate)
    }

    private fun validate(descriptor: HollowAddonDescriptor) {
        require(descriptor.id.matches(addonIdPattern)) { "Invalid addon id '${descriptor.id}'" }
        require(descriptor.version.isNotBlank()) { "Addon '${descriptor.id}' has an empty version" }
        require(descriptor.entrypoint.isNotBlank()) { "Addon '${descriptor.id}' has an empty entrypoint" }
        require(descriptor.id !in descriptor.dependencies) { "Addon '${descriptor.id}' cannot depend on itself" }
        require(descriptor.dependencies.distinct().size == descriptor.dependencies.size) {
            "Addon '${descriptor.id}' declares duplicate dependencies"
        }
    }

    private fun Properties.required(name: String): String = getProperty(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("Missing '$name' in $DESCRIPTOR_PATH")

    private fun Properties.list(name: String): List<String> = getProperty(name, "")
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
}

package ru.hollowhorizon.hollowengine.common.addons

import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimePlatform
import java.io.File
import java.util.jar.JarFile

internal data class HollowAddonVariant(val entryPath: String)

internal object HollowAddonVariants {
    private const val FORMAT_ATTRIBUTE = "HollowEngine-Addon-Format"
    private const val VARIANT_ATTRIBUTE_PREFIX = "HollowEngine-Variant-"
    private const val CURRENT_FORMAT = "2"

    fun select(
        artifact: File,
        platform: RuntimePlatform,
        namespace: HollowAddonMappingNamespace,
    ): HollowAddonVariant = JarFile(artifact).use { jar ->
        val attributes = requireNotNull(jar.manifest?.mainAttributes) {
            "Missing addon manifest"
        }
        val format = requireNotNull(attributes.getValue(FORMAT_ATTRIBUTE)) {
            "Unsupported legacy addon format: universal addon JAR required"
        }
        require(format == CURRENT_FORMAT) { "Unsupported HollowEngine addon format '$format'" }

        val platformAttribute = variantAttribute(platform.id(), namespace.id)
        val commonAttribute = variantAttribute("common", namespace.id)
        val agnosticAttribute = variantAttribute("common", HollowAddonMappingNamespace.AGNOSTIC.id)
        val entryPath = attributes.getValue(platformAttribute)
            ?: attributes.getValue(commonAttribute)
            ?: attributes.getValue(agnosticAttribute)
            ?: throw IllegalArgumentException(
                "Addon has no variant for ${platform.id()}/${namespace.id}",
            )
        val entry = jar.getJarEntry(entryPath)
            ?: throw IllegalArgumentException("Addon variant '$entryPath' does not exist")
        require(!entry.isDirectory && entryPath.endsWith(".jar")) {
            "Addon variant '$entryPath' is not a jar"
        }
        HollowAddonVariant(entryPath)
    }

    private fun variantAttribute(platform: String, namespace: String): String =
        "$VARIANT_ATTRIBUTE_PREFIX$platform-$namespace"
}

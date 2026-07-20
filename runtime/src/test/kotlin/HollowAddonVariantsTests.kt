package ru.hollowhorizon.hollowengine.common.addons

import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimePlatform
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class HollowAddonVariantsTests {
    @Test
    fun `selects platform variant before common fallback`() {
        val artifact = createAddon(
            "HollowEngine-Variant-Common-Named" to NAMED_VARIANT,
            "HollowEngine-Variant-Fabric-Intermediary" to INTERMEDIARY_VARIANT,
            "HollowEngine-Variant-Neoforge-Official" to NAMED_VARIANT,
        )

        assertEquals(
            INTERMEDIARY_VARIANT,
            HollowAddonVariants.select(
                artifact,
                RuntimePlatform.FABRIC,
                HollowAddonMappingNamespace.INTERMEDIARY,
            ).entryPath,
        )
        assertEquals(
            NAMED_VARIANT,
            HollowAddonVariants.select(
                artifact,
                RuntimePlatform.NEOFORGE,
                HollowAddonMappingNamespace.OFFICIAL,
            ).entryPath,
        )
    }

    @Test
    fun `uses common variant in named development environments`() {
        val artifact = createAddon("HollowEngine-Variant-Common-Named" to NAMED_VARIANT)

        assertEquals(
            NAMED_VARIANT,
            HollowAddonVariants.select(
                artifact,
                RuntimePlatform.FABRIC,
                HollowAddonMappingNamespace.NAMED,
            ).entryPath,
        )
        assertEquals(
            NAMED_VARIANT,
            HollowAddonVariants.select(
                artifact,
                RuntimePlatform.NEOFORGE,
                HollowAddonMappingNamespace.NAMED,
            ).entryPath,
        )
    }

    @Test
    fun `uses mapping agnostic variant as final fallback`() {
        val artifact = createAddon("HollowEngine-Variant-Common-Agnostic" to AGNOSTIC_VARIANT)

        HollowAddonMappingNamespace.entries
            .filterNot { namespace -> namespace == HollowAddonMappingNamespace.AGNOSTIC }
            .forEach { namespace ->
                assertEquals(
                    AGNOSTIC_VARIANT,
                    HollowAddonVariants.select(artifact, RuntimePlatform.FABRIC, namespace).entryPath,
                )
            }
    }

    @Test
    fun `rejects legacy addon jars`() {
        val artifact = createAddon(format = null)

        assertFailsWith<IllegalArgumentException> {
            HollowAddonVariants.select(
                artifact,
                RuntimePlatform.FABRIC,
                HollowAddonMappingNamespace.NAMED,
            )
        }
    }

    @Test
    fun `artifact store extracts only the selected classes jar`() {
        val artifact = createAddon("HollowEngine-Variant-Common-Named" to NAMED_VARIANT)
        val cacheDirectory = createTempDirectory("hollow-addon-cache").toFile()

        val candidate = HollowAddonArtifactStore(
            cacheDirectory,
            RuntimePlatform.FABRIC,
            HollowAddonMappingNamespace.NAMED,
        ).stage(artifact)

        assertNotEquals(candidate.artifactFile, candidate.classesFile)
        assertEquals(HollowAddonMappingNamespace.NAMED, candidate.descriptor.mappingNamespace)
        JarFile(candidate.classesFile).use { classes ->
            val marker = classes.getJarEntry(VARIANT_MARKER)
            assertEquals("selected", classes.getInputStream(marker).bufferedReader().use { it.readText() })
        }
    }

    private fun createAddon(
        vararg variants: Pair<String, String>,
        format: String? = "2",
    ): File {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            format?.let { mainAttributes.putValue("HollowEngine-Addon-Format", it) }
            variants.forEach { (name, path) -> mainAttributes.putValue(name, path) }
        }
        val file = createTempDirectory("hollow-addon-variants").resolve("addon.jar").toFile()
        JarOutputStream(file.outputStream(), manifest).use { output ->
            output.putNextEntry(JarEntry("META-INF/plugin.properties"))
            output.write("id=test-addon\nentry=test.Entry\n".toByteArray())
            output.closeEntry()
            val variantBytes = createVariantJar()
            variants.map(Pair<String, String>::second).distinct().forEach { path ->
                output.putNextEntry(JarEntry(path))
                output.write(variantBytes)
                output.closeEntry()
            }
        }
        return file
    }

    private fun createVariantJar(): ByteArray {
        val bytes = ByteArrayOutputStream()
        JarOutputStream(bytes).use { output ->
            output.putNextEntry(JarEntry(VARIANT_MARKER))
            output.write("selected".toByteArray())
            output.closeEntry()
        }
        return bytes.toByteArray()
    }

    private companion object {
        const val NAMED_VARIANT = "META-INF/hollowengine/variants/named.jar"
        const val INTERMEDIARY_VARIANT = "META-INF/hollowengine/variants/intermediary.jar"
        const val AGNOSTIC_VARIANT = "META-INF/hollowengine/variants/agnostic.jar"
        const val VARIANT_MARKER = "variant-marker.txt"
    }
}

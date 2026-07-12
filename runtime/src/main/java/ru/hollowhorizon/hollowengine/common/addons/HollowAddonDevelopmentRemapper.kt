package ru.hollowhorizon.hollowengine.common.addons

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.MappingResolver
import org.objectweb.asm.commons.Remapper
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.remap
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

internal object HollowAddonDevelopmentRemapper {
    private const val MAPPING_NAMESPACE_ATTRIBUTE = "HollowEngine-Mapping-Namespace"

    fun remapIfRequired(candidate: HollowAddonCandidate, cacheRoot: File): HollowAddonCandidate {
        val runtimeNamespace = HollowAddonRuntimeEnvironment.mappingNamespace()
        if (
            runtimeNamespace != HollowAddonMappingNamespace.NAMED ||
            candidate.descriptor.mappingNamespace != HollowAddonMappingNamespace.INTERMEDIARY
        ) {
            return candidate
        }

        val fabricLoader = FabricLoader.getInstance()
        val resolver = fabricLoader.mappingResolver
        val minecraftVersion = fabricLoader.getModContainer("minecraft")
            .map { container -> container.metadata.version.friendlyString }
            .orElse("unknown")
        val mappingIdentity = (minecraftVersion + '-' + resolver.currentRuntimeNamespace)
            .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val outputDirectory = cacheRoot.resolve("remapped").resolve(candidate.fingerprint).resolve(mappingIdentity)
        val outputFile = outputDirectory.resolve("addon-named.jar")
        if (!outputFile.isFile) {
            outputDirectory.mkdirs()
            val temporaryFile = outputDirectory.resolve("addon-named.jar.tmp")
            remapJar(candidate.artifactFile, temporaryFile, resolver)
            Files.move(
                temporaryFile.toPath(),
                outputFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }

        return candidate.copy(
            artifactFile = outputFile,
            descriptor = candidate.descriptor.copy(mappingNamespace = HollowAddonMappingNamespace.NAMED),
        )
    }

    private fun remapJar(inputFile: File, outputFile: File, resolver: MappingResolver) {
        val remapper = FabricMappingRemapper(resolver)
        JarFile(inputFile).use { input ->
            val manifest = input.manifest?.apply {
                mainAttributes[Attributes.Name(MAPPING_NAMESPACE_ATTRIBUTE)] = HollowAddonMappingNamespace.NAMED.id
            }
            val outputStream = outputFile.outputStream().buffered()
            val jarOutput = if (manifest == null) JarOutputStream(outputStream) else JarOutputStream(outputStream, manifest)
            jarOutput.use { output ->
                input.entries().asSequence()
                    .filterNot { entry -> entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true) }
                    .filterNot(::isSignatureFile)
                    .forEach { entry ->
                        val isClass = !entry.isDirectory && entry.name.endsWith(".class")
                        val outputName = if (isClass) {
                            remapper.map(entry.name.removeSuffix(".class")) + ".class"
                        } else {
                            entry.name
                        }
                        output.putNextEntry(JarEntry(outputName))
                        if (isClass) {
                            val bytes = input.getInputStream(entry).use { stream -> stream.readBytes() }
                            output.write(bytes.remap(remapper))
                        } else if (!entry.isDirectory) {
                            input.getInputStream(entry).use { stream -> stream.copyTo(output) }
                        }
                        output.closeEntry()
                    }
            }
        }
    }

    private fun isSignatureFile(entry: JarEntry): Boolean {
        if (!entry.name.startsWith("META-INF/", ignoreCase = true)) return false
        return entry.name.endsWith(".SF", ignoreCase = true) ||
            entry.name.endsWith(".RSA", ignoreCase = true) ||
            entry.name.endsWith(".DSA", ignoreCase = true)
    }

    private class FabricMappingRemapper(
        private val resolver: MappingResolver,
    ) : Remapper() {
        override fun map(internalName: String): String = resolver
            .mapClassName(HollowAddonMappingNamespace.INTERMEDIARY.id, internalName.replace('/', '.'))
            .replace('.', '/')

        override fun mapMethodName(owner: String, name: String, descriptor: String): String {
            if (name == "<init>" || name == "<clinit>") return name
            return resolver.mapMethodName(
                HollowAddonMappingNamespace.INTERMEDIARY.id,
                owner.replace('/', '.'),
                name,
                descriptor,
            )
        }

        override fun mapFieldName(owner: String, name: String, descriptor: String?): String = resolver.mapFieldName(
            HollowAddonMappingNamespace.INTERMEDIARY.id,
            owner.replace('/', '.'),
            name,
            requireNotNull(descriptor) { "A field descriptor is required for mapping $owner.$name" },
        )

        override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String =
            mapFieldName(owner, name, descriptor)
    }
}

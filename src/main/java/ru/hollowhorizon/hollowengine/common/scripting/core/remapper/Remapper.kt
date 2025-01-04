package ru.hollowhorizon.hollowengine.common.scripting.core.remapper

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.*
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.LOGGER
import java.io.*
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

object Remapper {
    private val MAPPINGS = loadMappings(
        //? if fabric && >=1.21 {
        /*"mappings-1.21.tiny"
        *///?} elif fabric && >=1.20.1 {
        "mappings-1.20.1.tiny"
        //?} elif fabric && >=1.19.2 {
        /*"mappings-1.19.2.tiny",
        *///?} elif forge && >=1.20.1 {
        /*"mappings-1.20.1.tsrg"
        *///?} else {
        /*"mappings-1.19.2.tsrg"*/
        //?}
    )
    val OBFUSCATE_REMAPPER get() = buildRemapper(createMappings(MAPPINGS, "named", "intermediary"))
    val DEOBFUSCATE_REMAPPER get() = buildRemapper(createMappings(MAPPINGS, "intermediary", "named"))

    private fun buildRemapper(mappings: IMappingProvider) = TinyRemapper.newRemapper()
        .rebuildSourceFilenames(true)
        .fixPackageAccess(false)
        .skipLocalVariableMapping(true)
        .keepInputData(false)
        .ignoreConflicts(true)
        .ignoreFieldDesc(true)
        .extension(KotlinMetadataTinyRemapperExtension)
        .withMappings(mappings)
        .build()


    private fun createMappings(tree: MemoryMappingTree, from: String, to: String): IMappingProvider {
        return TinyUtils.createMappingProvider(tree, from, to)
    }

    private fun loadMappings(name: String, tsrg: Boolean = name.endsWith(".tsrg")) = MemoryMappingTree().apply {
        MappingReader.read(
            InputStreamReader(HollowCore::class.java.getResourceAsStream("/$name")
                ?: throw FileNotFoundException("$name not found!")
            ),
            if (tsrg) MappingFormat.TSRG_FILE else MappingFormat.TINY_2_FILE,
            this
        )
    }


    fun remap(remapper: TinyRemapper, filesArray: Array<File>, newDir: Path, vararg classPath: Path) {
        val files: MutableMap<Path, InputTag> = mutableMapOf()
        filesArray.forEach { file ->
            try {
                if (!newDir.resolve(file.name).exists()) {
                    files[file.toPath()] = remapper.createInputTag()
                }
            } catch (e: IOException) {
                LOGGER.error("Error while copying $file", e)
            }
        }

        remapper.readInputsAsync(remapper.createInputTag(), *classPath)

        val consumers: MutableMap<OutputConsumerPath, InputTag> = mutableMapOf()

        for ((file, tag) in files) {
            try {
                val consumer = OutputConsumerPath.Builder(newDir.resolve(file.name))
                    .assumeArchive(true)
                    .build().apply { consumers[this] = tag }

                consumer.addNonClassFiles(file, NonClassCopyMode.FIX_META_INF, remapper)
                remapper.readInputsAsync(tag, file)
            } catch (e: Exception) {
                LOGGER.error("Error while reading $file", e)
            }
        }
        consumers.forEach { (consumer, tag) ->
            try {
                remapper.apply(consumer, tag)
            } catch (e: Exception) {
                LOGGER.error("Error while applying remapper", e)
            }
        }

        if (files.isNotEmpty()) remapper.finish()
        for (consumer in consumers.keys) consumer.close()
    }
}
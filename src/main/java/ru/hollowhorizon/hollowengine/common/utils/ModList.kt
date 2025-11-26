package ru.hollowhorizon.hollowengine.common.utils

//? if fabric {
/*import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import java.util.jar.JarFile
import kotlin.jvm.optionals.getOrNull

*///?} elif forge {
import net.minecraftforge.fml.ModList
//?} elif neoforge {
/*import net.neoforged.fml.ModList
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
*///?}


import ru.hollowhorizon.hollowengine.common.scripting.deobf.collectModJars
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString


/**
 * Utility object for managing loaded mods.
 *
 * Provides methods to check for loaded mods, retrieve mod files, and list installed mods.
 */
object ModList {
    /**
     * Checks if a mod is loaded.
     *
     * @param modId The mod identifier.
     * @return `true` if the mod is loaded, otherwise `false`.
     */
    fun isLoaded(modId: String): Boolean {
        //? if fabric {
        /*return FabricLoader.getInstance().isModLoaded(modId)
        *///?} elif forge || neoforge {
        return ModList.get().isLoaded(modId)
        //?}
    }

    /**
     * Retrieves the mod file associated with the given mod ID.
     *
     * @param modId The mod identifier.
     * @return The mod file as a File object.
     * @throws FileNotFoundException if the mod file cannot be found.
     */
    fun getFile(modId: String): File {
        //? if fabric {
        /*return FabricLoader.getInstance().getModFile(modId)
        *///?} elif forge || neoforge {
        // Неужели так сложно просто дать нормальный путь к файлу...
        return getModFile(modId)
        //?}
    }

    fun getAllFiles(modId: String): List<File> {
        //? if fabric {
        /*return FabricLoader.getInstance().getAllMods(modId)
        *///?} elif forge || neoforge {
        return getAllMods(modId)
        //?}
    }

    /**
     * Retrieves a list of all loaded mod IDs.
     *
     * @return A list of strings representing mod IDs.
     */
    val mods: List<String>
        get() {
            //? if fabric {
            /*return FabricLoader.getInstance().allMods.map { it.metadata.id }
            *///?} elif forge || neoforge {
            return ModList.get().mods.map { it.modId }
            //?}
        }

}

//? if fabric {
/*fun FabricLoader.getModFile(modId: String): File {
    val modContainer = getModContainer(modId).getOrNull()
        ?: throw FileNotFoundException("Mod Not Found: $modId")
    val origin = modContainer.origin

    return when (val kind = origin.kind) {
        ModOrigin.Kind.PATH -> origin.paths[0].toFile()
        ModOrigin.Kind.NESTED -> getNestedModFile(origin)
        else -> throw IllegalStateException("Unsupported kind: $kind")
    }
}

fun FabricLoader.getAllMods(modId: String): List<File> {
    val mods = (getModContainer(modId).getOrNull()?.containedMods?.flatMap { getAllMods(it.metadata.id) } ?: emptyList()).toMutableList()
    mods += getModFile(modId)
    return mods
}

fun FabricLoader.getNestedModFile(origin: ModOrigin): File {

    val parentId = origin.parentModId
    val parentFile = getModFile(parentId)
    val subLocation = origin.parentSubLocation

    val parentJar = JarFile(parentFile)
    val nestedJarEntry = parentJar.getJarEntry(subLocation)

    val fileName = subLocation.split('/').last()
    val newFile = File("hollowengine/.cache/mods/$fileName").apply {
        if (!this.parentFile.exists()) this.parentFile.mkdirs()
    }

    if (newFile.exists()) return newFile
    else newFile.createNewFile()

    FileOutputStream(newFile).use { jarOutput ->
        parentJar.getInputStream(nestedJarEntry).use { reader ->
            reader.copyTo(jarOutput)
        }
    }

    return newFile
}
*///?} else {
fun getAllMods(modId: String): List<File> {
    return collectModJars(getModFile(modId))
}
fun getModFile(modId: String): File {
    val path = ModList.get().getModFileById(modId).file.filePath
    try {
        var fileName = path.fileName.toString()
        if (!fileName.endsWith(".jar")) fileName = "$modId.jar"

        val copy = Files.newInputStream(path)

        val newFile = File("hollowengine/.cache/mods/$fileName").apply {
            if (!this.parentFile.exists()) this.parentFile.mkdirs()
        }

        if (newFile.exists()) return newFile
        else newFile.createNewFile()

        FileOutputStream(newFile).use { jarOutput ->
            copy.use { reader ->
                reader.copyTo(jarOutput)
            }
        }

        return newFile
    } catch (e: AccessDeniedException) {
        return e.file
    } catch (e: Exception) {
        if (path.fileSystem::class.java.name == "cpw.mods.niofs.union.UnionFileSystem") {
            val system = path.fileSystem::class.java.getDeclaredField("basepaths")
            system.isAccessible = true
            return (system.get(path.fileSystem) as List<Path>).map { it.toFile() }.first()
        }

        return File(path.absolutePathString())
    }
}
//?}

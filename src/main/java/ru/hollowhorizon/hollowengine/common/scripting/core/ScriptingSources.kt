package ru.hollowhorizon.hollowengine.common.scripting.core

//? if fabric {
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider
import java.nio.file.Path
//?} else {
/*import net.minecraftforge.fml.loading.FMLLoader
import kotlin.io.path.absolutePathString
*///?}
import ru.hollowhorizon.hc.common.utils.ModList
import ru.hollowhorizon.hc.common.utils.isProduction
import ru.hollowhorizon.hollowengine.common.scripting.core.mappings.MAPPINGS
import ru.hollowhorizon.hollowengine.common.scripting.core.mappings.remapJars
import sun.misc.Unsafe
import java.io.File

private val deobfClassPath: File = File("hollowcore/.classpath")
    .apply { if (!exists()) mkdirs() }


val scriptingClasspath = mutableListOf<File>()
val deobfClasspath get() = deobfClassPath.walk().toList()


fun forgeClasspath() = System.getProperty("java.class.path")
    .split(";").map(::File).toMutableSet()

private fun setupSTDLib(files: Collection<File>) {
    System.setProperty("kotlin.java.stdlib.jar", files.first { it.name.startsWith("kotlin-stdlib-jdk8") }.absolutePath)
}

fun setupScripting() {
    if(isProduction) {
        cleanup()

        //? if fabric
        setupFabric()
        //? if forge || neoforge
        /*setupForge()*/

        setupMods()
    }

    setupSTDLib(if(isProduction) deobfClasspath else forgeClasspath())
}

fun cleanup() {
    val modsHashCode = ModList.mods.map { ModList.getFile(it) }.sumOf { it.hashCode() }
    val hashFile = File("hollowcore/scripting_env.hash").apply { if (!parentFile.exists()) parentFile.mkdirs() }
    if (hashFile.exists()) {
        if (hashFile.readText().toInt() == modsHashCode) return
    }
    hashFile.writeText(modsHashCode.toString())

    File("hollowcore/embed_mods").walk().forEach { it.delete() }
    deobfClasspath.forEach { it.delete() }
}

fun setupMods() {
    if (isProduction) remapJars(
        MAPPINGS,
        //? if fabric {
        ModList.mods
            .map { ModList.getFile(it) }
            .filter { it.name.endsWith(".jar") },
        //?} else {
        /*File("hollowcore/embed_mods").walk()
            .filter { it.extension == "jar" }
            .toList(),
        *///?}
        deobfClassPath,
        from = "intermediary",
        to = "named"
    )
}

//? if fabric {

fun setupFabric() {
    val gameProvider =
        (FabricLoader.getInstance() as FabricLoaderImpl).gameProvider as MinecraftGameProvider
    val libs: List<Path> = findField(gameProvider, "miscGameLibraries")
    val gameJars: List<Path> = findField(gameProvider, "gameJars")
    val logJars: Set<Path> = findField(gameProvider, "logJars")
    val parentClassPath: Collection<Path> = findField(gameProvider, "validParentClassPath")

    if (isProduction) {
        remapJars(
            MAPPINGS,
            gameJars.map { it.toFile() },
            deobfClassPath,
            from = "intermediary",
            to = "named"
        )

        scriptingClasspath.addAll((libs + logJars + parentClassPath).map { it.toFile() })
    } else {
        scriptingClasspath.addAll((libs + gameJars + logJars + parentClassPath).map { it.toFile() })
    }
}
//?}

//? if forge {
/*fun setupForge() {
    val classpath = forgeClasspath()

    val gameJars = FMLLoader.getLaunchHandler().minecraftPaths.minecraftPaths
        .map { File(it.absolutePathString()) }.filter { it.isFile && it.exists() }

    if (isProduction) {
        remapJars(MAPPINGS, gameJars, deobfClassPath,
        from = "intermediary",
        to = "named"
        )
    }

    scriptingClasspath.addAll(classpath)

    collectModsJars()
}
*///?}


private val unsafe by lazy {
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    theUnsafe[null] as Unsafe
}

@Suppress("UNCHECKED_CAST")
fun <T> findField(lookup: Any, name: String): T {
    val lookupClass = lookup::class.java
    val field = lookupClass.getDeclaredField(name) // Why did you have to make it private?
    val offset = unsafe.objectFieldOffset(field)
    return unsafe.getObject(lookup, offset) as T
}

fun compilerJar() = deobfClasspath.first { it.name.startsWith("kotlin-compiler-embeddable") }
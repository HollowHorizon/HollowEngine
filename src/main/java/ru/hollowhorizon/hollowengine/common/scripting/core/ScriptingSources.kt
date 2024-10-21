package ru.hollowhorizon.hollowengine.common.scripting.core

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider
import ru.hollowhorizon.hc.client.utils.ModList
import ru.hollowhorizon.hc.client.utils.isProduction
import ru.hollowhorizon.hollowengine.common.scripting.core.remapper.Remapper
import sun.misc.Unsafe
import java.io.File
import java.nio.file.Path

private val deobfClassPath: File = File("hollowcore/.classpath")
    .apply { if (!exists()) mkdirs() }


val scriptingClasspath = mutableListOf<File>()
val deobfClasspath get() = deobfClassPath.walk().toList()



private fun forgeClasspath() = System.getProperty("java.class.path")
    .split(";").map(::File).toMutableSet()
private fun setupSTDLib(files: Collection<File>) {
    System.setProperty("kotlin.java.stdlib.jar", files.first { it.name.startsWith("kotlin-stdlib") }.absolutePath)
}


fun setupScripting() {
    cleanup()

    //? if fabric
    setupFabric()
    //? if forge || neoforge
    /*setupForge()*/

    setupMods()
}

fun cleanup() {
    File("hollowcore/embedded_mods").walk().forEach { it.delete() }
    deobfClasspath.forEach { it.delete() }
}

fun setupMods() {
    Remapper.remap(
        Remapper.DEOBFUSCATE_REMAPPER,
        ModList.mods
            .map { ModList.getFile(it) }
            .filter { it.name.endsWith(".jar") }
            .toTypedArray(),
        deobfClassPath.toPath()
    )
}

fun setupFabric() {
    val gameProvider =
        (FabricLoader.getInstance() as FabricLoaderImpl).gameProvider as MinecraftGameProvider
    val libs: List<Path> = findField(gameProvider, "miscGameLibraries")
    val gameJars: List<Path> = findField(gameProvider, "gameJars")
    val logJars: Set<Path> = findField(gameProvider, "logJars")
    val parentClassPath: Collection<Path> = findField(gameProvider, "validParentClassPath")

    if (isProduction) {
        Remapper.remap(
            Remapper.DEOBFUSCATE_REMAPPER,
            gameJars.map { it.toFile() }.toTypedArray(),
            deobfClassPath.toPath()
        )

        scriptingClasspath.addAll((libs + logJars + parentClassPath).map { it.toFile() })
    } else {
        scriptingClasspath.addAll((libs + gameJars + logJars + parentClassPath).map { it.toFile() })
    }
}

fun setupForge() {
    val classpath = forgeClasspath()

    setupSTDLib(classpath)
}


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
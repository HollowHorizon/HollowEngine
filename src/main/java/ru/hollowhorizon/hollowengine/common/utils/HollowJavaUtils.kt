package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Utility class for accessing resources within the game.
 */
object HollowJavaUtils {
    /**
     * Retrieves an input stream for a given resource location.
     *
     * @param location The resource location.
     * @return An InputStream of the resource.
     * @throws FileNotFoundException If the resource cannot be found.
     */
    @JvmStatic
    fun getResource(location: ResourceLocation): InputStream {
        return try {
            Minecraft.getInstance().resourceManager.getResource(location).orElseThrow().open()
        } catch (e: Exception) {
            Thread.currentThread().contextClassLoader.getResourceAsStream("assets/" + location.namespace + "/" + location.path)
                ?: throw FileNotFoundException("Resource $location not found!")
        }
    }
}

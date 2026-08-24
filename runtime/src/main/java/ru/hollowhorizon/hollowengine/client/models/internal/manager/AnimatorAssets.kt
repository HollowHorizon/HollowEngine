package ru.hollowhorizon.hollowengine.client.models.internal.manager

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.models.Animator
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.nbt.loadAsNBT
import java.util.concurrent.ConcurrentHashMap

/**
 * The animators models can wear, by id.
 */
object AnimatorAssets {
    private val assets = ConcurrentHashMap<ResourceLocation, Animator>()

    fun register(id: ResourceLocation, animator: Animator) {
        assets[id] = animator
    }

    fun get(id: ResourceLocation?): Animator? = assets[id]

    /**
     * Re-reads every `.animator` in the pack; code-registered animators are left alone.
     */
    fun reload(manager: ResourceManager) {
        ROOTS.forEach { root ->
            manager.listResources(root) { it.path.endsWith(SUFFIX) }.forEach { (location, resource) ->
                try {
                    val tag = resource.open().use { it.loadAsNBT() }
                    assets[location] = NBTFormat.deserialize(Animator.serializer(), tag)
                } catch (e: Exception) {
                    HollowEngine.LOGGER.warn("Could not read animator '{}': {}", location, e.message)
                }
            }
        }
    }

    private const val SUFFIX = ".animator"
    private val ROOTS = listOf("models", "animations")
}

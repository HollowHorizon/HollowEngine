package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import net.minecraft.client.renderer.ShaderInstance

/**
 * The appearance of the surface to be rendered.
 */
interface RenderMaterial {
    /**
     * Should the rendering calls for this material be sorted from far to near?
     *
     * For example, for semi-transparent materials.
     */
    val isTranslucent: Boolean

    fun bind(context: MaterialBindContext)

    /** Undoes whatever [bind] set if it needs to be undone. */
    fun clear(context: MaterialBindContext) = Unit
}

/**
 * What a material is being bound for.
 */
class MaterialBindContext(val shader: ShaderInstance, val colorLocation: Int = -1)

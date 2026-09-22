package ru.hollowhorizon.hollowengine.client.models.internal

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine.MODID
import ru.hollowhorizon.hollowengine.common.utils.Color
import ru.hollowhorizon.hollowengine.common.utils.rl

/**
 * How a surface of a model is drawn.
 *
 * The name is how everything else addresses it: `.hemeta` renames it, and the materials of an entity
 * are keyed by it. Models that name nothing get `material_0`, `material_1`, and so on.
 */
data class Material(
    var name: String = "",
    var color: Color = Color(1f, 1f, 1f, 1f),
    var texture: ResourceLocation = MISSING_TEXTURE,
    var normalTexture: ResourceLocation = MISSING_NORMAL,
    var specularTexture: ResourceLocation = MISSING_SPECULAR,
    var doubleSided: Boolean = false,
    var blend: Blend = Blend.OPAQUE,
    var emissive: Boolean = false,
) {
    enum class Blend { OPAQUE, BLEND }

    companion object {
        val MISSING_TEXTURE = "$MODID:default_color_map".rl
        val MISSING_NORMAL = "$MODID:default_normal_map".rl
        val MISSING_SPECULAR = "$MODID:default_specular_map".rl
    }
}
package ru.hollowhorizon.hollowengine.client.models.internal

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine.MODID
import ru.hollowhorizon.hollowengine.common.utils.Color
import ru.hollowhorizon.hollowengine.common.utils.rl

data class Material(
    var color: Color = Color(1f, 1f, 1f, 1f),
    var texture: ResourceLocation = MISSING_TEXTURE,
    var normalTexture: ResourceLocation = MISSING_NORMAL,
    var specularTexture: ResourceLocation = MISSING_SPECULAR,
    var doubleSided: Boolean = false,
    var blend: Blend = Blend.OPAQUE,
) {
    enum class Blend { OPAQUE, BLEND }

    companion object {
        val MISSING_TEXTURE = "$MODID:default_color_map".rl
        val MISSING_NORMAL = "$MODID:default_normal_map".rl
        val MISSING_SPECULAR = "$MODID:default_specular_map".rl
    }
}
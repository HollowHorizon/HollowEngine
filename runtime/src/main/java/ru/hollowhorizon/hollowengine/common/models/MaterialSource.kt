package ru.hollowhorizon.hollowengine.common.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

/**
 * What a named material of an entity should look like.
 */
@Serializable
sealed interface MaterialSource {
    /** Textures out of a resource pack, and the color they are tinted with. */
    @Serializable
    @SerialName("hollowengine:material/texture")
    data class Texture(
        val texture: @Serializable(ForResourceLocation::class) ResourceLocation,
        val normal: @Serializable(ForResourceLocation::class) ResourceLocation? = null,
        val specular: @Serializable(ForResourceLocation::class) ResourceLocation? = null,
        /** `#rrggbb` or `#aarrggbb`; null keeps the color the model was authored with. */
        val color: String? = null,
    ) : MaterialSource

    /** Whatever a player wears: their skin, cape or elytra, by name or by uuid. */
    @Serializable
    @SerialName("hollowengine:material/player")
    data class Player(
        val player: String,
        val part: PlayerSkinPart = PlayerSkinPart.SKIN,
    ) : MaterialSource
}

enum class PlayerSkinPart { SKIN, CAPE, ELYTRA }

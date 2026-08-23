package ru.hollowhorizon.hollowengine.common.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.peanuuutz.tomlkt.Toml
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

/**
 * What a model file says about itself, in a `<model>.hemeta` next to it.
 *
 * ```toml
 * preload = true
 * animation-controller = "hollowengine:standard_player"
 *
 * [materials]
 * skin = "Body"
 * cape = "material_3"
 * ```
 */
@Serializable
data class ModelMetadata(
    /** Load the model when resources load, rather than when something first asks for it. */
    val preload: Boolean = false,

    /** The animator this model wears; a `.animator` file, or an id registered from code. */
    @SerialName("animation-controller")
    val animationController: @Serializable(ForResourceLocation::class) ResourceLocation? = null,

    /**
     * New name for a material of the model, keyed by the name it already has.
     */
    val materials: Map<String, String> = emptyMap(),
) {
    /** The name a material should answer to, given the name it was loaded with. */
    fun renameOf(loadedName: String): String? =
        materials.entries.firstOrNull { it.value == loadedName }?.key

    companion object {
        val EMPTY = ModelMetadata()

        private val toml = Toml { ignoreUnknownKeys = true }

        /** Parses [source]; anything unreadable is reported and treated as no metadata at all. */
        fun parse(source: String, name: String): ModelMetadata {
            if (source.isBlank()) return EMPTY
            return try {
                toml.decodeFromString(serializer(), source)
            } catch (e: Exception) {
                HollowEngine.LOGGER.warn("Could not read model metadata '{}': {}", name, e.message)
                EMPTY
            }
        }
    }
}

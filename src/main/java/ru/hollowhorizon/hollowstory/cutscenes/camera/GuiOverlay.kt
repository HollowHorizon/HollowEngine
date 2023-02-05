@file:UseSerializers(ForResourceLocation::class)

package ru.hollowhorizon.hollowstory.cutscenes.camera

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.util.ResourceLocation
import ru.hollowhorizon.hc.client.utils.nbt.ForResourceLocation

@Serializable
class GuiOverlay {
    val labels = arrayListOf<Label>()
    val images = arrayListOf<Image>()

    @Serializable
    data class Label(
        val text: String,
        val x: Int,
        val y: Int,
        val size: Float,
        val color: Int,
        val align: TextAlign = TextAlign.LEFT,
    ) {
        enum class TextAlign(val value: Float) {
            LEFT(0f), CENTER(0.5f), RIGHT(1f)
        }
    }

    @Serializable
    data class Image(
        val location: ResourceLocation,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val blend: Boolean = false,
    )
}

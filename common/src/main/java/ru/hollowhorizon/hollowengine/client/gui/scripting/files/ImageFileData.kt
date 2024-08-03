package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import imgui.ImGui
import imgui.type.ImBoolean
import net.minecraft.client.renderer.texture.DynamicTexture
import kotlin.math.min

class ImageFileData(name: String, path: String, open: ImBoolean, val image: DynamicTexture) : FileData(name, path, open) {
    override fun draw() {
        val imageWidth = image.pixels?.width?.toFloat() ?: 1f
        val imageHeight = image.pixels?.height?.toFloat() ?: 1f

        val size = ImGui.getContentRegionAvail()
        size.minus(5f, 5f)

        val scale = min(size.x / imageWidth, size.y / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale

        ImGui.image(image.id, width, height, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
    }

    override fun save() {

    }
}
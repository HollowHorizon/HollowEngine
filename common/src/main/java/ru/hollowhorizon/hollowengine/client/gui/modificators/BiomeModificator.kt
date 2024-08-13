package ru.hollowhorizon.hollowengine.client.gui.modificators

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImFloat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.HollowEngineGui


object BiomeModificator : Screen("".literal) {
    var enable = ImBoolean(false)
    var enableSkybox = ImBoolean(false)
    private var skyColor = floatArrayOf(0f, 0f, 0f)
    private var fogColor = floatArrayOf(0f, 0f, 0f)
    private var waterColor = floatArrayOf(0f, 0f, 0f)
    private var waterFogColor = floatArrayOf(0f, 0f, 0f)

    var sunSize = floatArrayOf(30f)
    var moonSize = floatArrayOf(20f)

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        ImGuiHandler.drawFrame {
            centredWindow {
                draw()
            }
        }
    }

    private fun draw() {
        ImGui.checkbox("Включить модификатор биомов", enable)
        ImGui.checkbox("Включить скайбокс", enableSkybox)

        ImGui.separator()

        ImGui.colorEdit3("Цвет неба", skyColor)
        ImGui.colorEdit3("Цвет тумана", fogColor)
        ImGui.colorEdit3("Цвет воды", waterColor)
        ImGui.colorEdit3("Цвет тумана в воде", waterFogColor)

        ImGui.separator()

        ImGui.sliderFloat("Размер солнца", sunSize, 0f, 500f)
        ImGui.sliderFloat("Размер луны", moonSize, 0f, 500f)
    }

    fun skyColor() = colorConvert(skyColor)
    fun fogColor() = colorConvert(fogColor)
    fun waterColor() = colorConvert(waterColor)
    fun waterFogColor() = colorConvert(waterFogColor)

    private fun colorConvert(array: FloatArray): Int {
        val r = (array[0] * 255.0).toInt()
        val g = (array[1] * 255.0).toInt()
        val b = (array[2] * 255.0).toInt()
        return (r shl 16) or (g shl 8) or b
    }
}

@SubscribeEvent
fun onTabRegistry(event: HollowEngineGui.TabEvent) {
    event.register(HollowEngineGui.Tab("biome_modifier") {
        BiomeModificator.open()
    })
}
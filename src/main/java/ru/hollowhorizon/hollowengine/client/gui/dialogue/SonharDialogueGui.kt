/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.gui.dialogue

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.centredWindow
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.client.screen.OnChoicePerform

object SonharDialogueGui : HollowScreen() {
    val BG = "hollowengine:textures/gui/dialogues/sonhar_dialogue_menu.png".rl
    val CHOICE_BUTTON = "hollowengine:textures/gui/dialogues/sonhar_dialogue_choice.png".rl
    val CHOICE_BUTTON_LARGE = "hollowengine:textures/gui/dialogues/sonhar_dialogue_choice_large.png".rl
    val CHOICE_SWITCH_LEFT = "hollowengine:textures/gui/dialogues/sonhar_dialogue_button_left.png".rl
    val CHOICE_SWITCH_RIGHT = "hollowengine:textures/gui/dialogues/sonhar_dialogue_button_right.png".rl
    val DIALOGUE_SWITCH_LEFT = "hollowengine:textures/gui/dialogues/sonhar_dialogue_button_left_1.png".rl
    val DIALOGUE_SWITCH_RIGHT = "hollowengine:textures/gui/dialogues/sonhar_dialogue_button_right_1.png".rl
    val DIALOGUE_BUTTON_MIC = "hollowengine:textures/gui/dialogues/sonhar_dialogue_button_mic.png".rl
    private var scale = 1.0f

    var text = arrayListOf<String>()
    var sounds = arrayListOf<String>()
    var textIndex = 0
    var choices = arrayListOf<String>()
    var currentPage = 0
    var entities: List<LivingEntity?> = arrayListOf<LivingEntity?>()

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderBackground(pPoseStack)
        ImguiHandler.drawFrame { draw() }
    }

    fun draw() {
        scale = Minecraft.getInstance().window.guiScale.toFloat()

        centredWindow(
            args = ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoTitleBar or
                    ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoMove
        ) {
            image(BG, 311 * scale, 191 * scale)

            if (entities[textIndex] != null) {
                ImGui.setCursorPos(209 * scale, 21 * scale)
                npc(entities[textIndex]!!, 64 * scale, 64 * scale, 52 * scale, 1.75f)
            }

            ImGui.setCursorPos(212 * scale, 91 * scale)
            if (textIndex < sounds.size && button("", DIALOGUE_BUTTON_MIC, 24 * scale, 24 * scale)) {

                Minecraft.getInstance().soundManager.play(
                    SimpleSoundInstance(
                        sounds[textIndex].rl,
                        SoundSource.MASTER,
                        1.0f,
                        1.0f,
                        Minecraft.getInstance().player!!.random,
                        false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true
                    )
                )
            }

            ImGui.setCursorPos(42 * scale, 24 * scale)
            ImGui.pushTextWrapPos(200f * scale)

            val old = ImGui.getStyle().getColor(ImGuiCol.Text)
            ImGui.getStyle().setColor(ImGuiCol.Text, 0f, 0f, 0f, 1f)
            if (text.isNotEmpty()) ImGui.textWrapped(text[textIndex])
            ImGui.getStyle().setColor(ImGuiCol.Text, old.x, old.y, old.z, old.w)

            ImGui.setCursorPos(40 * scale, 80 * scale)
            if (textIndex > 0 && button("", DIALOGUE_SWITCH_LEFT, 19 * scale, 11 * scale, true)) {
                textIndex--
                textIndex = textIndex.coerceAtLeast(0)
            }
            ImGui.setCursorPos(185 * scale, 80 * scale)
            if (textIndex < text.size - 1 && button("", DIALOGUE_SWITCH_RIGHT, 19 * scale, 11 * scale, true)) {
                textIndex++
                textIndex = textIndex.coerceAtMost(text.size - 1)
            }

            if (textIndex == text.size - 1) {
                ImGui.setCursorPos(38 * scale, 95 * scale)
                ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f)
                ImGui.pushStyleColor(ImGuiCol.ScrollbarBg, 0f, 0f, 0f, 0f)
                ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab, 0.58f, 0.45f, 0.35f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabHovered, 0.6f, 0.5f, 0.4f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabActive, 0.62f, 0.52f, 0.4f, 1f)
                ImGui.beginChild("##choices", 168 * scale, 80f * scale, false, ImGuiWindowFlags.AlwaysAutoResize)
                for (i in choices.indices) {
                    if (button(
                            choices[i],
                            CHOICE_BUTTON,
                            163 * scale,
                            22 * scale
                        )
                    ) {
                        choices.clear()
                        OnChoicePerform(i).send()
                        break
                    }
                }
                ImGui.endChild()
                ImGui.popStyleColor(5)
            }

            ImGui.popTextWrapPos()
        }
    }

    private fun button(
        label: String,
        image: ResourceLocation,
        width: Float,
        height: Float,
        horizontal: Boolean = false,
    ): Boolean {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)

        val cursorPos = ImGui.getCursorPos()

        var height = if (label.length > 30) height * 1.5f else height
        var image =
            if (label.length > 30) "${image.namespace}:${image.path.substringBeforeLast(".png")}_medium.png".rl else image

        height = if (label.length > 60) height / 1.5f * 2.15f else height
        image =
            if (label.length > 60) "${image.namespace}:${image.path.substringBeforeLast("_medium.png")}_large.png".rl else image


        ImGui.invisibleButton("##$label", width, height)
        val hovered = ImGui.isItemHovered()
        val pressed = ImGui.isItemClicked()

        ImGui.setCursorPos(cursorPos.x, cursorPos.y)
        if (horizontal) {
            ImGuiMethods.image(
                image, width, height, width * 2, height,
                u0 = if (hovered) width else 0f,
                u1 = if (hovered) width * 2 else width
            )
        } else {
            ImGuiMethods.image(
                image, width, height, width, height * 2,
                v0 = if (hovered) height else 0f,
                v1 = if (hovered) height * 2 else height
            )
        }
        val textSize = ImGui.calcTextSize(label, false, 140f * scale)
        ImGui.setCursorPos(cursorPos.x + width / 2 - textSize.x / 2, cursorPos.y + 8f)

        if (label.isNotEmpty()) {
            val old = ImGui.getStyle().getColor(ImGuiCol.Text)
            ImGui.getStyle().setColor(ImGuiCol.Text, 0f, 0f, 0f, 1f)
            ImGui.textWrapped(label)
            ImGui.getStyle().setColor(ImGuiCol.Text, old.x, old.y, old.z, old.w)
        }

        ImGui.setCursorPos(cursorPos.x, cursorPos.y + height)

        ImGui.popStyleVar()

        return pressed
    }

    private fun npc(entity: LivingEntity, width: Float, height: Float, offsetY: Float, scale: Float) {
        ImGuiMethods.entity(entity, width, height, 0f, offsetY, scale)
    }

    override fun shouldCloseOnEsc() = false
}
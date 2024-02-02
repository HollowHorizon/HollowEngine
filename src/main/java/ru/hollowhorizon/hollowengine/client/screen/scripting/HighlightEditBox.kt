package ru.hollowhorizon.hollowengine.client.screen.scripting

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.Whence
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl
import kotlin.math.max

class HighlightEditBox(width: Int, height: Int) : MultiLineEditBox(
    Minecraft.getInstance().font, 0, 0, width, height, "".mcText, "".mcText
) {
    var isTabPressed = false

    init {
        value = ""
    }

    override fun renderButton(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        if (this.visible) {
            RenderSystem.setShaderTexture(0, "hollowengine:textures/gui/multiline_field.png".rl)

            GuiComponent.blit(
                pPoseStack,
                x,
                y,
                0f,
                if (isFocused) height.toFloat() else 0f,
                width,
                height,
                width,
                height * 2
            )

            enableScissor(this.x - 40, this.y + 1, this.x + this.width - 1, this.y + this.height - 1)
            pPoseStack.pushPose()
            pPoseStack.translate(0.0, -this.scrollAmount(), 0.0)
            this.renderContents(pPoseStack, pMouseX, pMouseY, pPartialTick)
            pPoseStack.popPose()
            disableScissor()
            this.renderDecorations(pPoseStack)
        }
    }

    override fun renderContents(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        val pressed = GLFW.glfwGetKey(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_TAB) != 0
        if (!pressed && isTabPressed) {
            textField.insertText("    ")
        }
        isTabPressed = pressed


        val s = textField.value()
        if (s.isEmpty() && !this.isFocused) {
            font.drawWordWrap(
                this.placeholder,
                this.x + this.innerPadding(),
                this.y + this.innerPadding(),
                this.width - this.totalInnerPadding(), -857677600
            )
        } else {
            val i = textField.cursor()
            val flag = this.isFocused && this.frame / 6 % 2 == 0
            val flag1 = i < s.length
            var j = 0
            var k = 0
            var l = this.y + this.innerPadding()

            for (stringView in textField.iterateLines()) {
                font.drawScaled(pPoseStack, Anchor.END, (l / 9 - 1).toString().mcText, x, l, 0xFFFFFF, 1.2f)

                val flag2 = this.withinContentAreaTopBottom(l, l + 9)
                if (flag && flag1 && i >= stringView.beginIndex() && i <= stringView.endIndex()) {
                    if (flag2) {
                        val jj = this.x + this.innerPadding() + font.width(
                            s.substring(
                                stringView.beginIndex(),
                                i
                            )
                        ) - 1
                        fill(pPoseStack, jj, l - 1, jj + 1, l + 1 + 9, -3092272)
                        j = renderLine(
                            pPoseStack, s.substring(stringView.beginIndex, stringView.endIndex),
                            (this.x + this.innerPadding()).toFloat(), l.toFloat(), -2039584
                        )
                    }
                } else {
                    if (flag2) {
                        j = renderLine(
                            pPoseStack, s.substring(stringView.beginIndex, stringView.endIndex),
                            (this.x + this.innerPadding()).toFloat(), l.toFloat(), -2039584
                        ) - 1
                    }
                    k = l
                }
                l += 9
            }

            if (flag && !flag1 && this.withinContentAreaTopBottom(k, k + 9)) {
                font.drawShadow(pPoseStack, "_", j.toFloat(), k.toFloat(), -3092272)
            }

            if (textField.hasSelection()) {
                val selected = textField.selected
                val k1 = this.x + this.innerPadding()
                l = this.y + this.innerPadding()

                for (line in textField.iterateLines()) {
                    if (selected.beginIndex() > line.endIndex()) {
                        l += 9
                    } else {
                        if (line.beginIndex() > selected.endIndex()) break

                        if (this.withinContentAreaTopBottom(l, l + 9)) {
                            val t1 = s.substring(
                                line.beginIndex(),
                                max(selected.beginIndex().toDouble(), line.beginIndex().toDouble()).toInt()
                            )
                            val t2 = s.substring(line.beginIndex(), selected.endIndex())
                            val i1 = font.width(t1)
                            val j1 =
                                if (selected.endIndex() > line.endIndex()) width - this.innerPadding()
                                else font.width(t2)

                            this.renderHighlight(pPoseStack, k1 + i1, l, k1 + j1, l + 9)
                        }

                        l += 9
                    }
                }
            }
        }
    }

    fun renderLine(pPoseStack: PoseStack, pText: String, pX: Float, pY: Float, pColor: Int): Int {
        var x = pX.toInt()
        var i = 0
        val count = pText.count { it == ' ' }
        pText.split(" ").forEach { word ->
            val color =
                when (word) {
                    in KotlinSyntax.operators -> KotlinSyntax.style.other
                    in KotlinSyntax.special -> KotlinSyntax.style.special
                    in KotlinSyntax.primaryKeywords -> KotlinSyntax.style.primary
                    in KotlinSyntax.secondaryKeywords -> KotlinSyntax.style.secondary
                    else -> pColor
                }

            val space = if (i == count) "" else " "

            when {
                word.any(Character::isDigit) -> {
                    var lastChar = 'a'
                    for (char in word) {
                        x = if (Character.isDigit(char) || char == '.' || (char == 'f' && Character.isDigit(lastChar))) {
                            font.drawShadow(pPoseStack, char + "", x.toFloat(), pY, KotlinSyntax.style.numbers)
                        } else {
                            font.drawShadow(pPoseStack, char + "", x.toFloat(), pY, color)
                        }
                        x -= 1
                        lastChar = char
                    }
                }
                word.contains(".") -> {
                    val words = word.split(".")
                    words.forEach { key ->
                        val isMethod = key.contains("(")

                        x = font.drawShadow(pPoseStack, key, x.toFloat(), pY, if(isMethod) KotlinSyntax.style.methods else KotlinSyntax.style.properties) - 1
                        if(key != words.last()) x = font.drawShadow(pPoseStack, ".", x.toFloat(), pY, color) - 1
                    }
                }
                else -> {
                    val isMethod = word.contains("(")

                    x = if(word == "{" || word == "}") font.drawShadow(pPoseStack, word, x.toFloat(), pY, KotlinSyntax.style.numbers) - 1
                    else font.drawShadow(pPoseStack, word, x.toFloat(), pY, if(isMethod) KotlinSyntax.style.methods else color) - 1
                }
            }

            x = font.drawShadow(pPoseStack, space, x.toFloat(), pY, color) - 1

            i++
        }

        return x
    }

    private fun renderHighlight(pPoseStack: PoseStack, pStartX: Int, pStartY: Int, pEndX: Int, pEndY: Int) {
        val matrix = pPoseStack.last().pose()
        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder
        RenderSystem.setShader { GameRenderer.getPositionShader() }
        RenderSystem.setShaderColor(0.0f, 0.0f, 1.0f, 1.0f)
        RenderSystem.disableTexture()
        RenderSystem.enableColorLogicOp()
        RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE)
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        builder.vertex(matrix, pStartX.toFloat(), pEndY.toFloat(), 0.0f).endVertex()
        builder.vertex(matrix, pEndX.toFloat(), pEndY.toFloat(), 0.0f).endVertex()
        builder.vertex(matrix, pEndX.toFloat(), pStartY.toFloat(), 0.0f).endVertex()
        builder.vertex(matrix, pStartX.toFloat(), pStartY.toFloat(), 0.0f).endVertex()
        tesselator.end()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.disableColorLogicOp()
        RenderSystem.enableTexture()
    }

    override fun keyPressed(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(SoundEvents.METAL_HIT, 1f)
        )

        if (pKeyCode == GLFW.GLFW_KEY_ENTER) {
            val view = textField.getLineView(textField.lineAtCursor)
            val line = textField.value().substring(view.beginIndex, view.endIndex)
            var spaceCount = 0
            for (char in line) {
                if (char != ' ') break
                spaceCount++
            }

            val cursor = textField.cursor()
            val newBlock =
                cursor != 0 && cursor != textField.value().length && textField.value()[cursor - 1] == '{' && textField.value()[cursor] == '}'

            if (newBlock) {
                spaceCount += 4
            }

            val spaces = " ".repeat(spaceCount)
            textField.insertText("\n$spaces")

            if (newBlock) {
                textField.setSelecting(false)
                textField.insertText("\n${" ".repeat(spaceCount - 4)}")
                textField.seekCursor(Whence.RELATIVE, -(spaceCount - 3))
            }
            return true

        }
        if (pKeyCode == GLFW.GLFW_KEY_BACKSPACE) {
            val view = textField.getLineView(textField.lineAtCursor)
            val line = textField.value().substring(view.beginIndex, textField.cursor())
            if (line.endsWith("    ")) {
                textField.deleteText(-4)
                return true
            }
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers)
    }

    override fun charTyped(pCodePoint: Char, pModifiers: Int): Boolean {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(SoundEvents.METAL_HIT, 1f)
        )

        if (textField.value().isEmpty()) return super.charTyped(pCodePoint, pModifiers)

        if (pCodePoint == '{') {
            val cursor = textField.cursor()
            if (cursor == textField.value().length || textField.value()[cursor] != '}') {
                textField.setSelecting(false)
                textField.insertText("{}")
                textField.seekCursor(Whence.RELATIVE, -1)
                return true
            }
        }
        if (pCodePoint == '}') {
            val cursor = textField.cursor()
            if (cursor != 0 && cursor != textField.value().length && textField.value()[cursor - 1] == '{' && textField.value()[cursor] == '}') {
                textField.setSelecting(false)
                textField.seekCursor(Whence.RELATIVE, 1)
                return true
            }
        }
        return super.charTyped(pCodePoint, pModifiers)
    }
}
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

package ru.hollowhorizon.hollowengine.client.utils

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.utils.HollowJavaUtils
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.InputStream

// Only Client utils
val mc: Minecraft get() = Minecraft.getInstance()

enum class Axis(val x: Float, val y: Float, val z: Float) {
    X(1f, 0f, 0f),
    Y(0f, 1f, 0f),
    Z(0f, 0f, 1f);
}

val hasShaders get() = ModList.isLoaded("oculus") || ModList.isLoaded("iris") || ModList.isLoaded("optifine")

val areShadersEnabled get() = hasShaders && areShadersEnabled_()

lateinit var areShadersEnabled_: () -> Boolean
lateinit var shouldOverrideShaders: () -> Boolean

val registryAccess: RegistryAccess
    get() = if (currentServer is IntegratedServer) Minecraft.getInstance().connection?.registryAccess()
        ?: currentServer.registryAccess()
    else currentServer.registryAccess()

fun isCursorAtPos(cursorX: Int, cursorY: Int, x: Int, y: Int, width: Int, height: Int) : Boolean =
    cursorX >= x && cursorY >= y && cursorX <= x + width && cursorY <= y + height

fun isCursorAtPos(cursorX: Double, cursorY: Double, x: Int, y: Int, width: Int, height: Int) : Boolean =
    cursorX >= x && cursorY >= y && cursorX <= x + width && cursorY <= y + height

fun GuiGraphics.defaultBlit(
    id: ResourceLocation,
    x: Int,
    y: Int,
    uOffset: Float = 0f,
    vOffset: Float = 0f,
    width: Int = 176,
    height: Int = 166,
    textureWidth: Int = 256,
    textureHeight: Int = 256
) = this.blit(id, x, y, uOffset, vOffset, width, height, textureWidth, textureHeight)

fun AbstractContainerScreen<*>.xPos(x: Int): Int {
    val j = ((this.width / 2) - (this.imageWidth / 2))
    return x + j
}

fun AbstractContainerScreen<*>.yPos(y: Int): Int {
    val j = ((this.height / 2) - (this.imageHeight / 2))
    return y + j
}

fun AbstractContainerScreen<*>.xPos(x: Float): Float {
    val j = ((this.width / 2) - (this.imageWidth / 2))
    return x + j
}

fun AbstractContainerScreen<*>.yPos(y: Float): Float {
    val j = ((this.height / 2) - (this.imageHeight / 2))
    return y + j
}

val AbstractContainerScreen<*>.guiPosLeft: Int
    get() = (this.width - this.imageWidth) / 2

val AbstractContainerScreen<*>.guiPosTop: Int
    get() = (this.height - this.imageHeight) / 2

fun resource(resource: String) = "${HollowCore.MODID}:$resource".rl.stream

fun ResourceLocation.exists(): Boolean {
    return mc.resourceManager.getResource(this).isPresent
}

val ResourceLocation.stream: InputStream
    get() = HollowJavaUtils.getResource(this)

fun Screen.open() {
    mc.setScreen(this)
}

@Deprecated("Use new format", replaceWith = ReplaceWith("this.stream", "ru.hollowhorizon.hollowengine.client.utils.stream"))
fun ResourceLocation.toIS(): InputStream {
    return HollowJavaUtils.getResource(this)
}

fun Int.toRGBA(): HollowColor {
    return HollowColor(
        (this shr 16 and 255).toFloat() / 255.0f,
        (this shr 8 and 255).toFloat() / 255.0f,
        (this and 255).toFloat() / 255.0f,
        (this shr 24 and 255).toFloat() / 255.0f
    )
}

fun Int.toABGR(): HollowColor {
    return HollowColor(
        (this and 255).toFloat() / 255.0f,
        (this shr 8 and 255).toFloat() / 255.0f,
        (this shr 16 and 255).toFloat() / 255.0f,
        (this shr 24 and 255).toFloat() / 255.0f
    )
}

fun ResourceLocation.toTexture(): AbstractTexture = mc.textureManager.getTexture(this)

data class HollowColor(val r: Float, val g: Float, val b: Float, val a: Float) {
    constructor(r: Int, g: Int, b: Int, a: Int) : this(
        r.toFloat() / 255f,
        g.toFloat() / 255f,
        b.toFloat() / 255f,
        a.toFloat() / 255f
    )

    fun toRGBA(): Int {
        val red = (r * 255.0f + 0.5f).toInt() shl 16
        val green = (g * 255.0f + 0.5f).toInt() shl 8
        val blue = (b * 255.0f + 0.5f).toInt()
        val alpha = (a * 255.0f + 0.5f).toInt() shl 24
        return alpha or red or green or blue
    }

    fun toABGR(): Int {
        return ((a * 255f).toInt() shl 24) or
                ((b * 255f).toInt() shl 16) or
                ((g * 255f).toInt() shl 8) or
                (r * 255f).toInt()
    }
}

inline fun PoseStack.use(usable: PoseStack.() -> Unit) {
    this.pushPose()
    usable()
    this.popPose()
}

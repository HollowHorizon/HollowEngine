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

package ru.hollowhorizon.hollowengine.client.gui

import imgui.*
import imgui.extension.nodeditor.NodeEditor
import imgui.type.ImInt
import net.minecraft.client.Minecraft


fun ImDrawList.addRectFilled(min: ImVec2, max: ImVec2, imColor: Int) {
    addRectFilled(min.x, min.y, max.x, max.y, imColor)
}

fun ImDrawList.addRectFilled(min: ImVec2, max: ImVec2, imColor: ImVec4) {
    addRectFilled(min.x, min.y, max.x, max.y, ImGui.colorConvertFloat4ToU32(imColor.x, imColor.y, imColor.z, imColor.w))
}

fun ImDrawList.addLine(min: ImVec2, max: ImVec2, color: Int, borderWidth: Float) {
    addLine(min.x, min.y, max.x, max.y, color, borderWidth)
}

fun ImDrawList.addLine(min: ImVec2, max: ImVec2, color: ImVec4, borderWidth: Float) {
    addLine(min.x, min.y, max.x, max.y, ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w), borderWidth)
}

fun ImDrawList.addLine(min: ImVec2, max: ImVec2, color: Int) {
    addLine(min.x, min.y, max.x, max.y, color)
}

fun ImDrawList.addText(font: ImFont, size: Float, pos: ImVec2, color: Int, text: String) {
    addText(font, size, pos.x, pos.y, color, text)
}

fun ImDrawList.addText(pos: ImVec2, color: Int, text: String) {
    addText(pos.x, pos.y, color, text)
}

fun ImDrawList.addPolyline(points: Array<ImVec2>, count: Int, color: ImVec4, fl: Float) {
    addPolyline(
        points,
        count,
        ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w),
        0,
        fl
    )
}

fun ImDrawList.addRect(min: ImVec2, max: ImVec2, color: Int) {
    addRect(min.x, min.y, max.x, max.y, color)
}

fun ImDrawList.addRect(min: ImVec2, max: ImVec2, color: ImVec4) {
    addRect(min.x, min.y, max.x, max.y, ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w))
}

fun ImDrawList.addRect(min: ImVec2, max: ImVec2, color: Int, radius: Float, flags: Int, rounding: Float) {
    addRect(min.x, min.y, max.x, max.y, color, radius, flags, rounding)
}

fun ImDrawList.addRectFilled(min: ImVec2, max: ImVec2, color: Int, radius: Float) {
    addRectFilled(min.x, min.y, max.x, max.y, color, radius)
}

fun ImDrawList.addRectFilled(min: ImVec2, max: ImVec2, color: ImVec4, radius: Float) {
    addRectFilled(min.x, min.y, max.x, max.y, ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w), radius)
}

operator fun ImVec4.times(scalar: Float): ImVec4 {
    return ImVec4(
        (x * scalar).coerceAtMost(1f),
        (y * scalar).coerceAtMost(1f),
        (z * scalar).coerceAtMost(1f),
        (w * scalar).coerceAtMost(1f)
    )
}

operator fun ImVec2.times(scalar: Float): ImVec2 {
    return ImVec2(x * scalar, y * scalar)
}

operator fun ImVec2.plus(scalar: Int): ImVec2 {
    return ImVec2(x + scalar, y + scalar)
}

operator fun ImVec2.plus(scalar: Float): ImVec2 {
    return ImVec2(x + scalar, y + scalar)
}

operator fun ImVec2.minus(scalar: Int): ImVec2 {
    return ImVec2(x - scalar, y - scalar)
}

operator fun ImVec2.minus(scalar: Float): ImVec2 {
    return ImVec2(x - scalar, y - scalar)
}

val Int.imVec4 get() = ImVec4().also { ImGui.colorConvertU32ToFloat4(this, it) }

fun hsv(hue: Float, saturation: Float, value: Float): Int {
    val rgb = floatArrayOf(0f, 0f, 0f)
    ImGui.colorConvertHSVtoRGB(floatArrayOf(hue, saturation, value), rgb)
    return ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], 1f)
}

fun hsva(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
    val rgb = floatArrayOf(0f, 0f, 0f)
    ImGui.colorConvertHSVtoRGB(floatArrayOf(hue, saturation, value), rgb)
    return ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], alpha)
}

val width get() = Minecraft.getInstance().window.width.toFloat()
val height get() = Minecraft.getInstance().window.height.toFloat()

fun comboNode(type: String, values: Array<String>, value: ImInt): Boolean {
    var changed = false
    ImGui.pushItemWidth(350f)
    val width = values.map { ImGui.calcTextSize(it) }.maxBy { it.x }
    if(ImGui.button(values[value.get()], width.x+5, width.y+5f)) ImGui.openPopup(type)
    val cursorX = NodeEditor.toScreenX(ImGui.getCursorPos().x)
    val cursorY = NodeEditor.toScreenY(ImGui.getCursorPos().y)
    ImGui.sameLine()
    ImGui.text(type)

    NodeEditor.suspend()
    ImGui.setNextWindowSize(0f, 512f)
    if(ImGui.beginPopup(type)) {
        ImGui.setWindowPos(cursorX, cursorY)

        for((i, name) in values.withIndex()) {
            if(ImGui.selectable(name)) {
                value.set(i)
                changed = true
            }
        }
        ImGui.endPopup()
    }
    NodeEditor.resume()
    ImGui.popItemWidth()
    return changed
}
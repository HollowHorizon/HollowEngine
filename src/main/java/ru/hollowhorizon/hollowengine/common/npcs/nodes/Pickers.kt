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

package ru.hollowhorizon.hollowengine.common.npcs.nodes

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImDouble
import imgui.type.ImInt
import net.minecraft.world.phys.Vec3

val VEC3_PICKER: (Vec3?) -> Vec3 = { old ->
    val x = ImDouble(old?.x ?: 0.0)
    val y = ImDouble(old?.y ?: 0.0)
    val z = ImDouble(old?.z ?: 0.0)
    ImGui.pushItemWidth(150f)
    ImGui.inputDouble("X", x, 0.5, 1.0, "%.3f"); ImGui.sameLine()
    ImGui.inputDouble("Y", y, 0.5, 1.0, "%.3f"); ImGui.sameLine()
    ImGui.inputDouble("Z", z, 0.5, 1.0, "%.3f")
    ImGui.popItemWidth()
    Vec3(x.get(), y.get(), z.get())
}

val DOUBLE_PICKER: (Double?) -> Double = { old ->
    val value = ImDouble(old ?: 0.0)
    ImGui.pushItemWidth(150f)
    ImGui.inputDouble("Значение", value, 0.5, 1.0, "%.3f")
    ImGui.popItemWidth()
    value.get()
}

val BOOLEAN_PICKER: (Boolean?) -> Boolean = { old ->
    val value = ImBoolean(old ?: false)
    ImGui.checkbox("Значение", value)
    value.get()
}

private val fcache = FloatArray(1)

val FLOAT_PICKER: (Float?) -> Float = { old ->
    fcache[0] = old ?: 1f
    ImGui.pushItemWidth(150f)
    ImGui.dragFloat("Значение", fcache, 0.01f, 0f, 50f, "%.3f")
    ImGui.popItemWidth()
    fcache[0]
}

val INT_PICKER: (Int?) -> Int = { old ->
    val int = ImInt(old ?: 1)
    ImGui.pushItemWidth(150f)
    ImGui.inputInt("Значение", int); ImGui.sameLine()
    ImGui.popItemWidth()
    int.get()
}
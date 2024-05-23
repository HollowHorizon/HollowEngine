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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.pins

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.npcs.nodes.VEC3_PICKER

class Vec3Pin : Pin<Vec3>() {
    override val picker = VEC3_PICKER

    init {
        value = Vec3.ZERO
    }

    override fun serializeNBT() = super.serializeNBT().apply {
        val vec = value ?: return@apply
        putDouble("x", vec.x)
        putDouble("y", vec.y)
        putDouble("z", vec.z)
    }

    override fun deserializeNBT(tag: CompoundTag) {
        super.deserializeNBT(tag)
        if (!tag.contains("x")) return

        value = Vec3(
            tag.getDouble("x"),
            tag.getDouble("y"),
            tag.getDouble("z")
        )
    }
}
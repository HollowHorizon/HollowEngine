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

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

fun IContextBuilder.wait(time: () -> Int) = +WaitNode(time)

class WaitNode(var startTime: () -> Int) : Node() {
    var isStarted = false
    var time = 0

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            time = startTime()
        }
        return time-- > 0
    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("time", time)
        putBoolean("isStarted", isStarted)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        time = nbt.getInt("time")
        isStarted = nbt.getBoolean("isStarted")
    }
}

fun IContextBuilder.await(condition: () -> Boolean) = +object : Node() {
    override fun tick(): Boolean {
        return condition()
    }

    override fun serializeNBT() = CompoundTag()

    override fun deserializeNBT(p0: CompoundTag) {}

}

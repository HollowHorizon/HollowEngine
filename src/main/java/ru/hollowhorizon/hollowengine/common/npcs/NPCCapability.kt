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

package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.network.MouseButton

@HollowCapabilityV2(NPCEntity::class)
class NPCCapability: CapabilityInstance() {
    var hitboxMode by syncable(HitboxMode.PULLING)
    var icon by syncable(NpcIcon.EMPTY)
    var mouseButton by syncable(MouseButton.NONE)
    var script by syncable(ScriptGraph())
}

@Serializable
class NpcIcon private constructor(
    val image: @Serializable(ForResourceLocation::class) ResourceLocation,
    var scale: Float = 1f,
    var offsetY: Float = 0f
) {
    companion object {
        fun create(image: String, scale: Float = 1f, offsetY: Float = 0f) = NpcIcon(image.rl, scale, offsetY)

        val EMPTY = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/empty.png"))
        val DIALOGUE = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/dialogue.png"))
        val QUESTION = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/question.png"))
        val WARN = NpcIcon(ResourceLocation("hollowengine:textures/gui/icons/warn.png"))
    }

    override fun equals(other: Any?): Boolean {
        if(other !is NpcIcon) return false
        return this.image == other.image
    }

    override fun hashCode(): Int {
        return image.hashCode()
    }
}
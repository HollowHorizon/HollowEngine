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

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.particles

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.effects.ParticleEmitterInfo
import ru.hollowhorizon.hc.common.effects.ParticleHelper
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.next

class EffekseerContainer {
    var world = "minecraft:overworld"
    var path = "hc:lightning/lightning"
    var pos = Vec3.ZERO
    internal var scale = Vec3(1.0, 1.0, 1.0)
    internal var rotation = Vec3(0.0, 0.0, 0.0)
    var emitter: ResourceLocation? = null
    var target = ""
    var entity: Entity? = null
    var dynamic0 = 0f
    var dynamic1 = 0f
    var dynamic2 = 0f
    var dynamic3 = 0f

    fun scale(x: Number, y: Number, z: Number) {
        scale = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    }

    fun scale(size: Number) {
        scale = Vec3(size.toDouble(), size.toDouble(), size.toDouble())
    }

    fun rotation(x: Number, y: Number, z: Number) {
        rotation = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    }
}

fun IContextBuilder.effekseer(path: EffekseerContainer.() -> Unit) = next {
    val container = EffekseerContainer().apply(path)
    val dimension = manager.server.levelKeys().find { it.location() == container.world.rl }
        ?: throw IllegalStateException("Dimension ${container.world} not found. Or not loaded!")

    val info =
        ParticleEmitterInfo(container.path.removeSuffix(".efkefc").rl, container.emitter).apply {
            if (container.entity != null) {
                bindOnEntity(container.entity!!)
                if (container.target.isNotEmpty()) bindOnTarget(container.target)
            } else position(container.pos)
            scale(container.scale.x.toFloat(), container.scale.y.toFloat(), container.scale.z.toFloat())
            rotation(container.rotation.x.toFloat(), container.rotation.y.toFloat(), container.rotation.z.toFloat())
            dynamic0 = container.dynamic0
            dynamic1 = container.dynamic1
            dynamic2 = container.dynamic2
            dynamic3 = container.dynamic3
        }

    ParticleHelper.addParticle(manager.server.getLevel(dimension)!!, info, true)
}
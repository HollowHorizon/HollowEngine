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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.npcs

import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.colored
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.plus
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.nodes.base.OperationNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.inPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.*

open class NPCOperationNode : OperationNode() {
    val npc by inPin<NPCEntity, NpcPin>()
}

class NpcSuspendScriptNode : NPCOperationNode() {
    val suspend by inPin<Boolean, BooleanPin>().apply {
        name = "Приостановить скрипт"
    }

    override fun tick(graph: ScriptGraph) {
        if (suspend) npc.navigation.stop()
        complete(graph)
    }
}

class MoveToNode : NPCOperationNode() {
    val target by inPin<Vec3, Vec3Pin>()
    val speed by inPin<Double, DoublePin>().apply {
        name = "Модификатор скорости"
    }
    val distance by inPin<Double, DoublePin>().apply {
        name = "Расстояние до остановки"
    }

    override fun tick(graph: ScriptGraph) {
        npc.navigation.moveTo(npc.navigation.createPath(target.x, target.y, target.z, 0), speed)
        if (npc.distanceToSqr(target) < distance * distance) {
            npc.navigation.stop()
            complete(graph)
        }
    }
}

class LookAtNode : NPCOperationNode() {
    val target by inPin<Vec3, Vec3Pin>()
    val speedX by inPin<Float, FloatPin>().apply {
        name = "Скорость по X"
    }
    val speedY by inPin<Float, FloatPin>().apply {
        name = "Скорость по Y"
    }
    var time = -1

    override fun tick(graph: ScriptGraph) {
        npc.lookControl.setLookAt(target.x, target.y, target.z, speedX, speedY)

        if (time == -1) time = 30

        if (time-- == 0) {
            time = -1
            complete(graph)
        }
    }
}

class SayNode : NPCOperationNode() {
    val message by inPin<String, StringPin>().apply {
        name = "Сообщение в чат"
    }

    override fun tick(graph: ScriptGraph) {
        val name = npc.displayName

        npc.server?.playerList?.players?.forEach {
            it.sendSystemMessage(Component.empty() + "[".mcText.colored(0xDAA520) + name + "] ".mcText.colored(0xDAA520) + message.mcText)
        }

        complete(graph)
    }
}

class NpcInteractNode : NPCOperationNode() {
    var isStarted = false
    private var hasInteracted = false
    override fun tick(graph: ScriptGraph) {
        val npc = npc
        val server = npc.server ?: return

        if (!isStarted) {
            isStarted = true

        }
        if (hasInteracted) {
            isStarted = false
            hasInteracted = false
            complete(graph)
        }
    }
}
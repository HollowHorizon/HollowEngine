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

package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.base.*
import ru.hollowhorizon.hollowengine.common.npcs.nodes.npcs.*
import ru.hollowhorizon.hollowengine.common.npcs.nodes.server.MessageNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.server.players.NearestPlayerNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.server.players.PlayerByNickNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.server.players.PlayerInfoNode

object NodesRegistry : EngineRegistry<ScriptNode>() {
    init {
        reload()
    }

    override fun init() {
        register("hollowengine:general/start".rl, ::StartNode)
        register("hollowengine:general/end".rl, ::EndNode)
        register("hollowengine:general/condition".rl, ::IfNode)

        register("hollowengine:npcs/move_to".rl, ::MoveToNode)
        register("hollowengine:npcs/look_at".rl, ::LookAtNode)
        register("hollowengine:npcs/say".rl, ::SayNode)
        register("hollowengine:npcs/animation_start".rl, ::NpcStartAnimationNode)
        register("hollowengine:npcs/animation_stop".rl, ::NpcStopAnimationNode)
        register("hollowengine:npcs/interact".rl, ::NpcInteractNode)
        register("hollowengine:npcs/throw_item".rl, ::NpcGiveItemNode)
        register("hollowengine:npcs/suspend".rl, ::NpcSuspendScriptNode)

        register("hollowengine:waiters/wait".rl, ::WaitNode)

        register("hollowengine:server/message".rl, ::MessageNode)
        register("hollowengine:server/command".rl, ::CommandNode)
        register("hollowengine:server/player_by_nick".rl, ::PlayerByNickNode)
        register("hollowengine:server/npc_by_uuid".rl, ::GetNpcNode)
        register("hollowengine:server/nearest_player".rl, ::NearestPlayerNode)
        register("hollowengine:server/player_info".rl, ::PlayerInfoNode)
        register("hollowengine:server/npc_info".rl, ::NpcInfoNode)
    }
}
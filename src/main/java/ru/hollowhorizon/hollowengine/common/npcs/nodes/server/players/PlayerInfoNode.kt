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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.server.players

import net.darkhax.gamestages.GameStageHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.inPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.outPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.*

class PlayerInfoNode : ScriptNode() {
    val player by inPin<Player, PlayerPin>()
    val position by outPin<Vec3, Vec3Pin>().apply {
        name = "Координаты"
        updater = { player.position() }
    }
    val positionHead by outPin<Vec3, Vec3Pin>().apply {
        name = "Координаты Глаз"
        updater = { player.eyePosition }
    }
    val name by outPin<String, StringPin>().apply {
        name = "Ник"
        updater = { player.name.string }
    }
    val health by outPin<Float, FloatPin>().apply {
        name = "Здоровье"
        updater = { player.health }
    }
}

class StageCheckerNode : ScriptNode() {
    val player by inPin<Player, PlayerPin>()
    val stage by inPin<String, StringPin>().apply {
        name = "Уровень"
    }
    val position by outPin<Boolean, BooleanPin>().apply {
        name = "Наличие уровня"
        updater = { GameStageHelper.hasStage(player, stage) }
    }
}
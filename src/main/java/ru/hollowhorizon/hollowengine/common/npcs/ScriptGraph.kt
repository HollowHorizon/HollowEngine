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

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.colored
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.connections.Connection
import ru.hollowhorizon.hollowengine.common.npcs.connections.toConnection
import ru.hollowhorizon.hollowengine.common.npcs.connections.toTag
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ForgeEventNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.base.StartNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.toScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.toTag
import ru.hollowhorizon.hollowengine.common.util.deserialize
import ru.hollowhorizon.hollowengine.common.util.serialize

lateinit var CURRENT_GRAPH: ScriptGraph

open class  ScriptGraph : INBTSerializable<CompoundTag> {
    val nodes = ArrayList<ScriptNode>()
    val connections = ArrayList<Connection>()
    lateinit var npc: NPCEntity
    val activeNodes = ArrayList<ScriptNode>()
    var editorInfo = ""
    var suspend = false

    fun init(npc: NPCEntity) {
        this.npc = npc
    }

    fun restart() {
        activeNodes.clear()
        activeNodes.addAll(nodes.filterIsInstance<StartNode>())

        nodes.filterIsInstance<ForgeEventNode>().forEach { _ ->
            MinecraftForge.EVENT_BUS.register(this)
        }
        suspend = false
    }

    fun update() {
        if (suspend || !(npc.level as ServerLevel).areEntitiesLoaded(ChunkPos.asLong(npc.blockPosition()))) return

        try {
            CURRENT_GRAPH = this

            if(nodes.any { !it.isLoaded() }) return

            val nodes = activeNodes.toList()

            nodes.forEach { it.tick(this) }
        } catch (e: Exception) {
            npc.server?.playerList?.players?.filter { it.hasPermissions(2) }?.forEach {
                it.sendSystemMessage(
                    "nodes.$MODID.npcs.cause".mcTranslate(npc.name.string).colored(
                        0xFF222
                    )
                )

                HollowCore.LOGGER.error("NPC ${npc.name.string} cause errors! ", e)

                activeNodes.clear()
            }
        }
    }

    override fun serializeNBT() = CompoundTag().apply {
        put("nodes", nodes.serialize { it.toTag() })
        put("connections", connections.serialize { it.toTag(this@ScriptGraph) })
        put("active_nodes", activeNodes.serialize { IntTag.valueOf(nodes.indexOf(it)) })
        putString("editor_info", editorInfo)
        putBoolean("is_paused", suspend)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        CURRENT_GRAPH = this

        nodes.filterIsInstance<ForgeEventNode>().forEach { MinecraftForge.EVENT_BUS.unregister(this) }
        nodes.clear()
        try {
            nodes.deserialize(nbt.getList("nodes", 10)) { (it as CompoundTag).toScriptNode() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        connections.clear()
        connections.deserialize(nbt.getList("connections", 10)) { (it as CompoundTag).toConnection(this) }
        activeNodes.clear()
        activeNodes.deserialize(nbt.getList("active_nodes", 3)) { nodes[(it as IntTag).asInt] }
        editorInfo = nbt.getString("editor_info")
        suspend = nbt.getBoolean("is_paused")
    }
}
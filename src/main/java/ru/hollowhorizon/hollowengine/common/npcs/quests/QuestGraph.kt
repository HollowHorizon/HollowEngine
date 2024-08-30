package ru.hollowhorizon.hollowengine.common.npcs.quests

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.nbt.INBTSerializable
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.serialize

class QuestGraph : INBTSerializable {
    val nodes = ArrayList<QuestNode>()
    val connections = ArrayList<QuestConnection>()

    override fun serialize() = CompoundTag().apply {
        put("quests", ListTag().apply { addAll(nodes.map(NBTFormat::serialize)) })
        put("connections", ListTag().apply {
            connections.forEach { connection ->
                add(CompoundTag().apply {
                    putInt("in", nodes.indexOf(connection.input))
                    putInt("out", nodes.indexOf(connection.output))
                })
            }
        })
    }

    override fun deserialize(tag: Tag) {
        nodes.clear()
        connections.clear()
        (tag as CompoundTag).apply {
            getList("quests", 10).forEach {
                nodes.add(NBTFormat.deserialize(it))
            }
            getList("connections", 10).forEach {
                val nbt = it as CompoundTag
                connections.add(
                    QuestConnection(
                        nodes[nbt.getInt("in")],
                        nodes[nbt.getInt("out")]
                    )
                )
            }
        }
    }
}
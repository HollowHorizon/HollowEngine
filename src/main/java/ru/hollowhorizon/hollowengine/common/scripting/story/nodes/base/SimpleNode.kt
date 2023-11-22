package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TranslatableComponent
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript

open class SimpleNode(val task: SimpleNode.() -> Unit) : Node() {
    override fun tick(): Boolean {
        task()
        return false
    }

    override fun serializeNBT() = CompoundTag()

    override fun deserializeNBT(nbt: CompoundTag) {}
}

class BooleanValue(var data: Boolean)

class CombinedNode(nodes: List<Node>) : Node() {
    val nodes = nodes.associateWith { BooleanValue(false) }

    init {
        nodes.forEach { it.parent = this }
    }

    override fun tick(): Boolean {
        for ((node, state) in nodes) {
            if (!state.data && !node.tick()) state.data = true
        }
        return nodes.values.any { !it.data }
    }

    override fun serializeNBT() = CompoundTag().apply {
        serializeNodes("nodes", nodes.keys)
        put("completed", ListTag().apply { addAll(nodes.values.map { ByteTag.valueOf(it.data) }) })
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        nbt.deserializeNodes("nodes", nodes.keys)
        val completed = nbt.getList("completed", 1)
        nodes.values.forEachIndexed { i, it ->
            it.data = (completed[i] as ByteTag) == ByteTag.ONE
        }
    }
}

fun IContextBuilder.send(text: Component) = +SimpleNode {
    manager.team.onlineMembers.forEach { it.sendMessage(text, it.uuid) }
}

fun IContextBuilder.startScript(text: String) = +SimpleNode {
    val file = text.fromReadablePath()
    if (!file.exists()) manager.team.onlineMembers.forEach {
        it.sendMessage(
            TranslatableComponent(
                "hollowengine.scripting.story.script_not_found",
                file.absolutePath
            ), it.uuid
        )
    }

    runScript(manager.server, manager.team, file)
}

fun IContextBuilder.execute(command: () -> String) = +SimpleNode {
    val server = this@execute.stateMachine.server
    val src = server.createCommandSourceStack()

    if(server.commands.performCommand(src.withPermission(4).withSuppressedOutput(), command()) == 0) {
        manager.team.onlineMembers.filter { it.abilities.instabuild }.forEach {
            it.sendMessage("Command \"${command()}\" execution failed!".mcText, it.uuid)
        }
    }
}


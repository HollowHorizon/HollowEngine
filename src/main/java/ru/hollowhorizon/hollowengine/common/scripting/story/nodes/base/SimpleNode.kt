package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
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

fun IContextBuilder.send(text: Component) = +SimpleNode {
    manager.team.onlineMembers.forEach { it.sendSystemMessage(text) }
}

fun IContextBuilder.startScript(text: String) = +SimpleNode {
    val file = text.fromReadablePath()
    if (!file.exists()) manager.team.onlineMembers.forEach {
        it.sendSystemMessage(
            Component.translatable(
                "hollowengine.scripting.story.script_not_found",
                file.absolutePath
            )
        )
    }

    runScript(manager.server, manager.team, file)
}

fun IContextBuilder.execute(command: () -> String) = +SimpleNode {
    val server = this@execute.stateMachine.server
    val src = server.createCommandSourceStack()

    if (server.commands.performPrefixedCommand(src.withPermission(4).withSuppressedOutput(), command()) == 0) {
        manager.team.onlineMembers.filter { it.abilities.instabuild }.forEach {
            it.sendSystemMessage("Command \"${command()}\" execution failed!".mcText)
        }
    }
}
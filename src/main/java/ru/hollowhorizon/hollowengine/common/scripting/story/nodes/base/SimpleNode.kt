package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TranslatableComponent
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.StoryLogger
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript

open class SimpleNode(val task: SimpleNode.() -> Unit) : Node() {
    override fun tick(): Boolean {
        task()
        return false
    }

    override fun serializeNBT() = CompoundTag()

    override fun deserializeNBT(nbt: CompoundTag) {
        //Ничего сериализуемого нет
    }
}

fun IContextBuilder.next(block: SimpleNode.() -> Unit) = +SimpleNode(block)

fun IContextBuilder.send(text: Component) = +SimpleNode {
    manager.team.onlineMembers.forEach { it.sendMessage(text, it.uuid) }
}

fun IContextBuilder.startScript(text: () -> String) = next {
    val file = text().fromReadablePath()
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

fun IContextBuilder.stopScript(file: () -> String) = next {
    StoryHandler.stopEvent(this@stopScript.stateMachine.team, file())
}

fun IContextBuilder.restartScript() = next {
    val team = this@restartScript.stateMachine.team
    StoryHandler.getEventByScript(team, this@restartScript.stateMachine)?.let {
        StoryHandler.restartEvent(team, it)
    }
}

fun IContextBuilder.execute(command: () -> String) = +SimpleNode {
    val server = this@execute.stateMachine.server
    val src = server.createCommandSourceStack()
        .withPermission(4)
        .withSuppressedOutput()

    if (server.commands.performCommand(src, command()) == 0) {
        StoryLogger.LOGGER.warn("Command \"${command()}\" execution failed!")
    }
}
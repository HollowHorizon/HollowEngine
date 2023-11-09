package ru.hollowhorizon.hollowengine.common.scripting.story

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ServerTickEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.deserializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.serializeNodes

open class StoryStateMachine(val server: MinecraftServer, val team: Team) : IContextBuilder {
    val variables = ArrayList<StoryVariable<*>>()
    private val nodes = ArrayList<Node>()
    private var currentIndex = 0
    var isStarted = false
    val isEnded get() = currentIndex >= nodes.size

    fun tick(event: ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        if (!isEnded && !nodes[currentIndex].tick()) currentIndex++
    }

    fun serialize() = CompoundTag().apply {
        serializeNodes("\$nodes", nodes)
        putInt("\$current", currentIndex)
        put("\$variables", ListTag().apply {
            addAll(variables.map { it.serializeNBT() })
        })
    }

    fun deserialize(nbt: CompoundTag) {
        nbt.deserializeNodes("\$nodes", nodes)
        currentIndex = nbt.getInt("\$current")
        variables.forEachIndexed { index, storyVariable ->
            storyVariable.deserializeNBT(nbt.getList("\$variables", 10).getCompound(index))
        }
    }

    override val stateMachine = this
    override fun <T : Node> T.unaryPlus(): T {
        this.manager = this@StoryStateMachine
        nodes.add(this)
        return this
    }
}



package ru.hollowhorizon.hollowengine.common.scripting.story

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ServerTickEvent
import ru.hollowhorizon.hc.client.models.gltf.animations.AnimationType
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.deserializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.serializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.NpcDelegate

open class StoryStateMachine(val server: MinecraftServer, val team: Team) : IContextBuilder {
    val variables = ArrayList<StoryVariable<*>>()
    private val nodes = ArrayList<Node>()
    private var currentIndex = 0
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

    override operator fun Node.unaryPlus() {
        this.manager = this@StoryStateMachine
        nodes.add(this)
    }

    override val stateMachine = this

    fun NPCEntity.Companion.creating(settings: NPCSettings, location: SpawnLocation): NpcDelegate {
        return NpcDelegate(settings, location).apply { manager = this@StoryStateMachine }
    }

    fun pos(x: Double, y: Double, z: Double) = Vec3(x, y, z)
    fun pos(x: Int, y: Int, z: Int) = Vec3(x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5)
    fun vec(x: Int, y: Int) = Vec2(x.toFloat(), y.toFloat())
    fun vec(x: Float, y: Float) = Vec2(x, y)
    val Int.sec get() = this * 20
    val Int.min get() = this * 1200
    val Int.hours get() = this * 72000
}



package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun IContextBuilder.main() {
    var input by InputNode()
    val npc by NPCEntity.creating(NPCSettings(), SpawnLocation(pos = pos(1, 2, 3)))

    npc.despawn()
}

fun input(vararg values: String, onlyHostMode: Boolean = false): InputNode {
    return InputNode(*values, onlyHostMode = onlyHostMode)
}

class InputNode(vararg val values: String, val onlyHostMode: Boolean = false) :
    ForgeEventNode<ServerChatEvent>(ServerChatEvent::class.java, { true }),
    ReadWriteProperty<Any?, String> {
    var message: String = "Nothing"
    override val action = { event: ServerChatEvent ->
        val isFromTeam =
            event.player in manager.team.onlineMembers || (onlyHostMode && event.player.uuid == manager.team.owner)
        if (isFromTeam) message = event.message.string
        !(isFromTeam && (event.message.string in values || values.isEmpty()))
    }

    override fun serializeNBT(): CompoundTag {
        return super.serializeNBT().apply {
            putString("message", message)
        }
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        super.deserializeNBT(nbt)
        message = nbt.getString("message")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return message
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        message = value
    }
}

open class ForgeEventNode<T : Event>(private val type: Class<T>, open val action: (T) -> Boolean) : Node() {
    private var isStarted = false
    private var isEnded = false

    @SubscribeEvent
    fun onEvent(event: T) {
        val etype = event::class.java

        if (!etype.isAssignableFrom(type)) return

        if (action(event)) {
            isEnded = true
            MinecraftForge.EVENT_BUS.unregister(this)
        }
    }

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            MinecraftForge.EVENT_BUS.register(this)
        }
        return isEnded
    }

    override fun serializeNBT() = CompoundTag()
    override fun deserializeNBT(nbt: CompoundTag) = Unit
}

inline fun <reified T : Event> IContextBuilder.waitForgeEvent(noinline function: (T) -> Boolean) =
    +ForgeEventNode(T::class.java, function)
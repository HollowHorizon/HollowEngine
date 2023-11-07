package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.event.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.ForgeEventNode
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class InputNode(vararg val values: String, val onlyHostMode: Boolean = false) :
    ForgeEventNode<ServerChatEvent>(ServerChatEvent::class.java, { true }), ReadWriteProperty<Any?, () -> String> {
    var message: String = "Nothing"
    override val action = { event: ServerChatEvent ->
        val isFromTeam =
            event.player in manager.team.onlineMembers || (onlyHostMode && event.player.uuid == manager.team.owner)

        if (isFromTeam) message = event.message
        isFromTeam && (event.message in values || values.isEmpty())
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

    override fun getValue(thisRef: Any?, property: KProperty<*>): () -> String {
        return { message }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: () -> String) {
        message = value()
    }
}
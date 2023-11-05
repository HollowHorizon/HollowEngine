package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.ServerMouseClickedEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.ForgeEventNode

class ClickNode(var clickType: MouseButton) :
    ForgeEventNode<ServerMouseClickedEvent>(ServerMouseClickedEvent::class.java, { true }) {
    override val action = { event: ServerMouseClickedEvent ->
        manager.team.isMember(event.entity.uuid) && clickType == event.button
    }

    override fun serializeNBT(): CompoundTag {
        return super.serializeNBT().apply {
            putInt("clickType", clickType.ordinal)
        }
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        super.deserializeNBT(nbt)
        clickType = MouseButton.entries[nbt.getInt("clickType")]
    }
}
package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.events.ServerKeyPressedEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.ForgeEventNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.wait
import ru.hollowhorizon.hollowengine.common.util.Keybind

class KeybindNode(var keybind: () -> Keybind) : ForgeEventNode<ServerKeyPressedEvent>(
    ServerKeyPressedEvent::class.java,
    { it.keybind == keybind() }) {
    override fun serializeNBT(): CompoundTag {
        return super.serializeNBT().apply {
            putInt("keybind", keybind().ordinal)
        }
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        super.deserializeNBT(nbt)
        keybind = { Keybind.entries[nbt.getInt("keybind")] }
    }
}

infix fun IContextBuilder.keybind(keybind: () -> Keybind) = +KeybindNode(keybind)

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine

abstract class Node : INBTSerializable<CompoundTag> {
    lateinit var manager: StoryStateMachine
    var parent: Node? = null

    abstract fun tick(): Boolean
}


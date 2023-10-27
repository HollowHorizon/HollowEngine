package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayType
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScriptBaseV2
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.executors.ServerDialogueExecutor
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*

fun CompoundTag.serializeNodes(name: String, nodes: Collection<Node>) {
    put(name, ListTag().apply {
        addAll(nodes.map(INBTSerializable<CompoundTag>::serializeNBT))
    })
}

fun CompoundTag.deserializeNodes(name: String, nodes: Collection<Node>) {
    val list = getList(name, 10)
    nodes.forEachIndexed { i, node ->
        node.deserializeNBT(list.getCompound(i))
    }
}

class NodeContextBuilder(override val stateMachine: StoryStateMachine) : IContextBuilder {
    val tasks = ArrayList<Node>()

    override operator fun Node.unaryPlus() {
        this.manager = stateMachine
        tasks.add(this)
    }
}
package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.api.Team
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayType
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScriptBaseV2
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.executors.ServerDialogueExecutor
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.CombinedNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.NodeContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*
import ru.hollowhorizon.hollowengine.common.sendMessage

interface IContextBuilder {
    val stateMachine: StoryStateMachine
    operator fun Node.unaryPlus()

    infix fun NPCProperty.moveTo(pos: Vec3) = +NpcMoveToBlockNode(this, pos)
    infix fun NPCProperty.moveTo(target: () -> Entity) = +NpcMoveToEntityNode(this, target)
    infix fun NPCProperty.moveTo(target: Entity) = +NpcMoveToEntityNode(this) { target }
    infix fun NPCProperty.moveTo(target: Team) = +NpcMoveToEntityNode(this) {
        target.onlineMembers.minBy { it.distanceToSqr(this()) }
    }

    fun NPCProperty.moveTo(x: Int, y: Int, z: Int) = +NpcMoveToBlockNode(this, stateMachine.pos(x, y, z))
    fun NPCProperty.moveTo(x: Double, y: Double, z: Double) = +NpcMoveToBlockNode(this, stateMachine.pos(x, y, z))


    fun NPCProperty.lookAt(target: Vec3, speed: Vec2) = +NpcLookToBlockNode(this, target, speed)
    infix fun NPCProperty.lookAt(target: Vec3) = +NpcLookToBlockNode(this, target)
    fun NPCProperty.lookAt(target: () -> Entity, speed: Vec2) = +NpcLookToEntityNode(this, target, speed)
    infix fun NPCProperty.lookAt(target: () -> Entity) = +NpcLookToEntityNode(this, target)
    fun NPCProperty.lookAt(target: Entity, speed: Vec2) = +NpcLookToEntityNode(this, { target }, speed)
    infix fun NPCProperty.lookAt(target: Entity) = +NpcLookToEntityNode(this, { target })
    fun NPCProperty.lookAt(target: Team, speed: Vec2) = +NpcLookToEntityNode(this, {
        target.onlineMembers.minBy { it.distanceToSqr(this()) }
    }, speed)

    infix fun NPCProperty.lookAt(target: Team) = +NpcLookToEntityNode(this, {
        target.onlineMembers.minByOrNull { it.distanceToSqr(this()) }
    })


    fun NPCProperty.play(
        animation: String,
        priority: Float = 1.0f,
        playType: PlayType = ru.hollowhorizon.hc.client.models.gltf.animations.PlayType.ONCE,
        speed: Float = 1.0f
    ) = +SimpleNode {
        this@play().play(animation, priority, playType, speed)
    }

    infix fun NPCProperty.play(animation: String) = +SimpleNode {
        this@play().play(animation)
    }

    infix fun NPCProperty.stop(animation: String) = +SimpleNode {
        this@stop().stop(animation)
    }


    infix fun NPCProperty.say(text: Component) = +SimpleNode {
        val component =
            TextComponent("§6[§7" + this@say().characterName + "§6]§7 ").append(text)
        stateMachine.team.onlineMembers.forEach { it.sendMessage(component) }
    }

    infix fun NPCProperty.say(text: String) = say(TextComponent(text))

    fun NPCProperty.despawn(text: String) = +SimpleNode { this@despawn().remove(Entity.RemovalReason.DISCARDED) }

    fun NPCProperty.dropItem(stack: ItemStack) = +SimpleNode {
        val entity = this@dropItem()
        val p = entity.position()
        val entityStack = ItemEntity(entity.level, p.x, p.y + entity.eyeHeight, p.z, stack)
        entityStack.setDeltaMovement(entity.lookAngle.x / 3, entity.lookAngle.y / 3, entity.lookAngle.z / 3)
        entity.level.addFreshEntity(entityStack)
    }

    fun NPCProperty.equip(slot: EquipmentSlot, item: ItemStack) = +SimpleNode {
        this@equip().setItemSlot(slot, item)
    }

    fun async(vararg tasks: IContextBuilder.() -> Unit) = +CombinedNode(
        tasks.flatMap { NodeContextBuilder(this.stateMachine).apply(it).tasks }
    )

    infix fun playSound(sound: String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            it.connection.send(
                ClientboundCustomSoundPacket(
                    sound.rl,
                    SoundSource.MASTER,
                    it.position(),
                    1.0f,
                    1.0f
                )
            )
        }
    }

    fun openDialogue(script: DialogueScriptBaseV2.() -> Unit) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach { player ->
            Thread {
                val executor = ServerDialogueExecutor(player)

                script(DialogueScriptBaseV2(executor))

                executor.stop()
            }.start()
        }
    }

    fun Team.tp(dim: String, pos: Vec3, view: Vec2? = null) = +SimpleNode {
        val dimension =
            manager.server.getLevel(manager.server.levelKeys().find { it.location() == dim.rl } ?: return@SimpleNode)
                ?: return@SimpleNode
        this@tp.onlineMembers.forEach {
            if (view == null) {
                it.teleportTo(dimension, pos.x, pos.y, pos.z, it.yHeadRot, it.xRot)
            } else {
                it.teleportTo(dimension, pos.x, pos.y, pos.z, view.x, view.y)
            }
        }
    }

    infix fun Team.tp(pos: Vec3) = +SimpleNode {
        this@tp.onlineMembers.forEach {
            it.teleportTo(it.getLevel(), pos.x, pos.y, pos.z, it.yHeadRot, it.xRot)
        }
    }
}
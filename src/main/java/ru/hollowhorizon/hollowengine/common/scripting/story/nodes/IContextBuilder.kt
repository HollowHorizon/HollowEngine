package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.api.Team
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayType
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.packets.StartAnimationContainer
import ru.hollowhorizon.hc.common.network.packets.StartOnceAnimationPacket
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenContainer
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.CombinedNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.NodeContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*

interface IContextBuilder {
    val stateMachine: StoryStateMachine
    operator fun <T : Node> T.unaryPlus(): T

    fun NPCEntity.Companion.creating(settings: NPCSettings, location: SpawnLocation): NpcDelegate {
        return NpcDelegate(settings, location).apply { manager = stateMachine }
    }

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


    infix fun NPCProperty.setTarget(value: (() -> LivingEntity?)?) = +SimpleNode {
        this@setTarget().target = value?.invoke()
    }

    infix fun NPCProperty.setTarget(value: Team) = setTarget {
        stateMachine.team.onlineMembers.minByOrNull { it.distanceToSqr(this()) }
    }

    infix fun NPCProperty.giveLeftHand(item: ItemStack?) = +SimpleNode {
        this@giveLeftHand().setItemInHand(InteractionHand.OFF_HAND, item ?: ItemStack.EMPTY)
    }

    infix fun NPCProperty.giveRightHand(item: ItemStack?) = +SimpleNode {
        this@giveRightHand().setItemInHand(InteractionHand.MAIN_HAND, item ?: ItemStack.EMPTY)
    }

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

    infix fun NPCProperty.playOnce(animation: String) = +SimpleNode {
        val npc = this@playOnce()
        StartOnceAnimationPacket().send(
            StartAnimationContainer(npc.id, animation, 10.0f, 1.0f),
            PacketDistributor.TRACKING_ENTITY.with(this@playOnce)
        )
    }

    fun NPCProperty.playOnce(animation: String, priority: Float = 10f, speed: Float = 1.0f) = +SimpleNode {
        val npc = this@playOnce()
        StartOnceAnimationPacket().send(
            StartAnimationContainer(npc.id, animation, priority, speed),
            PacketDistributor.TRACKING_ENTITY.with(this@playOnce)
        )
    }

    infix fun NPCProperty.stop(animation: String) = +SimpleNode {
        this@stop().stop(animation)
    }


    infix fun NPCProperty.say(text: Component) = +SimpleNode {
        val component =
            TextComponent("§6[§7" + this@say().characterName + "§6]§7 ").append(text)
        stateMachine.team.onlineMembers.forEach { it.sendMessage(component, it.uuid) }
    }

    infix fun NPCProperty.say(text: String) = say(TextComponent(text))

    fun Team.println(text: Component) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach { it.sendMessage(text, it.uuid) }
    }

    fun println(text: String) = println(TextComponent(text))

    fun NPCProperty.despawn() = +SimpleNode { this@despawn().remove(Entity.RemovalReason.DISCARDED) }

    infix fun NPCProperty.dropItem(stack: ItemStack) = +SimpleNode {
        val entity = this@dropItem()
        val p = entity.position()
        val entityStack = ItemEntity(entity.level, p.x, p.y + entity.eyeHeight, p.z, stack)
        entityStack.setDeltaMovement(entity.lookAngle.x, entity.lookAngle.y, entity.lookAngle.z)
        entity.level.addFreshEntity(entityStack)
    }

    fun NPCProperty.equip(slot: EquipmentSlot, item: ItemStack) = +SimpleNode {
        this@equip().setItemSlot(slot, item)
    }

    fun fadeIn(text: String, subTitle: String, time: Int) = +SimpleNode {
        OverlayScreenPacket().send(
            OverlayScreenContainer(true, text, subTitle, time),
            *stateMachine.team.onlineMembers.toTypedArray()
        )
    }

    fun fadeOut(text: String, subTitle: String, time: Int) = +SimpleNode {
        OverlayScreenPacket().send(
            OverlayScreenContainer(false, text, subTitle, time),
            *stateMachine.team.onlineMembers.toTypedArray()
        )
    }

    fun async(vararg tasks: NodeContextBuilder.() -> Unit) = +CombinedNode(
        tasks.flatMap { NodeContextBuilder(this.stateMachine).apply(it).tasks }
    )

    fun playSound(sound: String, volume: Float = 1.0f, pitch: Float = 1.0f) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            it.connection.send(
                ClientboundCustomSoundPacket(
                    sound.rl,
                    SoundSource.MASTER,
                    it.position(),
                    volume,
                    pitch
                )
            )
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

    fun pos(x: Double, y: Double, z: Double) = Vec3(x, y, z)
    fun pos(x: Int, y: Int, z: Int) = Vec3(x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5)
    fun vec(x: Int, y: Int) = Vec2(x.toFloat(), y.toFloat())
    fun vec(x: Float, y: Float) = Vec2(x, y)
    val Int.sec get() = this * 20
    val Int.min get() = this * 1200
    val Int.hours get() = this * 72000
}
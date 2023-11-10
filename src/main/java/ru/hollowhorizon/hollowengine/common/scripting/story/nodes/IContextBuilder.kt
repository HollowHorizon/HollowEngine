package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayType
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.packets.StartAnimationContainer
import ru.hollowhorizon.hc.common.network.packets.StartOnceAnimationPacket
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenContainer
import ru.hollowhorizon.hollowengine.client.screen.FadeOverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.scripting.item
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*
import java.io.File

interface IContextBuilder {
    val stateMachine: StoryStateMachine
    operator fun <T : Node> T.unaryPlus(): T

    fun next(block: SimpleNode.() -> Unit) = +SimpleNode(block)

    fun StoryStateMachine.init() {
        val npc1 by NPCEntity.creating {
            settings = NPCSettings()
            location = SpawnLocation(pos = pos(1, 2, 3))
        }

        val npc2 by NPCEntity.creating {
            settings = NPCSettings()
            location = SpawnLocation(pos = pos(1, 2, 3))
        }
        npc1 dropItem { item("minecraft:apple") }
    }

    class NpcContainer {
        var settings = NPCSettings()
        var location = SpawnLocation(pos = BlockPos(0, 0, 0))
    }

    fun NPCEntity.Companion.creating(settings: NpcContainer.() -> Unit): NpcDelegate {
        val container = NpcContainer().apply(settings)
        return NpcDelegate(container.settings, container.location).apply { manager = stateMachine }
    }

    infix fun NPCProperty.moveToPos(pos: () -> Vec3) = +NpcMoveToBlockNode(this, pos)
    infix fun NPCProperty.moveToEntity(target: () -> Entity) = +NpcMoveToEntityNode(this, target)
    infix fun NPCProperty.moveToTeam(target: () -> Team) = +NpcMoveToTeamNode(this, target)

    infix fun NPCProperty.lookAtPos(target: () -> Vec3) = +NpcLookToBlockNode(this, target)
    infix fun NPCProperty.lookAtEntity(target: () -> Entity) = +NpcLookToEntityNode(this, target)

    infix fun NPCProperty.lookAtTeam(target: () -> Team) = +NpcLookToTeamNode(this, target)


    infix fun NPCProperty.setTarget(value: (() -> LivingEntity?)?) = +SimpleNode {
        this@setTarget().target = value?.invoke()
    }

    infix fun NPCProperty.setTargetTeam(value: () -> Team) = setTarget {
        return@setTarget value().onlineMembers.minByOrNull { it.distanceToSqr(this()) }
    }

    infix fun NPCProperty.giveLeftHand(item: () -> ItemStack?) = +SimpleNode {
        this@giveLeftHand().setItemInHand(InteractionHand.OFF_HAND, item() ?: ItemStack.EMPTY)
    }

    infix fun NPCProperty.giveRightHand(item: () -> ItemStack?) = +SimpleNode {
        this@giveRightHand().setItemInHand(InteractionHand.MAIN_HAND, item() ?: ItemStack.EMPTY)
    }

    class AnimationContainer {
        var animation = ""
        var priority = 1.0f
        var playType = PlayType.LOOPED
        var speed = 1.0f
    }

    fun NPCProperty.play(block: AnimationContainer.() -> Unit) = +SimpleNode {
        val container = AnimationContainer().apply(block)
        this@play().play(container.animation, container.priority, container.playType, container.speed)
    }

    infix fun NPCProperty.playLooped(animation: () -> String) = play {
        this.playType = PlayType.LOOPED
        this.animation = animation()
    }

    infix fun NPCProperty.playOnce(animation: () -> String) = +SimpleNode {
        val npc = this@playOnce()
        StartOnceAnimationPacket().send(
            StartAnimationContainer(npc.id, animation(), 10.0f, 1.0f),
            PacketDistributor.TRACKING_ENTITY.with(this@playOnce)
        )
    }

    fun NPCProperty.playOnce(block: AnimationContainer.() -> Unit) = +SimpleNode {
        val container = AnimationContainer().apply(block)
        val npc = this@playOnce()
        StartOnceAnimationPacket().send(
            StartAnimationContainer(npc.id, container.animation, container.priority, container.speed),
            PacketDistributor.TRACKING_ENTITY.with(this@playOnce)
        )
    }

    infix fun NPCProperty.stop(animation: () -> String) = +SimpleNode {
        this@stop().stop(animation())
    }


    infix fun NPCProperty.say(text: () -> String) = +SimpleNode {
        val component =
            Component.literal("§6[§7" + this@say().characterName + "§6]§7 ").append(text().mcTranslate)
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(component) }
    }

    infix fun NPCProperty.configure(body: AnimatedEntityCapability.() -> Unit) = +SimpleNode {
        this@configure()[AnimatedEntityCapability::class].apply(body)
    }

    infix fun Team.sendAsPlayer(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            val componente = Component.literal("§6[§7${it.displayName.string}§7]§7").append(text().mcTranslate)
            it.sendSystemMessage(componente)
        }
    }

    infix fun Team.send(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(text().mcTranslate) }
    }

    fun NPCProperty.despawn() = +SimpleNode { this@despawn().remove(Entity.RemovalReason.DISCARDED) }

    infix fun NPCProperty.dropItem(stack: () -> ItemStack) = +SimpleNode {
        val entity = this@dropItem()
        val p = entity.position()
        val entityStack = ItemEntity(entity.level, p.x, p.y + entity.eyeHeight, p.z, stack())
        entityStack.setDeltaMovement(entity.lookAngle.x, entity.lookAngle.y, entity.lookAngle.z)
        entity.level.addFreshEntity(entityStack)
    }

    class FadeContainer {
        var text = ""
        var subtitle = ""
        var time = 0
    }

    fun fadeIn(block: FadeContainer.() -> Unit) = +WaitNode {
        val container = FadeContainer().apply(block)
        FadeOverlayScreenPacket().send(
            OverlayScreenContainer(true, container.text, container.subtitle, container.time),
            *stateMachine.team.onlineMembers.toTypedArray()
        )
        container.time
    }

    fun fadeOut(block: FadeContainer.() -> Unit) = +WaitNode {
        val container = FadeContainer().apply(block)
        FadeOverlayScreenPacket().send(
            OverlayScreenContainer(false, container.text, container.subtitle, container.time),
            *stateMachine.team.onlineMembers.toTypedArray()
        )
        container.time
    }

    fun async(vararg tasks: NodeContextBuilder.() -> Unit) = +CombinedNode(
        tasks.flatMap { NodeContextBuilder(this.stateMachine).apply(it).tasks }
    )

    class SoundContainer {
        var sound = ""
        var volume = 1.0f
        var pitch = 1.0f
    }

    fun playSound(sound: SoundContainer.() -> Unit) = +SimpleNode {
        val container = SoundContainer().apply(sound)
        stateMachine.team.onlineMembers.forEach {
            it.connection.send(
                ClientboundCustomSoundPacket(
                    container.sound.rl,
                    SoundSource.MASTER,
                    it.position(),
                    container.volume,
                    container.pitch,
                    it.random.nextLong()
                )
            )
        }
    }

    class SimpleTeleport {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var cameraY = 0F
        var cameraX = 0F
    }

    infix fun Team.tp(pos: () -> Vec3) = +SimpleNode {
        val p = pos()
        this@tp.onlineMembers.forEach {
            it.teleportTo(it.getLevel(), p.x, p.y, p.z, it.yHeadRot, it.xRot)
        }
    }

    infix fun Team.sTp(pos: SimpleTeleport.() -> Unit) = +SimpleNode {
        val p = SimpleTeleport().apply(pos)
        this@sTp.onlineMembers.forEach {
            it.teleportTo(it.getLevel(), p.x, p.y, p.z, p.cameraY, p.cameraX)
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

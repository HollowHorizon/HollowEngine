package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayType
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.packets.StartAnimationContainer
import ru.hollowhorizon.hc.common.network.packets.StartOnceAnimationPacket
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.FadeOverlayScreenPacket
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreenContainer
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.CombinedNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.NodeContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.WaitNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*

interface IContextBuilder {
    val stateMachine: StoryStateMachine
    operator fun <T : Node> T.unaryPlus(): T

    fun next(block: SimpleNode.() -> Unit) = +SimpleNode(block)

    class NpcContainer {
        var settings = NPCSettings()
        var location = SpawnLocation(pos = BlockPos(0, 0, 0))
    }

    fun NPCEntity.Companion.creating(settings: NpcContainer.() -> Unit): NpcDelegate {
        val container = NpcContainer().apply(settings)
        return +NpcDelegate(container.settings, container.location).apply { manager = stateMachine }
    }

    infix fun NPCProperty.moveToPos(pos: () -> Vec3) = +NpcMoveToBlockNode(this, pos)
    infix fun NPCProperty.moveToEntity(target: () -> Entity) = +NpcMoveToEntityNode(this, target)
    infix fun NPCProperty.moveToTeam(target: () -> Team) = +NpcMoveToTeamNode(this, target)
    infix fun NPCProperty.moveToBiome(biomeName: () -> String) = +NpcMoveToBlockNode(this) {
        val npc = this@moveToBiome()
        val biome = biomeName().rl

        val pos = (npc.level as ServerLevel).findClosestBiome3d(
            { it.`is`(biome) },
            npc.blockPosition(),
            6400,
            32,
            64
        )?.first ?: npc.blockPosition()
        Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

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
        this@play()[AnimatedEntityCapability::class].layers.add(
            AnimationLayer(
                container.animation, container.priority, container.playType, container.speed, 0
            )
        )
    }

    infix fun NPCProperty.playLooped(animation: () -> String) = play {
        this.playType = PlayType.LOOPED
        this.animation = animation()
    }

    infix fun NPCProperty.playOnce(animation: () -> String) = +SimpleNode {
        val npc = this@playOnce()
        StartOnceAnimationPacket().send(
            StartAnimationContainer(npc.id, animation(), 100.0f, 1.0f),
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
        val anim = animation()
        this@stop()[AnimatedEntityCapability::class].layers.removeIf { it.animation == anim }
    }


    infix fun NPCProperty.say(text: () -> String) = +SimpleNode {
        val component = Component.literal("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
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
        entityStack.setDefaultPickUpDelay()
        val f8 = Mth.sin(entity.xRot * Mth.PI / 180f)
        val f3 = Mth.sin(entity.yHeadRot * Mth.PI / 180f)
        val f4 = Mth.cos(entity.yHeadRot * Mth.PI / 180f)
        entityStack.setDeltaMovement(
            -f3 * 0.3, -f8 * 0.3 + 0.1, f4 * 0.3
        )
        entity.level.addFreshEntity(entityStack)
    }

    class GiveItemList {
        val items = mutableListOf<ItemStack>()

        operator fun ItemStack.unaryPlus() {
            items.add(this)
        }
    }

    infix fun NPCProperty.requestItems(block: GiveItemList.() -> Unit) =
        +NpcItemListNode(GiveItemList().apply(block).items, this@requestItems)

    fun NPCProperty.waitInteract() = +NpcInteractNode(this@waitInteract)

    class FadeContainer {
        var text = ""
        var subtitle = ""
        var texture = ""
        var color = 0xFFFFFF
        var time = 0
    }

    fun fadeIn(block: FadeContainer.() -> Unit) = +WaitNode {
        val container = FadeContainer().apply(block)
        FadeOverlayScreenPacket().send(
            OverlayScreenContainer(
                true,
                container.text,
                container.subtitle,
                container.color,
                container.texture,
                container.time
            ), *stateMachine.team.onlineMembers.toTypedArray()
        )
        container.time
    }

    fun fadeOut(block: FadeContainer.() -> Unit) = +WaitNode {
        val container = FadeContainer().apply(block)
        FadeOverlayScreenPacket().send(
            OverlayScreenContainer(
                false,
                container.text,
                container.subtitle,
                container.color,
                container.texture,
                container.time
            ), *stateMachine.team.onlineMembers.toTypedArray()
        )
        container.time
    }

    fun async(vararg tasks: NodeContextBuilder.() -> Unit) =
        +CombinedNode(tasks.flatMap { NodeContextBuilder(this.stateMachine).apply(it).tasks })

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

    fun stopSound(sound: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            it.connection.send(
                ClientboundStopSoundPacket(
                    sound().rl,
                    SoundSource.MASTER
                )
            )
        }
    }

    infix fun Team.tp(pos: () -> Vec3) = +SimpleNode {
        val p = pos()
        this@tp.onlineMembers.forEach {
            it.teleportTo(it.getLevel(), p.x, p.y, p.z, it.yHeadRot, it.xRot)
        }
    }

    fun pos(x: Double, y: Double, z: Double) = Vec3(x, y, z)
    fun pos(x: Int, y: Int, z: Int) = Vec3(x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5)
    fun vec(x: Int, y: Int) = Vec2(x.toFloat(), y.toFloat())
    fun vec(x: Float, y: Float) = Vec2(x, y)

    operator fun Team.get(name: String): Player? = this.onlineMembers.find { it.gameProfile.name == name }

    val Int.sec get() = this * 20
    val Int.min get() = this * 1200
    val Int.hours get() = this * 72000
}

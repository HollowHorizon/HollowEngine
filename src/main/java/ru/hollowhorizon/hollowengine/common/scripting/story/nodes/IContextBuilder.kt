@file:Suppress("INAPPLICABLE_JVM_NAME")

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.ITeleporter
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.SubModel
import ru.hollowhorizon.hc.client.screens.CloseGuiPacket
import ru.hollowhorizon.hc.client.utils.capability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hc.common.ui.Widget
import ru.hollowhorizon.hollowengine.common.capabilities.AimMark
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.scripting.story.ProgressManager
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.NPCProperty
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.NpcDelegate
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.distanceToXZ
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.players.PlayerProperty
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.AnimationContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.NpcContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.TeamHelper
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.TeleportContainer
import java.util.function.Function
import kotlin.math.sqrt

interface IContextBuilder {
    val stateMachine: StoryStateMachine

    /**
     * Функция по добавлению новых задач (чтобы при добавлении задач в цикле они добавлялись именно в цикл, а не основную машину состояний)
     */
    operator fun <T : Node> T.unaryPlus(): T


    // ------------------------------------
    //          Функции персонажей
    // ------------------------------------

    fun NPCEntity.Companion.creating(settings: NpcContainer.() -> Unit) =
        +NpcDelegate(this@IContextBuilder) { NpcContainer().apply(settings) }.apply { manager = stateMachine }


    fun Widget.close() {
        stateMachine.team.onlineMembers.forEach { CloseGuiPacket().send(PacketDistributor.PLAYER.with { it }) }
    }

    fun NPCEntity.Companion.fromSubModel(subModel: NpcContainer.() -> SubModel) = +NpcDelegate(this@IContextBuilder) {
        NpcContainer().apply {
            val settings = subModel()
            model = settings.model
            textures.putAll(settings.textures)
            transform = settings.transform
            subModels.putAll(settings.subModels)
        }
    }.apply { manager = stateMachine }

    // ------------------------------------
    //          Функции квестов
    // ------------------------------------
    fun ProgressManager.addMessage(message: () -> String) = +SimpleNode {
        val list = this.manager.team.extraData.getList("hollowengine_progress_tasks", 8)
        list += StringTag.valueOf(message())
        this.manager.team.extraData.put("hollowengine_progress_tasks", list)
        this.manager.team.save()
        this.manager.team.onlineMembers.forEach {
            FTBTeamsAPI.getManager().syncAllToPlayer(it, this.manager.team)
        }
    }

    fun ProgressManager.removeMessage(message: () -> String) = +SimpleNode {
        val list = this.manager.team.extraData.getList("hollowengine_progress_tasks", 8)
        list -= StringTag.valueOf(message())
        this.manager.team.extraData.put("hollowengine_progress_tasks", list)
        this.manager.team.save()
        this.manager.team.onlineMembers.forEach {
            FTBTeamsAPI.getManager().syncAllToPlayer(it, this.manager.team)
        }
    }

    fun ProgressManager.clear() = +SimpleNode {
        this.manager.team.extraData.put("hollowengine_progress_tasks", ListTag())
        this.manager.team.save()
        this.manager.team.onlineMembers.forEach {
            FTBTeamsAPI.getManager().syncAllToPlayer(it, this.manager.team)
        }
    }

    @JvmName("playerPlay")
    infix fun PlayerProperty.play(block: AnimationContainer.() -> Unit) = +SimpleNode {
        val container = AnimationContainer().apply(block)

        val serverLayers = this@play()[AnimatedEntityCapability::class].layers

        if (serverLayers.any { it.animation == container.animation }) return@SimpleNode

        StartAnimationPacket(
            this@play().id, container.animation, container.layerMode, container.playType, container.speed
        ).send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(this@play))

        if (container.playType != PlayMode.ONCE) {
            //Нужно на случай если клиентская сущность выйдет из зоны видимости (удалится)
            serverLayers.addNoUpdate(
                AnimationLayer(
                    container.animation, container.layerMode, container.playType, container.speed
                )
            )
        }
    }

    @JvmName("playerPlayLooped")
    infix fun PlayerProperty.playLooped(animation: () -> String) = play {
        this.playType = PlayMode.LOOPED
        this.animation = animation()
    }

    @JvmName("playerPlayOnce")
    infix fun PlayerProperty.playOnce(animation: () -> String) = play {
        this.playType = PlayMode.ONCE
        this.animation = animation()
    }

    @JvmName("playerPlayFreeze")
    infix fun PlayerProperty.playFreeze(animation: () -> String) = play {
        this.playType = PlayMode.LAST_FRAME
        this.animation = animation()
    }

    @JvmName("playerStop")
    infix fun PlayerProperty.stop(animation: () -> String) = +SimpleNode {
        val anim = animation()
        this@stop()[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == anim }
        StopAnimationPacket(this@stop().id, anim).send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(this@stop))
    }


    infix fun NPCProperty.say(text: () -> String) = +SimpleNode {
        val component = Component.literal("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(component) }
    }

    @JvmName("playerSay")
    infix fun PlayerProperty.say(text: () -> String) = +SimpleNode {
        val component = Component.literal("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(component) }
    }

    @JvmName("playerConfigure")
    infix fun PlayerProperty.configure(body: AnimatedEntityCapability.() -> Unit) = +SimpleNode {
        this@configure()[AnimatedEntityCapability::class].apply(body)
    }

    infix fun Team.sendAsPlayer(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            it.sendSystemMessage(Component.literal("§6[§7${it.displayName.string}§6]§7 ").append(text().mcTranslate))
        }
    }

    infix fun Team.send(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(text().mcTranslate) }
    }


    fun PlayerProperty.saveInventory() = next {
        val player = this@saveInventory()

        player.persistentData.put("he_inventory", ListTag().apply(player.inventory::save))
    }

    fun PlayerProperty.loadInventory() = next {
        val player = this@loadInventory()

        if (player.persistentData.contains("he_inventory")) {
            player.inventory.load(player.persistentData.getList("he_inventory", 10))
            player.inventory.setChanged()
        }
    }

    fun PlayerProperty.clearInventory() = next {
        val player = this@clearInventory()

        player.inventory.clearContent()
        player.inventory.setChanged()
    }


    infix fun Team.modify(inv: TeamHelper.() -> Unit) = +SimpleNode {
        TeamHelper(this@modify).apply(inv)
    }

    class PosWaiter {
        var pos = Vec3(0.0, 0.0, 0.0)
        var radius = 0.0
        var inverse = false
        var ignoreY = true
    }

    infix fun Team.waitPos(context: PosWaiter.() -> Unit) {
        next {
            val waiter = PosWaiter().apply(
                context
            )
            val pos = waiter.pos
            this@waitPos.capability(StoryTeamCapability::class).aimMarks += AimMark(
                pos.x, pos.y, pos.z, NpcIcon.QUESTION.image, waiter.ignoreY
            )
        }
        waitForgeEvent<ServerTickEvent> {
            var result = false
            val waiter = PosWaiter().apply(context)

            this@waitPos.onlineMembers.forEach {
                val distance: (Vec3) -> Double =
                    if (!waiter.ignoreY) { pos: Vec3 -> sqrt(it.distanceToSqr(pos)) } else it::distanceToXZ
                val compare: (Double) -> Boolean =
                    if (!waiter.inverse) { len: Double -> len <= waiter.radius }
                    else { len: Double -> len >= waiter.radius }

                result = result || compare(distance(waiter.pos))
            }

            if (result) {
                this@waitPos.capability(StoryTeamCapability::class).aimMarks.removeIf { it.x == waiter.pos.x && it.y == waiter.pos.y && it.z == waiter.pos.z }
            }

            result
        }
    }

    fun async(body: NodeContextBuilder.() -> Unit): AsyncProperty {
        val chainNode = ChainNode(NodeContextBuilder(stateMachine).apply(body).tasks)
        val index = stateMachine.asyncNodes.size
        stateMachine.asyncNodes.add(chainNode)
        +SimpleNode { stateMachine.onTickTasks += { stateMachine.asyncNodeIds.add(index) } }
        return AsyncProperty(index)
    }

    fun AsyncProperty.stop() = +SimpleNode {
        stateMachine.onTickTasks += {
            stateMachine.asyncNodeIds.remove(this@stop.index)
        }
    }

    fun AsyncProperty.resume() = +SimpleNode {
        stateMachine.onTickTasks += {
            stateMachine.asyncNodeIds.add(this@resume.index)
        }
    }


    infix fun Team.tpPos(pos: () -> Vec3) = +SimpleNode {
        val p = pos()
        this@tpPos.onlineMembers.forEach {
            it.teleportTo(it.getLevel(), p.x, p.y, p.z, it.yHeadRot, it.xRot)
        }
    }


    infix fun Team.tpTo(teleport: TeleportContainer.() -> Unit) = +SimpleNode {
        val config = TeleportContainer().apply(teleport)
        val position = config.pos
        val camera = config.vec
        val dimension = manager.server.levelKeys().find { it.location() == config.world.rl }
            ?: throw IllegalStateException("Dimension ${config.world} not found!")

        this@tpTo.onlineMembers.forEach {
            it.changeDimension(it.server.getLevel(dimension)!!, object : ITeleporter {
                override fun placeEntity(
                    entity: Entity,
                    currentWorld: ServerLevel,
                    destWorld: ServerLevel,
                    yaw: Float,
                    repositionEntity: Function<Boolean, Entity>
                ): Entity {
                    val teleportedEntity = repositionEntity.apply(false) as ServerPlayer

                    teleportedEntity.teleportTo(destWorld, position.x, position.y, position.z, camera.x, camera.y)

                    return teleportedEntity
                }

                override fun playTeleportSound(player: ServerPlayer, sourceWorld: ServerLevel, destWorld: ServerLevel) =
                    false
            })
        }
    }

    fun pos(x: Number, y: Number, z: Number) = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    fun vec(x: Number, y: Number) = Vec2(x.toFloat(), y.toFloat())

    val Int.sec get() = this * 20
    val Int.min get() = this * 1200
    val Int.hours get() = this * 72000
}

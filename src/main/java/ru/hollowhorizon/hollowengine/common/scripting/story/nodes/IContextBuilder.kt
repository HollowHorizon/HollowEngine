@file:Suppress("INAPPLICABLE_JVM_NAME")

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.core.BlockPos
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.ITeleporter
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.RawPose
import ru.hollowhorizon.hc.client.models.gltf.manager.SubModel
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hollowengine.client.render.effects.ParticleEffect
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.scripting.story.ProgressManager
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.players.PlayerProperty
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.AnimationContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.NpcContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.TeamHelper
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.TeleportContainer
import ru.hollowhorizon.hollowengine.common.util.getStructure
import ru.hollowhorizon.hollowengine.cutscenes.replay.Replay
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer
import java.util.function.Function

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
        +NpcDelegate { NpcContainer().apply(settings) }.apply { manager = stateMachine }


    fun NPCEntity.Companion.fromSubModel(subModel: NpcContainer.() -> SubModel) = +NpcDelegate {
        NpcContainer().apply {
            val settings = subModel()
            model = settings.model
            textures.putAll(settings.textures)
            transform = settings.transform
            subModels.putAll(settings.subModels)
        }
    }.apply { manager = stateMachine }

    infix fun NPCProperty.replay(file: () -> String) = next {
        val replay = Replay.fromFile(DirectoryManager.HOLLOW_ENGINE.resolve("replays").resolve(file()))
        ReplayPlayer(this@replay()).apply {
            saveEntity = true
            isLooped = false
            play(this@replay().level, replay)
        }
    }

    infix fun NPCProperty.setPose(fileName: () -> String?) = next {
        val file = fileName()
        if (file == null) {
            this@setPose()[AnimatedEntityCapability::class].pose = RawPose()
            return@next
        }
        val replay = RawPose.fromNBT(
            DirectoryManager.HOLLOW_ENGINE.resolve("npcs/poses/").resolve(file).inputStream().loadAsNBT()
        )
        this@setPose()[AnimatedEntityCapability::class].pose = replay
    }

    var NPCProperty.hitboxMode
        get(): HitboxMode = this()[NPCCapability::class].hitboxMode
        set(value) {
            next {
                this@hitboxMode()[NPCCapability::class].hitboxMode = value
            }
        }

    var NPCProperty.icon
        get(): NpcIcon = this()[NPCCapability::class].icon
        set(value) {
            next {
                this@icon()[NPCCapability::class].icon = value
            }
        }

    var NPCProperty.invulnerable
        get() = this().isInvulnerable
        set(value) {
            next {
                this@invulnerable().isInvulnerable = value
            }
        }

    infix fun NPCProperty.setMovingPos(pos: () -> Vec3?) = +SimpleNode {
        val position = pos()
        this@setMovingPos().npcTarget.movingPos = position
    }

    infix fun NPCProperty.setMovingEntity(entity: () -> Entity?) = +SimpleNode {
        val target = entity()
        this@setMovingEntity().npcTarget.movingEntity = target
    }

    infix fun NPCProperty.setMovingTeam(team: () -> Team?) = +SimpleNode {
        val target = team()
        this@setMovingTeam().npcTarget.movingTeam = target
    }

    infix fun NPCProperty.setLookingPos(pos: () -> Vec3?) = +SimpleNode {
        val position = pos()
        this@setLookingPos().npcTarget.lookingPos = position
    }

    infix fun NPCProperty.setLookingEntity(entity: () -> Entity?) = +SimpleNode {
        val target = entity()
        this@setLookingEntity().npcTarget.lookingEntity = target
    }

    infix fun NPCProperty.setLookingTeam(team: () -> Team?) = +SimpleNode {
        val target = team()
        this@setLookingTeam().npcTarget.lookingTeam = target
    }

    infix fun NPCProperty.moveToPos(pos: () -> Vec3) = +NpcMoveToBlockNode(this, pos)
    infix fun NPCProperty.moveToEntity(target: () -> Entity) = +NpcMoveToEntityNode(this, target)
    infix fun NPCProperty.moveToTeam(target: () -> Team) = +NpcMoveToTeamNode(this, target)
    infix fun NPCProperty.moveToBiome(biomeName: () -> String) = +NpcMoveToBlockNode(this) {
        val npc = this@moveToBiome()
        val biome = biomeName().rl

        val pos = (npc.level as ServerLevel).findNearestBiome(
            { it.`is`(biome) },
            npc.blockPosition(),
            6400,
            32
        )?.first ?: npc.blockPosition()
        Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    fun NPCProperty.moveToStructure(structureName: () -> String, offset: () -> BlockPos = { BlockPos.ZERO }) =
        +NpcMoveToBlockNode(this) {
            val npc = this@moveToStructure()
            val level = npc.level as ServerLevel
            val structure = level.getStructure(structureName(), npc.blockPosition()).pos
            val offsetPos = offset()

            Vec3(
                structure.x.toDouble() + offsetPos.x,
                structure.y.toDouble() + offsetPos.y,
                structure.z.toDouble() + offsetPos.z
            )
        }

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

    infix fun NPCProperty.lookAtPos(target: () -> Vec3) = +NpcLookToBlockNode(this, target)

    fun NPCProperty.lookAtEntityType(entity: () -> String) {
        val entityType = ForgeRegistries.ENTITY_TYPES.getValue(entity().rl)!!

        lookAtEntity {
            val npc = this()
            val level = npc.level

            level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(npc.position(), 25.0, 25.0, 25.0)) {
                it.type == entityType
            }.minByOrNull { it.distanceTo(npc) } ?: npc
        }
    }

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

    infix fun NPCProperty.play(block: AnimationContainer.() -> Unit) = +SimpleNode {
        val container = AnimationContainer().apply(block)

        val serverLayers = this@play()[AnimatedEntityCapability::class].layers

        if (serverLayers.any { it.animation == container.animation }) return@SimpleNode

        StartAnimationPacket(
            this@play().id,
            container.animation,
            container.layerMode,
            container.playType,
            container.speed
        ).send(PacketDistributor.TRACKING_ENTITY.with(this@play))

        if (container.playType != PlayMode.ONCE) {
            //Нужно на случай если клиентская сущность выйдет из зоны видимости (удалится)
            serverLayers.addNoUpdate(
                AnimationLayer(
                    container.animation,
                    container.layerMode,
                    container.playType,
                    container.speed
                )
            )
        }
    }

    @JvmName("playerPlay")
    infix fun PlayerProperty.play(block: AnimationContainer.() -> Unit) = +SimpleNode {
        val container = AnimationContainer().apply(block)

        val serverLayers = this@play()[AnimatedEntityCapability::class].layers

        if (serverLayers.any { it.animation == container.animation }) return@SimpleNode

        StartAnimationPacket(
            this@play().id,
            container.animation,
            container.layerMode,
            container.playType,
            container.speed
        ).send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(this@play))

        if (container.playType != PlayMode.ONCE) {
            //Нужно на случай если клиентская сущность выйдет из зоны видимости (удалится)
            serverLayers.addNoUpdate(
                AnimationLayer(
                    container.animation,
                    container.layerMode,
                    container.playType,
                    container.speed
                )
            )
        }
    }

    infix fun NPCProperty.playLooped(animation: () -> String) = play {
        this.playType = PlayMode.LOOPED
        this.animation = animation()
    }

    @JvmName("playerPlayLooped")
    infix fun PlayerProperty.playLooped(animation: () -> String) = play {
        this.playType = PlayMode.LOOPED
        this.animation = animation()
    }

    infix fun NPCProperty.playOnce(animation: () -> String) = play {
        this.playType = PlayMode.ONCE
        this.animation = animation()
    }

    @JvmName("playerPlayOnce")
    infix fun PlayerProperty.playOnce(animation: () -> String) = play {
        this.playType = PlayMode.ONCE
        this.animation = animation()
    }

    infix fun NPCProperty.playFreeze(animation: () -> String) = play {
        this.playType = PlayMode.LAST_FRAME
        this.animation = animation()
    }

    @JvmName("playerPlayFreeze")
    infix fun PlayerProperty.playFreeze(animation: () -> String) = play {
        this.playType = PlayMode.LAST_FRAME
        this.animation = animation()
    }

    infix fun NPCProperty.stop(animation: () -> String) = +SimpleNode {
        val anim = animation()
        this@stop()[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == anim }
        StopAnimationPacket(this@stop().id, anim).send(PacketDistributor.TRACKING_ENTITY.with(this@stop))
    }

    @JvmName("playerStop")
    infix fun PlayerProperty.stop(animation: () -> String) = +SimpleNode {
        val anim = animation()
        this@stop()[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == anim }
        StopAnimationPacket(this@stop().id, anim).send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(this@stop))
    }


    infix fun NPCProperty.say(text: () -> String) = +SimpleNode {
        val component = TextComponent("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
        stateMachine.team.onlineMembers.forEach { it.sendMessage(component, it.uuid) }
    }

    @JvmName("playerSay")
    infix fun PlayerProperty.say(text: () -> String) = +SimpleNode {
        val component = TextComponent("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
        stateMachine.team.onlineMembers.forEach { it.sendMessage(component, it.uuid) }
    }

    infix fun NPCProperty.configure(body: AnimatedEntityCapability.() -> Unit) = +SimpleNode {
        this@configure()[AnimatedEntityCapability::class].apply(body)
    }

    @JvmName("playerConfigure")
    infix fun PlayerProperty.configure(body: AnimatedEntityCapability.() -> Unit) = +SimpleNode {
        this@configure()[AnimatedEntityCapability::class].apply(body)
    }

    infix fun NPCProperty.useBlock(target: () -> Vec3) {
        this.moveToPos(target)
        this.lookAtPos(target)
        +SimpleNode {
            val entity = this@useBlock()
            val pos = target()
            val hit = entity.level.clip(
                ClipContext(
                    pos,
                    pos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    entity
                )
            )
            entity.swing(InteractionHand.MAIN_HAND)
            val state = entity.level.getBlockState(hit.blockPos)
            state.use(entity.level, entity.fakePlayer, InteractionHand.MAIN_HAND, hit)
        }
    }

    infix fun NPCProperty.destroyBlock(target: () -> Vec3) {
        this.moveToPos(target)
        this.lookAtPos(target)
        +SimpleNode {
            val entity = this@destroyBlock()
            val manager = entity.fakePlayer.gameMode

            manager.destroyBlock(BlockPos(target()))
            entity.swing(InteractionHand.MAIN_HAND)
        }
    }

    infix fun NPCProperty.addTrade(offer: () -> MerchantOffer) = +SimpleNode {
        this@addTrade().npcTrader.npcOffers.add(offer())
    }

    fun NPCProperty.clearTrades() = +SimpleNode {
        this@clearTrades().npcTrader.npcOffers.clear()
    }

    fun NPCProperty.clearTradeUses() = +SimpleNode {
        this@clearTradeUses().npcTrader.npcOffers.forEach { it.resetUses() }
    }


    fun AnimatedEntityCapability.skin(name: String) = "skins/$name"

    infix fun Team.sendAsPlayer(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            it.sendMessage(TextComponent("§6[§7${it.displayName.string}§6]§7 ").append(text().mcTranslate), it.uuid)
        }
    }

    infix fun Team.send(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach { it.sendMessage(text().mcTranslate, it.uuid) }
    }


    fun NPCProperty.despawn() = +SimpleNode { this@despawn().remove(Entity.RemovalReason.DISCARDED) }

    infix fun NPCProperty.addEffect(effect: ParticleEffect.() -> Unit) = +SimpleNode {
        this@addEffect().addEffect(ParticleEffect("".rl, "").apply(effect))
    }

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

    infix fun NPCProperty.requestItems(block: GiveItemList.() -> Unit) = +NpcItemListNode(block, this@requestItems)

    fun NPCProperty.waitInteract() = +NpcInteractNode(this@waitInteract)

    class PosWaiter {
        var vec = Vec3(0.0, 0.0, 0.0)
        var radius = 0.0
        var inverse = false
    }

    infix fun Team.waitPos(context: PosWaiter.() -> Unit) = waitForgeEvent<ServerTickEvent> {
        var result = false
        val waiter = PosWaiter().apply(context)

        this@waitPos.onlineMembers.forEach {
            val distance = it.distanceToSqr(waiter.vec)
            if (!waiter.inverse) {
                if (distance <= waiter.radius * waiter.radius) result = true
            } else {
                if (distance >= waiter.radius * waiter.radius) result = true
            }
        }

        result
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

    infix fun NPCProperty.tpTo(target: TeleportContainer.() -> Unit) = +SimpleNode {
        val tp = TeleportContainer().apply(target)
        val teleport = tp.pos
        this@tpTo.invoke().teleportTo(teleport.x, teleport.y, teleport.z)
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

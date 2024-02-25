@file:Suppress("INAPPLICABLE_JVM_NAME")

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.core.BlockPos
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
import ru.hollowhorizon.hc.client.screens.CloseGuiPacket
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hc.common.ui.Widget
import ru.hollowhorizon.hollowengine.common.capabilities.AimMark
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
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
import kotlin.math.sqrt

abstract class IContextBuilder {
    abstract val stateMachine: StoryStateMachine

    /**
     * Функция по добавлению новых задач (чтобы при добавлении задач в цикле они добавлялись именно в цикл, а не основную машину состояний)
     */
    abstract operator fun <T : Node> T.unaryPlus(): T


    // ------------------------------------
    //          Функции персонажей
    // ------------------------------------

    fun NPCEntity.Companion.creating(settings: NpcContainer.() -> Unit) =
        +NpcDelegate { NpcContainer().apply(settings) }.apply { manager = stateMachine }


    fun Widget.close() {
        stateMachine.team.onlineMembers.forEach { CloseGuiPacket().send(PacketDistributor.PLAYER.with { it }) }
    }

    fun NPCEntity.Companion.fromSubModel(subModel: NpcContainer.() -> SubModel) = +NpcDelegate {
        NpcContainer().apply {
            val settings = subModel()
            model = settings.model
            textures.putAll(settings.textures)
            transform = settings.transform
            subModels.putAll(settings.subModels)
        }
    }.apply { manager = stateMachine }

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

    var NPCProperty.name: String
        get() = this().displayName.string
        set(value) {
            next {
                this@name().customName = value.mcTranslate
            }
        }

    infix fun NPCProperty.giveLeftHand(item: () -> ItemStack?) {
        next {
            this@giveLeftHand().setItemInHand(InteractionHand.OFF_HAND, item() ?: ItemStack.EMPTY)
        }
    }

    infix fun NPCProperty.giveRightHand(item: () -> ItemStack?) {
        next {
            this@giveRightHand().setItemInHand(InteractionHand.MAIN_HAND, item() ?: ItemStack.EMPTY)
        }
    }

    infix fun NPCProperty.configure(body: AnimatedEntityCapability.() -> Unit) {
        next {
            this@configure()[AnimatedEntityCapability::class].apply(body)
        }
    }

    fun NPCProperty.despawn() = next { this@despawn().remove(Entity.RemovalReason.DISCARDED) }

    @Suppress("UNCHECKED_CAST")
    inline infix fun <reified T> NPCProperty.moveTo(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> +NpcMoveToBlockNode(this@moveTo, target as NpcTarget<Vec3>)
            Entity::class.java.isAssignableFrom(type) -> +NpcMoveToEntityNode(this@moveTo, target as NpcTarget<Entity>)
            Team::class.java.isAssignableFrom(type) -> +NpcMoveToTeamNode(this@moveTo, target as NpcTarget<Team>)
            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    infix fun NPCProperty.moveToBiome(biomeName: () -> String) {
        +NpcMoveToBlockNode(this@moveToBiome) {
            val npc = this@moveToBiome()
            val biome = biomeName().rl

            val pos = (npc.level as ServerLevel).findClosestBiome3d(
                { it.`is`(biome) }, npc.blockPosition(), 6400, 32, 64
            )?.first ?: npc.blockPosition()
            Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        }
    }

    fun NPCProperty.moveToStructure(structureName: () -> String, offset: () -> BlockPos = { BlockPos.ZERO }) {
        +NpcMoveToBlockNode(this@moveToStructure) {
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
    }

    inline infix fun <reified T> NPCProperty.moveAlwaysTo(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> next {
                this@moveAlwaysTo().npcTarget.movingPos = target() as Vec3
                this@moveAlwaysTo().npcTarget.movingEntity = null
                this@moveAlwaysTo().npcTarget.movingTeam = null
            }

            Entity::class.java.isAssignableFrom(type) -> next {
                this@moveAlwaysTo().npcTarget.movingPos = null
                this@moveAlwaysTo().npcTarget.movingEntity = target() as Entity
                this@moveAlwaysTo().npcTarget.movingTeam = null
            }

            Team::class.java.isAssignableFrom(type) -> next {
                this@moveAlwaysTo().npcTarget.movingPos = null
                this@moveAlwaysTo().npcTarget.movingEntity = null
                this@moveAlwaysTo().npcTarget.movingTeam = target() as Team
            }

            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    fun NPCProperty.stopMoveAlways() = next {
        this@stopMoveAlways().npcTarget.movingPos = null
        this@stopMoveAlways().npcTarget.movingEntity = null
        this@stopMoveAlways().npcTarget.movingTeam = null
    }

    inline infix fun <reified T> NPCProperty.setTarget(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> throw UnsupportedOperationException("Can't attack a block!")
            LivingEntity::class.java.isAssignableFrom(type) -> next {
                this@setTarget().target = target() as LivingEntity
            }

            Team::class.java.isAssignableFrom(type) -> next {
                this@setTarget().target = (target() as Team).onlineMembers
                    .minByOrNull { it.distanceToSqr(this@setTarget()) }
            }

            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    fun NPCProperty.clearTarget() {
        next { this@clearTarget().target = null }
    }

    infix fun NPCProperty.setPose(fileName: () -> String?) {
        next {
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
    }

    infix fun NPCProperty.play(block: AnimationContainer.() -> Unit) {
        next {
            val container = AnimationContainer().apply(block)

            val serverLayers = this@play()[AnimatedEntityCapability::class].layers

            if (serverLayers.any { it.animation == container.animation }) return@next

            StartAnimationPacket(
                this@play().id, container.animation, container.layerMode, container.playType, container.speed
            ).send(PacketDistributor.TRACKING_ENTITY.with(this@play))

            if (container.playType != PlayMode.ONCE) {
                //Нужно на случай если клиентская сущность выйдет из зоны видимости (удалится)
                serverLayers.addNoUpdate(
                    AnimationLayer(
                        container.animation, container.layerMode, container.playType, container.speed
                    )
                )
            }
        }
    }


    infix fun NPCProperty.playLooped(animation: () -> String) = play {
        this.playType = PlayMode.LOOPED
        this.animation = animation()
    }

    infix fun NPCProperty.playOnce(animation: () -> String) = play {
        this.playType = PlayMode.ONCE
        this.animation = animation()
    }

    infix fun NPCProperty.playFreeze(animation: () -> String) = play {
        this.playType = PlayMode.LAST_FRAME
        this.animation = animation()
    }

    infix fun NPCProperty.stop(animation: () -> String) {
        next {
            val anim = animation()
            this@stop()[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == anim }
            StopAnimationPacket(this@stop().id, anim).send(PacketDistributor.TRACKING_ENTITY.with(this@stop))
        }
    }

    infix fun NPCProperty.useBlock(target: () -> Vec3) {
        this moveTo target
        this lookAt target
        next {
            val entity = this@useBlock()
            val pos = target()
            val hit = entity.level.clip(
                ClipContext(
                    pos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity
                )
            )
            entity.swing(InteractionHand.MAIN_HAND)
            val state = entity.level.getBlockState(hit.blockPos)
            state.use(entity.level, entity.fakePlayer, InteractionHand.MAIN_HAND, hit)
        }
    }

    infix fun NPCProperty.destroyBlock(target: () -> Vec3) {
        this moveTo target
        this lookAt target
        next {
            val entity = this@destroyBlock()
            val manager = entity.fakePlayer.gameMode

            manager.destroyBlock(BlockPos(target()))
            entity.swing(InteractionHand.MAIN_HAND)
        }
    }

    infix fun NPCProperty.dropItem(stack: () -> ItemStack) = next {
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

    infix fun NPCProperty.requestItems(block: GiveItemList.() -> Unit) {
        +NpcItemListNode(block, this@requestItems)
    }

    fun NPCProperty.waitInteract() {
        +NpcInteractNode(this@waitInteract)
    }

    infix fun NPCProperty.tpTo(target: TeleportContainer.() -> Unit) = next {
        val tp = TeleportContainer().apply(target)
        val teleport = tp.pos
        this@tpTo.invoke().teleportTo(teleport.x, teleport.y, teleport.z)
    }

    infix fun NPCProperty.addTrade(offer: () -> MerchantOffer) = next {
        this@addTrade().npcTrader.npcOffers.add(offer())
    }

    fun NPCProperty.clearTrades() = next {
        this@clearTrades().npcTrader.npcOffers.clear()
    }

    fun NPCProperty.clearTradeUses() = next {
        this@clearTradeUses().npcTrader.npcOffers.forEach { it.resetUses() }
    }

    @Suppress("UNCHECKED_CAST")
    inline infix fun <reified T> NPCProperty.lookAt(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> +NpcLookToBlockNode(this@lookAt, target as NpcTarget<Vec3>)
            Entity::class.java.isAssignableFrom(type) -> +NpcLookToEntityNode(this@lookAt, target as NpcTarget<Entity>)
            Team::class.java.isAssignableFrom(type) -> +NpcLookToTeamNode(this@lookAt, target as NpcTarget<Team>)
            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline infix fun <reified T> NPCProperty.lookAlwaysAt(target: NpcTarget<T>) {

        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> next {
                this@lookAlwaysAt().npcTarget.lookingPos = target() as Vec3
                this@lookAlwaysAt().npcTarget.lookingEntity = null
                this@lookAlwaysAt().npcTarget.lookingTeam = null
            }

            Entity::class.java.isAssignableFrom(type) -> next {
                this@lookAlwaysAt().npcTarget.lookingPos = null
                this@lookAlwaysAt().npcTarget.lookingEntity = target() as Entity
                this@lookAlwaysAt().npcTarget.lookingTeam = null
            }

            Team::class.java.isAssignableFrom(type) -> next {
                this@lookAlwaysAt().npcTarget.lookingPos = null
                this@lookAlwaysAt().npcTarget.lookingEntity = null
                this@lookAlwaysAt().npcTarget.lookingTeam = target() as Team
            }

            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    fun NPCProperty.stopLookAlways() = next {
        this@stopLookAlways().npcTarget.lookingPos = null
        this@stopLookAlways().npcTarget.lookingEntity = null
        this@stopLookAlways().npcTarget.lookingTeam = null
    }


    fun NPCProperty.lookAtEntityType(entity: () -> String) {
        val entityType = ForgeRegistries.ENTITY_TYPES.getValue(entity().rl)!!

        lookAt {
            val npc = this()
            val level = npc.level

            level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(npc.position(), 25.0, 25.0, 25.0)) {
                it.type == entityType
            }.minByOrNull { it.distanceTo(npc) } ?: npc
        }
    }

    infix fun NPCProperty.replay(file: () -> String) {
        next {
            val replay = Replay.fromFile(DirectoryManager.HOLLOW_ENGINE.resolve("replays").resolve(file()))
            ReplayPlayer(this@replay()).apply {
                saveEntity = true
                isLooped = false
                play(this@replay().level, replay)
            }
        }
    }


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


    infix fun NPCProperty.say(text: () -> String) = sayComponent { text().mcTranslate }

    open infix fun NPCProperty.sayComponent(text: () -> Component) = +SimpleNode {
        val component = ("§6[§7" + this@sayComponent().displayName.string + "§6]§7 ").mcText + text()
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(component) }
    }

    @JvmName("playerSay")
    infix fun PlayerProperty.say(text: () -> String) = sayComponent { text().mcTranslate }

    @JvmName("playerSayComponent")
    open infix fun PlayerProperty.sayComponent(text: () -> Component) = +SimpleNode {
        val component = ("§6[§7" + this@sayComponent().displayName.string + "§6]§7 ").mcText + text()
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

    open infix fun Team.send(text: () -> String) = +SimpleNode {
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
        var icon: ResourceLocation = ResourceLocation("hollowengine:textures/gui/icons/question.png")
        var inverse = false
        var ignoreY = true
        var createIcon = true
    }

    infix fun Team.waitPos(context: PosWaiter.() -> Unit) {
        next {
            val waiter = PosWaiter().apply(context)
            if(!waiter.createIcon) return@next
            val pos = waiter.pos
            this@waitPos.capability(StoryTeamCapability::class).aimMarks +=
                AimMark(pos.x, pos.y, pos.z, waiter.icon, waiter.ignoreY)
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

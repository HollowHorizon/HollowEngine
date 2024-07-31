/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:Suppress("INAPPLICABLE_JVM_NAME")

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.ListTag
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
import net.minecraftforge.event.TickEvent.PlayerTickEvent
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.RawPose
import ru.hollowhorizon.hc.client.models.gltf.manager.SubModel
import ru.hollowhorizon.hc.client.render.shaders.post.PostChainPacket
import ru.hollowhorizon.hc.client.screens.CloseGuiPacket
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hc.common.ui.Widget
import ru.hollowhorizon.hollowengine.common.capabilities.AimMark
import ru.hollowhorizon.hollowengine.common.capabilities.PlayerStoryCapability
import ru.hollowhorizon.hollowengine.common.capabilities.StoriesCapability
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.entities.SeatEntity
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.scripting.item
import ru.hollowhorizon.hollowengine.common.scripting.story.ProgressManager
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events.InputNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.AnimationContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.NpcContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.SeatContainer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.TeleportContainer
import ru.hollowhorizon.hollowengine.common.util.Safe
import ru.hollowhorizon.hollowengine.common.util.getStructure
import ru.hollowhorizon.hollowengine.cutscenes.replay.Replay
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer
import java.util.*
import kotlin.math.sqrt

fun StoryStateMachine.main() {
    waitForgeEvent<PlayerTickEvent> {
        return@waitForgeEvent it.player.inventory.items.sumOf { if (it.item != item("minecraft:apple").item) 0 else it.count } >= 5
    }
}

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
        +NpcDelegate { NpcContainer().apply(settings) }

    fun NPCEntity.Companion.finding(settings: NPCFindContainer.() -> Unit) =
        +NPCFindDelegate { NPCFindContainer().apply(settings) }


    fun NPCEntity.Companion.fromSubModel(subModel: NpcContainer.() -> SubModel) = +NpcDelegate {
        NpcContainer().apply {
            val settings = subModel()
            model = settings.model
            textures.putAll(settings.textures)
            transform = settings.transform
            subModels.putAll(settings.subModels)
        }
    }

    fun Widget.close() {
        stateMachine.server.playerList.players.forEach { CloseGuiPacket().send(PacketDistributor.PLAYER.with { it }) }
    }

    fun Widget.runSingleCommandInGui(command: String): Int {
        val server = stateMachine.server
        val src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()

        return server.commands.performPrefixedCommand(src, command)
    }

    var Safe<NPCEntity>.hitboxMode
        get(): HitboxMode = this()[NPCCapability::class].hitboxMode
        set(value) {
            next {
                this@hitboxMode()[NPCCapability::class].hitboxMode = value
            }
        }

    var Safe<NPCEntity>.flying
        get() = this().flying
        set(value) {
            next {
                this@flying().flying = value
            }
        }

    var Safe<NPCEntity>.icon
        get(): NpcIcon = this()[NPCCapability::class].icon
        set(value) {
            next {
                this@icon()[NPCCapability::class].icon = value
            }
        }

    var Safe<NPCEntity>.invulnerable
        get() = this().isInvulnerable
        set(value) {
            next {
                this@invulnerable().isInvulnerable = value
            }
        }

    var Safe<NPCEntity>.name: String
        get() = this().displayName.string
        set(value) {
            next {
                val npc = this@name()
                npc.customName = value.mcTranslate
                npc.level[StoriesCapability::class].activeNpcs[npc.uuid.toString()] = npc.customName.toString()
            }
        }

    var Safe<NPCEntity>.isRunning: Boolean
        get() = this().isSprinting
        set(value) {
            next {
                this@isRunning().isSprinting = value
            }
        }

    fun Safe<List<ServerPlayer>>.seat(seat: SeatContainer.() -> Unit) {
        next {
            val c = SeatContainer().apply(seat)

            this@seat().forEach {
                SeatEntity.seat(it, BlockPos(c.pos), c.offset, c.rot)
            }
        }
    }

    infix fun Safe<NPCEntity>.giveLeftHand(item: () -> ItemStack?) {
        next {
            this@giveLeftHand().setItemInHand(InteractionHand.OFF_HAND, item() ?: ItemStack.EMPTY)
        }
    }

    infix fun Safe<NPCEntity>.giveRightHand(item: () -> ItemStack?) {
        next {
            this@giveRightHand().setItemInHand(InteractionHand.MAIN_HAND, item() ?: ItemStack.EMPTY)
        }
    }

    infix fun Safe<NPCEntity>.configure(body: AnimatedEntityCapability.() -> Unit) {
        next {
            this@configure()[AnimatedEntityCapability::class].apply(body)
        }
    }

    fun Safe<NPCEntity>.despawn() {
        next {
            if (!this@despawn.isLoaded) return@next
            val npc = this@despawn()
            val activeNpcs = npc.level[StoriesCapability::class].activeNpcs
            activeNpcs.remove(npc.uuid.toString())
            npc.remove(Entity.RemovalReason.DISCARDED)
        }
    }

    fun Safe<List<ServerPlayer>>.input(vararg args: String) = +InputNode(*args, players = this@input)

    @Suppress("UNCHECKED_CAST")
    inline infix fun <reified T> Safe<NPCEntity>.moveTo(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> +NpcMoveToBlockNode(this@moveTo, target as NpcTarget<Vec3>)
            Entity::class.java.isAssignableFrom(type) -> +NpcMoveToEntityNode(this@moveTo, target as NpcTarget<Entity>)
            Collection::class.java.isAssignableFrom(type) -> +NpcMoveToGroupNode(
                this@moveTo,
                target as NpcTarget<List<Entity>>
            )

            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    infix fun Safe<NPCEntity>.moveToBiome(biomeName: () -> String) {
        +NpcMoveToBlockNode(this@moveToBiome) {
            val npc = this@moveToBiome()
            val biome = biomeName().rl

            val pos = (npc.level as ServerLevel).findClosestBiome3d(
                { it.`is`(biome) }, npc.blockPosition(), 6400, 32, 64
            )?.first ?: npc.blockPosition()
            Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        }
    }

    fun Safe<NPCEntity>.moveToStructure(structureName: () -> String, offset: () -> BlockPos = { BlockPos.ZERO }) {
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

    inline infix fun <reified T> Safe<NPCEntity>.moveAlwaysTo(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> next {
                this@moveAlwaysTo().npcTarget.apply {
                    movingPos = target() as Vec3
                    movingEntity = null
                    movingGroup = null
                }
            }

            Entity::class.java.isAssignableFrom(type) -> next {
                this@moveAlwaysTo().npcTarget.apply {
                    movingPos = null
                    movingEntity = target() as Entity
                    movingGroup = null
                }
            }

            List::class.java.isAssignableFrom(type) -> next {
                this@moveAlwaysTo().npcTarget.apply {
                    movingPos = null
                    movingEntity = null
                    movingGroup = target() as List<ServerPlayer>
                }
            }

            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    fun Safe<NPCEntity>.stopMoveAlways() {
        next {
            this@stopMoveAlways().npcTarget.apply {
                movingPos = null
                movingEntity = null
                movingGroup = null
            }
        }
    }

    inline infix fun <reified T> Safe<NPCEntity>.setTarget(target: NpcTarget<T>) {

        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> throw UnsupportedOperationException("Can't attack a block!")
            LivingEntity::class.java.isAssignableFrom(type) -> next {
                this@setTarget().target = target() as LivingEntity
            }

            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }

    fun Safe<NPCEntity>.clearTarget() {
        next { this@clearTarget().target = null }
    }

    infix fun Safe<NPCEntity>.setPose(fileName: () -> String) {
        next {
            val file = fileName()
            val replay = RawPose.fromNBT(
                DirectoryManager.HOLLOW_ENGINE.resolve("npcs/poses/").resolve(file).inputStream().loadAsNBT()
            )
            this@setPose()[AnimatedEntityCapability::class].pose = replay
        }
    }

    fun Safe<NPCEntity>.clearPose() {
        next { this@clearPose()[AnimatedEntityCapability::class].pose = RawPose() }
    }

    infix fun Safe<NPCEntity>.play(block: AnimationContainer.() -> Unit) {
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


    infix fun Safe<NPCEntity>.playLooped(animation: () -> String) = play {
        this.playType = PlayMode.LOOPED
        this.animation = animation()
    }

    infix fun Safe<NPCEntity>.playOnce(animation: () -> String) = play {
        this.playType = PlayMode.ONCE
        this.animation = animation()
    }

    infix fun Safe<NPCEntity>.playFreeze(animation: () -> String) = play {
        this.playType = PlayMode.LAST_FRAME
        this.animation = animation()
    }

    infix fun Safe<NPCEntity>.stop(animation: () -> String) {
        next {
            val anim = animation()
            this@stop()[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == anim }
            StopAnimationPacket(this@stop().id, anim).send(PacketDistributor.TRACKING_ENTITY.with(this@stop))
        }
    }

    infix fun Safe<NPCEntity>.useBlock(target: () -> Vec3) {
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

    infix fun Safe<NPCEntity>.destroyBlock(target: () -> Vec3) {
        this moveTo target
        this lookAt target
        next {
            val entity = this@destroyBlock()
            val manager = entity.fakePlayer.gameMode

            manager.destroyBlock(BlockPos(target()))
            entity.swing(InteractionHand.MAIN_HAND)
        }
    }

    infix fun Safe<NPCEntity>.dropItem(stack: () -> ItemStack) {
        next {
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
    }

    infix fun Safe<NPCEntity>.requestItems(block: GiveItemList.() -> Unit) {
        +NpcItemListNode(block, this@requestItems)
    }

    fun Safe<NPCEntity>.waitInteract() {
        +NpcInteractNode(this@waitInteract)
    }

    infix fun Safe<NPCEntity>.tpTo(target: TeleportContainer.() -> Unit) {
        next {
            val tp = TeleportContainer().apply(target)
            val teleport = tp.pos
            val world = manager.server.levelKeys().find { it.location() == tp.world.rl } ?: throw IllegalStateException(
                "Dimension ${tp.world} not found!"
            )
            val level =
                manager.server.getLevel(world) ?: throw IllegalStateException("Dimension ${tp.world} can't be loaded!")
            this@tpTo().apply {
                if (this.level != level) changeDimension(level)
                teleportTo(teleport.x, teleport.y, teleport.z)
                xRot = tp.vec.x
                yHeadRot = tp.vec.y
                yRot = tp.vec.y
            }
        }
    }

    @JvmName("playerTpTo")
    infix fun Safe<List<ServerPlayer>>.tpTo(target: TeleportContainer.() -> Unit) {
        next {
            val tp = TeleportContainer().apply(target)
            val teleport = tp.pos
            val world = manager.server.levelKeys().find { it.location() == tp.world.rl } ?: throw IllegalStateException(
                "Dimension ${tp.world} not found!"
            )
            val level =
                manager.server.getLevel(world) ?: throw IllegalStateException("Dimension ${tp.world} can't be loaded!")
            this@tpTo().forEach { it.teleportTo(level, teleport.x, teleport.y, teleport.z, tp.vec.x, tp.vec.y) }
        }
    }

    infix fun Safe<NPCEntity>.addTrade(offer: () -> MerchantOffer) {
        next {
            this@addTrade().npcOffers.add(offer())
        }
    }

    fun Safe<NPCEntity>.clearTrades() {
        next {
            this@clearTrades().npcOffers.clear()
        }
    }

    fun Safe<NPCEntity>.clearTradeUses() {
        next {
            this@clearTradeUses().npcOffers.forEach { it.resetUses() }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline infix fun <reified T> Safe<NPCEntity>.lookAt(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> +NpcLookToBlockNode(this@lookAt, target as NpcTarget<Vec3>)
            Entity::class.java.isAssignableFrom(type) -> +NpcLookToEntityNode(this@lookAt, target as NpcTarget<Entity>)
            Collection::class.java.isAssignableFrom(type) -> +NpcLookAtGroupNode(
                this@lookAt,
                target as NpcTarget<List<Entity>>
            )

            else -> throw IllegalArgumentException("Can't look at ${type.name} target!")
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline infix fun <reified T> Safe<NPCEntity>.lookAlwaysAt(target: NpcTarget<T>) {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> next {
                this@lookAlwaysAt().npcTarget.apply {
                    lookingPos = target() as Vec3
                    lookingEntity = null
                    lookingGroup = null
                }
            }

            Entity::class.java.isAssignableFrom(type) -> next {
                this@lookAlwaysAt().npcTarget.apply {
                    lookingPos = null
                    lookingEntity = target() as Entity
                    lookingGroup = null
                }
            }

            List::class.java.isAssignableFrom(type) -> next {
                this@lookAlwaysAt().npcTarget.apply {
                    lookingPos = null
                    lookingEntity = null
                    lookingGroup = target() as List<ServerPlayer>
                }
            }

            else -> throw IllegalArgumentException("Can't look at ${type.name} target!")
        }
    }

    fun Safe<NPCEntity>.stopLookAlways() {
        next {
            this@stopLookAlways().npcTarget.apply {
                lookingPos = null
                lookingEntity = null
                lookingGroup = null
            }
        }
    }


    fun Safe<NPCEntity>.lookAtEntityType(entity: () -> String) {
        val entityType = ForgeRegistries.ENTITY_TYPES.getValue(entity().rl)!!
        lookAt {
            val npc = this()
            val level = npc.level

            level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(npc.position(), 25.0, 25.0, 25.0)) {
                it.type == entityType
            }.minByOrNull { it.distanceTo(npc) } ?: npc
        }
    }

    infix fun Safe<NPCEntity>.replay(file: () -> String) {
        next {
            val replay = Replay.fromFile(DirectoryManager.HOLLOW_ENGINE.resolve("replays").resolve(file()))
            ReplayPlayer(this@replay()).apply {
                saveEntity = true
                isLooped = false
                play(this@replay().level, replay)
            }
        }
    }

    infix fun Safe<NPCEntity>.replayAndWait(file: () -> String) {
        wait {
            val replay = Replay.fromFile(DirectoryManager.HOLLOW_ENGINE.resolve("replays").resolve(file()))
            ReplayPlayer(this@replayAndWait()).apply {
                saveEntity = true
                isLooped = false
                play(this@replayAndWait().level, replay)
            }
            replay.points.count()
        }
    }


    // ------------------------------------
    //          Функции квестов
    // ------------------------------------
    fun ProgressManager.addMessage(message: () -> String) = next {
        players().forEach {
            val story = it[PlayerStoryCapability::class]
            story.quests += message()
        }
    }

    fun ProgressManager.removeMessage(message: () -> String) = +SimpleNode {
        players().forEach {
            val story = it[PlayerStoryCapability::class]
            story.quests -= message()
        }
    }

    fun ProgressManager.clear() = +SimpleNode {
        players().forEach {
            val story = it[PlayerStoryCapability::class]
            story.quests.clear()
        }
    }

    @JvmName("playerPlay")
    infix fun Safe<List<ServerPlayer>>.play(block: AnimationContainer.() -> Unit) = +SimpleNode {
        val container = AnimationContainer().apply(block)

        for (serverPlayer in this@play()) {
            val serverLayers = serverPlayer[AnimatedEntityCapability::class].layers

            if (serverLayers.any { it.animation == container.animation }) return@SimpleNode

            StartAnimationPacket(
                serverPlayer.id, container.animation, container.layerMode, container.playType, container.speed
            ).send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with { serverPlayer })

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

    @JvmName("playerPlayLooped")
    infix fun Safe<List<ServerPlayer>>.playLooped(animation: () -> String) = play {
        this.playType = PlayMode.LOOPED
        this.animation = animation()
    }

    @JvmName("playerPlayOnce")
    infix fun Safe<List<ServerPlayer>>.playOnce(animation: () -> String) = play {
        this.playType = PlayMode.ONCE
        this.animation = animation()
    }

    @JvmName("playerPlayFreeze")
    infix fun Safe<List<ServerPlayer>>.playFreeze(animation: () -> String) = play {
        this.playType = PlayMode.LAST_FRAME
        this.animation = animation()
    }

    @JvmName("playerStop")
    infix fun Safe<List<ServerPlayer>>.stop(animation: () -> String) = +SimpleNode {
        val anim = animation()
        for (serverPlayer in this@stop()) {
            val serverLayers = serverPlayer[AnimatedEntityCapability::class].layers
            serverLayers.removeIfNoUpdate { it.animation == anim }
        }
    }


    infix fun Safe<NPCEntity>.say(text: () -> String) = sayComponent { text().mcTranslate }

    open infix fun Safe<NPCEntity>.sayComponent(text: () -> Component): SimpleNode {
        return next {
            val component = ("§6[§7" + this@sayComponent().displayName.string + "§6]§7 ").mcText + text()
            manager.server.playerList.players.forEach { it.sendSystemMessage(component) }
        }
    }

    @JvmName("playerSend")
    infix fun Safe<List<ServerPlayer>>.send(text: () -> String) = sendComponent { text().mcTranslate }

    @JvmName("playerSendComponent")
    open infix fun Safe<List<ServerPlayer>>.sendComponent(text: () -> Component) = +SimpleNode {
        this@sendComponent().forEach {
            it.sendSystemMessage(text())
        }
    }

    @JvmName("playerSay")
    infix fun Safe<List<ServerPlayer>>.say(text: () -> String) = sayComponent { text().mcTranslate }

    @JvmName("playerSayComponent")
    open infix fun Safe<List<ServerPlayer>>.sayComponent(text: () -> Component) = +SimpleNode {
        this@sayComponent().forEach {
            val component = ("§6[§7" + it.displayName.string + "§6]§7 ").mcText + text()
            it.sendSystemMessage(component)
        }
    }

    @JvmName("playerConfigure")
    infix fun Safe<List<ServerPlayer>>.configure(body: AnimatedEntityCapability.() -> Unit) = +SimpleNode {
        this@configure().forEach { it[AnimatedEntityCapability::class].apply(body) }
    }

    fun Safe<List<ServerPlayer>>.saveInventory() = next {
        for (serverPlayer in this@saveInventory()) {
            serverPlayer.persistentData.put("he_inventory", ListTag().apply(serverPlayer.inventory::save))
        }
    }

    fun Safe<List<ServerPlayer>>.loadInventory() = next {
        for (serverPlayer in this@loadInventory()) {

            if (serverPlayer.persistentData.contains("he_inventory")) {
                serverPlayer.inventory.load(serverPlayer.persistentData.getList("he_inventory", 10))
                serverPlayer.inventory.setChanged()
            }
        }
    }

    fun Safe<List<ServerPlayer>>.clearInventory() = next {
        for (serverPlayer in this@clearInventory()) {
            serverPlayer.inventory.clearContent()
            serverPlayer.inventory.setChanged()
        }
    }

    infix fun Safe<List<ServerPlayer>>.postEffect(effect: () -> ResourceLocation) = next {
        for (serverPlayer in this@postEffect()) PostChainPacket(effect()).send(serverPlayer)
    }

    fun Safe<List<ServerPlayer>>.clearPostEffects() = +SimpleNode {
        for (serverPlayer in this@clearPostEffects()) PostChainPacket(null).send(serverPlayer)
    }

    class PosWaiter {
        var pos = Vec3(0.0, 0.0, 0.0)
        var radius = 0.0
        var world = "minecraft:overworld"
        var icon: ResourceLocation = ResourceLocation("hollowengine:textures/gui/icons/question.png")
        var inverse = false
        var ignoreY = true
        var createIcon = true
    }

    infix fun Safe<List<ServerPlayer>>.waitPos(context: PosWaiter.() -> Unit) {
        next {
            for (serverPlayer in this@waitPos()) {
                val waiter = PosWaiter().apply(context)
                if (!waiter.createIcon) return@next
                val pos = waiter.pos
                serverPlayer[PlayerStoryCapability::class].aimMarks +=
                    AimMark(pos.x, pos.y, pos.z, waiter.icon, waiter.ignoreY)
            }
        }
        waitForgeEvent<ServerTickEvent> {
            var result = false
            val waiter = PosWaiter().apply(context)
            val worldKey = stateMachine.server.levelKeys().find { it.location() == waiter.world.rl }
            val world = worldKey?.let { stateMachine.server.getLevel(it) } ?: return@waitForgeEvent false

            this().forEach { player ->

                val distance: (Vec3) -> Double =
                    if (!waiter.ignoreY) { pos: Vec3 -> sqrt(player.distanceToSqr(pos)) } else player::distanceToXZ
                val compare: (Double) -> Boolean =
                    if (!waiter.inverse) { len: Double -> len <= waiter.radius }
                    else { len: Double -> len >= waiter.radius }

                result = result || compare(distance(waiter.pos))
                result = result && player.getLevel() == world
            }

            if (result && waiter.createIcon) {
                for (serverPlayer in this@waitPos()) {
                    serverPlayer[PlayerStoryCapability::class].aimMarks.removeIf { it.x == waiter.pos.x && it.y == waiter.pos.y && it.z == waiter.pos.z }
                }
            }

            result
        }
    }

    infix fun Safe<List<ServerPlayer>>.removeMark(pos: () -> Vec3) = next {
        for (serverPlayer in this@removeMark()) serverPlayer[PlayerStoryCapability::class].aimMarks.removeIf { it.x == pos().x && it.y == pos().y && it.z == pos().z }
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

    fun AsyncProperty.join() = await {
        stateMachine.asyncNodeIds.contains(this.index)
    }

    fun ConditionNode.Elif(condition: () -> Boolean, tasks: NodeContextBuilder.() -> Unit = {}) =
        If(condition, tasks).apply {
            this@Elif.setElseTasks(Collections.singletonList(this))
        }

    fun pos(x: Number, y: Number, z: Number) = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    fun vec(x: Number, y: Number) = Vec2(x.toFloat(), y.toFloat())

    val Int.sec get() = this * 20
    val Int.min get() = this * 1200
    val Int.hours get() = this * 72000
}

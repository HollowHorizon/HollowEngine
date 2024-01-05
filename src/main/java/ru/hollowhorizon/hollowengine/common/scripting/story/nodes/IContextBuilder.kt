package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.core.Registry
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.ITeleporter
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.models.gltf.Transform
import ru.hollowhorizon.hc.client.models.gltf.animations.AnimationType
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.*
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hollowengine.client.render.effects.ParticleEffect
import ru.hollowhorizon.hollowengine.client.screen.FadeOverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.Attributes
import ru.hollowhorizon.hollowengine.common.scripting.story.ProgressManager
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*
import java.util.*
import java.util.function.Function

interface IContextBuilder {
    val stateMachine: StoryStateMachine

    operator fun <T : Node> T.unaryPlus(): T

    fun next(block: SimpleNode.() -> Unit) = +SimpleNode(block)

    private fun innerBreak(
        tag: String = "",
        node: Node = stateMachine.nodes[stateMachine.currentIndex],
        stack: Stack<WhileNode> = Stack()
    ) {
        if (node is WhileNode) stack.push(node)
        if (node is HasInnerNodes) {
            if (node.currentNode is HasInnerNodes) innerBreak(tag, node.currentNode)
            else while (!stack.empty()) {
                val whileNode = stack.pop()
                if (whileNode.tag == tag) {
                    whileNode.shouldBreak = true
                    break
                }
            }
        } else throw IllegalArgumentException("${node.javaClass} is not a HasInnerNodes. May be you called Break() not in loop?")
    }

    private fun innerContinue(
        tag: String = "",
        node: Node = stateMachine.nodes[stateMachine.currentIndex],
        stack: Stack<WhileNode> = Stack()
    ) {
        if (node is WhileNode) stack.push(node)
        if (node is HasInnerNodes) {
            if (node.currentNode is HasInnerNodes) innerBreak(tag, node.currentNode)
            else while (!stack.empty()) {
                val whileNode = stack.pop()
                if (whileNode.tag == tag) {
                    whileNode.shouldContinue = true
                    break
                }
            }
        } else throw IllegalArgumentException("${node.javaClass} is not a HasInnerNodes. May be you called Continue() not in loop?")
    }

    fun Break(tag: () -> String) = SimpleNode { innerBreak(tag()) }
    fun Continue(tag: () -> String) = SimpleNode { innerContinue(tag()) }

    class NpcContainer {
        var name = "Неизвестный"
        var model = "hollowengine:models/entity/player_model.gltf"
        val animations = HashMap<AnimationType, String>()
        val textures = HashMap<String, String>()
        var transform = Transform()
        var world = "minecraft:overworld"
        var pos = Vec3(0.0, 0.0, 0.0)
        var rotation = Vec2.ZERO
        var attributes = Attributes()
        var size = Pair(0.6f, 1.8f)
        var showName = true
        var switchHeadRot = false

        fun skin(name: String) = "skins/$name"
    }

    fun NPCEntity.Companion.creating(settings: NpcContainer.() -> Unit): NpcDelegate {
        val container = NpcContainer().apply(settings)
        return +NpcDelegate(container).apply { manager = stateMachine }
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

        val pos = (npc.level as ServerLevel).findClosestBiome3d(
            { it.`is`(biome) },
            npc.blockPosition(),
            6400,
            32,
            64
        )?.first ?: npc.blockPosition()
        Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
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

    infix fun NPCProperty.lookAtPos(target: () -> Vec3) = +NpcLookToBlockNode(this, target)
    infix fun NPCProperty.lookAtEntity(target: () -> Entity) = +NpcLookToEntityNode(this, target)

    infix fun NPCProperty.lookAtTeam(target: () -> Team) = +NpcLookToTeamNode(this, target)

    infix fun NPCProperty.tp(target: SimpleTeleport.() -> Unit) = +SimpleNode {
        val tp = SimpleTeleport().apply(target)
        val teleport = tp.pos
        this@tp.invoke().teleportTo(teleport.x, teleport.y, teleport.z)
    }

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
        var layerMode = LayerMode.ADD
        var playType = PlayMode.LOOPED
        var speed = 1.0f
    }

    infix fun NPCProperty.play(block: AnimationContainer.() -> Unit) = +SimpleNode {
        val container = AnimationContainer().apply(block)
        StartAnimationPacket(
            this@play().id,
            container.animation,
            container.layerMode,
            container.playType,
            container.speed
        ).send(PacketDistributor.TRACKING_ENTITY.with(this@play))
    }

    infix fun NPCProperty.playLooped(animation: () -> String) = play {
        this.playType = PlayMode.LOOPED
        this.animation = animation()
    }

    infix fun NPCProperty.playOnce(animation: () -> String) = +SimpleNode {
        StartAnimationPacket(this@playOnce().id, animation(), LayerMode.ADD, PlayMode.ONCE, 1.0f)
            .send(PacketDistributor.TRACKING_ENTITY.with(this@playOnce))
    }

    infix fun NPCProperty.stop(animation: () -> String) = +SimpleNode {
        StopAnimationPacket(this@stop().id, animation())
            .send(PacketDistributor.TRACKING_ENTITY.with(this@stop))
    }


    infix fun NPCProperty.say(text: () -> String) = +SimpleNode {
        val component = Component.literal("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(component) }
    }

    infix fun NPCProperty.configure(body: AnimatedEntityCapability.() -> Unit) = +SimpleNode {
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

    fun AnimatedEntityCapability.skin(name: String) = "skins/$name"

    infix fun Team.sendAsPlayer(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach {
            val componente = Component.literal("§6[§7${it.displayName.string}§6]§7 ").append(text().mcTranslate)
            it.sendSystemMessage(componente)
        }
    }

    infix fun Team.send(text: () -> String) = +SimpleNode {
        stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(text().mcTranslate) }
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

    class TeamHelper(val team: Team) {
        operator fun ItemStack.unaryPlus() {
            team.onlineMembers.forEach {
                it.inventory.add(this)
                it.inventory.setChanged()
            }
        }

        fun setHealth(value: Float) {
            team.onlineMembers.forEach {
                it.health = value
            }
        }

        fun setMaxHealth(value: Float) {
            team.onlineMembers.forEach {
                it.attributes.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue =
                    value.toDouble()
            }
            setHealth(value)
        }

        fun addHealth(value: Float) {
            team.onlineMembers.forEach {
                it.health += value
            }
        }

        fun equipHelmet(item: ItemStack) = team.onlineMembers.forEach {
            it.drop(it.getItemBySlot(EquipmentSlot.HEAD), false)
            it.setItemSlot(EquipmentSlot.HEAD, item)
        }

        fun equipChestplate(item: ItemStack) = team.onlineMembers.forEach {
            it.drop(it.getItemBySlot(EquipmentSlot.CHEST), false)
            it.setItemSlot(EquipmentSlot.CHEST, item)
        }

        fun equipLeggings(item: ItemStack) = team.onlineMembers.forEach {
            it.drop(it.getItemBySlot(EquipmentSlot.LEGS), false)
            it.setItemSlot(EquipmentSlot.LEGS, item)
        }

        fun equipBoots(item: ItemStack) = team.onlineMembers.forEach {
            it.drop(it.getItemBySlot(EquipmentSlot.FEET), false)
            it.setItemSlot(EquipmentSlot.FEET, item)
        }
    }

    infix fun Team.modify(inv: TeamHelper.() -> Unit) = +SimpleNode {
        TeamHelper(this@modify).apply(inv)
    }

    class GiveItemList {
        val items = mutableListOf<ItemStack>()
        var text = "hollowengine.npc_need"

        operator fun ItemStack.unaryPlus() {
            items.add(this)
        }
    }

    infix fun NPCProperty.requestItems(block: GiveItemList.() -> Unit) = +NpcItemListNode(block, this@requestItems)

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
        stateMachine.team.onlineMembers.forEach {
            FadeOverlayScreenPacket(
                true,
                container.text,
                container.subtitle,
                container.color,
                container.texture,
                container.time
            ).send(PacketDistributor.PLAYER.with { it })
        }
        container.time
    }

    fun fadeOut(block: FadeContainer.() -> Unit) = +WaitNode {
        val container = FadeContainer().apply(block)
        stateMachine.team.onlineMembers.forEach {
            FadeOverlayScreenPacket(
                false,
                container.text,
                container.subtitle,
                container.color,
                container.texture,
                container.time
            ).send(PacketDistributor.PLAYER.with { it })
        }
        container.time
    }

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

    fun async(body: NodeContextBuilder.() -> Unit): AsyncProperty {
        val chainNode = ChainNode(NodeContextBuilder(stateMachine).apply(body).tasks)
        val index = stateMachine.asyncNodes.size
        stateMachine.asyncNodes.add(chainNode)
        +SimpleNode { stateMachine.asyncNodeIds.add(index) }
        return AsyncProperty(index)
    }

    fun AsyncProperty.stop() = +SimpleNode {
        stateMachine.asyncNodeIds.remove(this@stop.index)
    }

    fun AsyncProperty.resume() = +SimpleNode {
        stateMachine.asyncNodeIds.add(this@resume.index)
    }

//    @Serializable Крч, сам сделаешь. Просил сам сделать класс xD
//    data class Point(val x: Double, val y: Double, val z: Double, val xRot: Double, val yRot: Double, val zRot: Double = 0.0)

    class SimpleTeleport {
        var pos: Vec3 = Vec3(0.0, 0.0, 0.0)
        var vec: Vec2 = Vec2(0F, 0F)
        var dim: ResourceKey<Level> = Level.OVERWORLD
    }

    infix fun Team.tp(pos: () -> Vec3) = +SimpleNode {
        val p = pos()
        this@tp.onlineMembers.forEach {
            it.teleportTo(it.getLevel(), p.x, p.y, p.z, it.yHeadRot, it.xRot)
        }
    }

    infix fun Team.tpTo(teleport: SimpleTeleport.() -> Unit) = +SimpleNode {
        val pos = SimpleTeleport().apply(teleport)
        val position = pos.pos
        val camera = pos.vec
        val dimension = pos.dim

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
            })
        }
    }

    fun dim(dimension: String): ResourceKey<Level> = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimension.rl)

    fun pos(x: Double, y: Double, z: Double) = Vec3(x, y, z)
    fun pos(x: Int, y: Int, z: Int) = Vec3(x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5)
    fun vec(x: Int, y: Int) = Vec2(x.toFloat(), y.toFloat())
    fun vec(x: Float, y: Float) = Vec2(x, y)

    operator fun Team.get(name: String): Player? = this.onlineMembers.find { it.gameProfile.name == name }

    val Int.sec get() = this * 20
    val Int.min get() = this * 1200
    val Int.hours get() = this * 72000
}

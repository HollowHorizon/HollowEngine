package ru.hollowhorizon.hollowengine.common.scripting.katari

import kotlinx.coroutines.delay
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.PlayCutscenePacket
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.NpcAnimationRuntime
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.geary.api.set
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.playSound
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.execute
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.pos
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.*

private const val DAY_TICKS = 24000L

@ScriptBinding
fun say(text: String) {
    currentServer.playerList.players.forEach { it.sendSystemMessage(text.literal) }
}

@ScriptBinding
fun pos(x: Double, y: Double, z: Double): Vec3 = Vec3(x, y, z)

@ScriptBinding
fun blockPos(x: Int, y: Int, z: Int): Vec3 = pos(x, y, z)

@ScriptBinding("npc")
fun createKatariNpc(
    pos: Vec3,
    name: String = "NPC",
    model: String = "hollowengine:models/entity/player_model.gltf",
    world: String = "minecraft:overworld",
): NpcEntity = npc(pos = pos, name = name, model = model, world = world)

@ScriptBinding("command")
fun executeKatariCommand(command: String): Int = execute(command)

@ScriptBinding
fun Player.runScript(path: String) {
    currentServer.runtimeContext.katari.run(path, this as ServerPlayer).getOrThrow()
}

@ScriptBinding
suspend fun waitTime(timeOfDay: Int) {
    val targetTime = timeOfDay.toLong().floorMod(DAY_TICKS)
    while (currentServer.overworld().dayTime.floorMod(DAY_TICKS) != targetTime) delay(50)
}

@ScriptBinding
suspend fun waitDay() {
    while (!currentServer.overworld().isDay) delay(50)
}

@ScriptBinding
suspend fun waitNight() {
    while (currentServer.overworld().isDay) delay(50)
}

@ScriptBinding
fun playSound(
    sound: String,
    position: Vec3,
    volume: Double = 1.0,
    pitch: Double = 1.0,
) {
    currentServer.overworld().playSound(sound, volume.toFloat(), pitch.toFloat(), position, null, false)
}

@ScriptBinding
fun Player.playSound(
    sound: String,
    volume: Double = 1.0,
    pitch: Double = 1.0,
) {
    playSound(sound, volume.toFloat(), pitch.toFloat())
}

@ScriptBinding
fun playCutscene(path: String) {
    currentServer.playerList.players.forEach { player ->
        PlayCutscenePacket(path).send(player)
    }
}

@ScriptBinding
fun Player.playCutscene(path: String) {
    val serverPlayer = this as? ServerPlayer
        ?: error("playCutscene receiver must be a server player")
    PlayCutscenePacket(path).send(serverPlayer)
}

@ScriptBinding
fun Entity.setHitboxMode(mode: HitboxMode) {
    val npc = this as? NpcEntity ?: error("setHitboxMode receiver must be an NPC")
    npc.hitboxMode = mode
}

@ScriptBinding("teleport")
fun Entity.teleportKatari(position: Vec3) {
    teleportTo(
        level() as ServerLevel,
        position.x,
        position.y,
        position.z,
        emptySet<RelativeMovement>(),
        yRot,
        xRot,
    )
}

@ScriptBinding
fun Entity.teleportTo(target: Entity) {
    teleportTo(
        target.level() as ServerLevel,
        target.x,
        target.y,
        target.z,
        emptySet<RelativeMovement>(),
        target.yRot,
        target.xRot,
    )
}

@ScriptBinding
fun Entity.remove() {
    discard()
}

@ScriptBinding
fun Entity.despawn() {
    discard()
}

@ScriptBinding
fun Entity.swing() {
    val livingEntity = this as? LivingEntity ?: error("swing receiver must be a living entity")
    livingEntity.swing(InteractionHand.MAIN_HAND)
}

@ScriptBinding
fun Entity.setHealth(value: Double) {
    val livingEntity = this as? LivingEntity ?: error("setHealth receiver must be a living entity")
    livingEntity.health = value.toFloat()
}

@ScriptBinding
fun Entity.heal(value: Double = 1.0) {
    val livingEntity = this as? LivingEntity ?: error("heal receiver must be a living entity")
    livingEntity.heal(value.toFloat())
}

@ScriptBinding
fun Entity.setModel(model: String, controller: String = "player_model.animation-controller.kts") {
    set(Model(model = model, controllerScript = controller))
}

@ScriptBinding
fun Entity.setTransform(x: Double, y: Double, z: Double, scale: Double) {
    set(TransformComponent.legacy(x.toFloat(), y.toFloat(), z.toFloat(), scale.toFloat()))
}

@ScriptBinding
fun Entity.playAnimation(
    animation: String,
    playMode: AnimationPlayMode = AnimationPlayMode.Once,
    fadeIn: Double = 0.33,
    fadeOut: Double = 0.33,
) {
    NpcAnimationRuntime.apply(
        entity = this,
        from = null,
        to = animation,
        playMode = playMode,
        duration = 0f,
        fadeIn = fadeIn.toFloat(),
        fadeOut = fadeOut.toFloat(),
    )
}

@ScriptBinding
fun Entity.stopAnimation(animation: String, fadeOut: Double = 0.33) {
    NpcAnimationRuntime.apply(
        entity = this,
        from = animation,
        to = null,
        playMode = AnimationPlayMode.Once,
        duration = fadeOut.toFloat(),
    )
}

@ScriptBinding
fun Entity.attack(target: Entity? = null) {
    val mob = this as? Mob ?: error("attack receiver must be a mob")
    mob.target = target as? LivingEntity
}

@ScriptBinding
fun Entity.getAttribute(attribute: String): Double {
    val livingEntity = this as? LivingEntity ?: error("getAttribute receiver must be a living entity")
    return livingEntity.attributes.getInstance(attribute.attribute())?.baseValue ?: 0.0
}

@ScriptBinding
fun Entity.setAttribute(attribute: String, value: Double) {
    val livingEntity = this as? LivingEntity ?: error("setAttribute receiver must be a living entity")
    livingEntity.attributes.getInstance(attribute.attribute())?.baseValue = value
}

@ScriptBinding
fun Player.give(item: String, count: Int = 1) {
    inventory.add(ItemStack(BuiltInRegistries.ITEM.get(item.rl), count))
}

@ScriptBinding
fun Entity.stopNavigation() {
    (this as? Mob)?.navigation?.stop()
}

@ScriptBinding
suspend fun Entity.waitNpcInteract(): Player {
    val event = PlayerInteractEvent.EntityInteract.await { it.target.uuid == uuid }
    return event.player
}

@ScriptBinding
suspend fun Player.waitZone(position: Vec3, radius: Double = 1.0, leave: Boolean = false): Player {
    while ((position().distanceTo(position) <= radius) == leave) delay(50)
    return this
}

@ScriptBinding
suspend fun Player.waitKey(key: Int, action: KatariInputAction = KatariInputAction.Press): KatariInputSnapshot {
    return awaitInput(uuid.toString()) { input ->
        input.kind == KatariInputKind.Key && input.key == key && input.action == action
    }
}

@ScriptBinding
suspend fun Player.waitClick(button: Int, action: KatariInputAction = KatariInputAction.Press): KatariInputSnapshot {
    return awaitInput(uuid.toString()) { input ->
        input.kind == KatariInputKind.MouseButton && input.button == button && input.action == action
    }
}

@ScriptBinding
suspend fun Player.waitScroll(): KatariInputSnapshot {
    return awaitInput(uuid.toString()) { input -> input.kind == KatariInputKind.MouseScroll }
}

@ScriptBinding("name")
var Entity.scriptName: String
    get() = name.string
    set(value) {
        customName = value.literal
    }

@ScriptBinding("uuid")
var Entity.scriptUuid: String
    get() = uuid.toString()
    set(value) {
        uuid = UUID.fromString(value)
    }

@ScriptBinding("customName")
var Entity.scriptCustomName: String?
    get() = customName?.string
    set(value) {
        customName = value?.takeIf(String::isNotBlank)?.literal
    }

@ScriptBinding("alive")
val Entity.scriptAlive: Boolean get() = isAlive

@ScriptBinding("invulnerable")
var Entity.scriptInvulnerable: Boolean
    get() = isInvulnerable
    set(value) {
        isInvulnerable = value
    }

@ScriptBinding("sprinting")
var Entity.scriptSprinting: Boolean
    get() = isSprinting
    set(value) {
        isSprinting = value
    }

@ScriptBinding("health")
var Entity.scriptHealth: Double
    get() = (this as? LivingEntity)?.health?.toDouble() ?: 0.0
    set(value) {
        (this as? LivingEntity)?.health = value.toFloat()
    }

@ScriptBinding("position")
val Entity.scriptPosition: Vec3 get() = position()

@ScriptBinding("dimension")
val Entity.scriptDimension: String get() = level().dimension().location().toString()

@ScriptBinding("mainHand")
val Entity.scriptMainHand: String
    get() {
        val stack = (this as? LivingEntity)?.mainHandItem ?: ItemStack.EMPTY
        return if (stack.isEmpty) "" else BuiltInRegistries.ITEM.getKey(stack.item).toString()
    }

@ScriptBinding("blockX")
val Vec3.blockX: Int get() = BlockPos.containing(this).x

@ScriptBinding("blockY")
val Vec3.blockY: Int get() = BlockPos.containing(this).y

@ScriptBinding("blockZ")
val Vec3.blockZ: Int get() = BlockPos.containing(this).z

private fun Long.floorMod(divisor: Long): Long = ((this % divisor) + divisor) % divisor

private fun String.attribute() =
    BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElseThrow { IllegalArgumentException("Unknown attribute `$this`") }

@file:UseSerializers(ForResourceLocation::class, ForVec3::class)

package ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.audio.SoundLoops
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3
import ru.hollowhorizon.hollowengine.common.utils.rl

/**
 * Configuration for [playLoop].
 *
 * A loop differs from [playSound] in that it keeps running until something stops it, so it needs a
 * name: [playLoop] and [stopLoop] address the same sound through the id the script chose.
 */
@SoundDsl
class SoundLoopBuilder {
    /** Raw audio path, as [playSound] takes it: `namespace:audio/whatever.ogg`. */
    var location: String = ""
    var volume: Float = 1f
    var pitch: Float = 1f

    /** Seconds to reach [volume] from silence. */
    var fadeIn: Float = 1f

    /** Seconds to fall silent once [stopLoop] is called. */
    var fadeOut: Float = 1f

    /**
     * Where the sound is. Leaving it null keeps the loop stuck to the listener, which is what a
     * background hum wants; [at] places it in the world instead, and it then attenuates with distance.
     */
    var position: Vec3? = null
    var relative: Boolean = true

    fun at(pos: Vec3) {
        position = pos
        relative = false
    }

    fun at(x: Double, y: Double, z: Double) = at(Vec3(x, y, z))
}

/**
 * Starts the looping sound [id] for this player, or retunes it when it is already running - calling
 * this again is how a script changes volume or position without a gap in the sound.
 *
 * ```kotlin
 * player.playLoop("drone", "lost_in_space:audio/rotor.ogg") { volume = 0.6f; fadeIn = 2f }
 * ```
 */
fun ServerPlayer.playLoop(id: String, location: String, block: SoundLoopBuilder.() -> Unit = {}) {
    val loop = SoundLoopBuilder().also { it.location = location }.apply(block)
    require(loop.location.isNotEmpty()) { "SoundLoopBuilder.location must be set before starting a loop" }
    StartSoundLoopPacket(
        id = id,
        location = loop.location.rl,
        volume = loop.volume,
        pitch = loop.pitch,
        fadeIn = loop.fadeIn,
        fadeOut = loop.fadeOut,
        position = loop.position,
        relative = loop.relative,
    ).send(this)
}

/** Fades the loop [id] out for this player. Ids that are not playing are ignored. */
fun ServerPlayer.stopLoop(id: String) = StopSoundLoopPacket(id).send(this)

/** Same as [playLoop], for every player in the level. */
fun Level.playLoop(id: String, location: String, block: SoundLoopBuilder.() -> Unit = {}) =
    players().filterIsInstance<ServerPlayer>().forEach { it.playLoop(id, location, block) }

/** Same as [stopLoop], for every player in the level. */
fun Level.stopLoop(id: String) = players().filterIsInstance<ServerPlayer>().forEach { it.stopLoop(id) }

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class StartSoundLoopPacket(
    private val id: String = "",
    private val location: ResourceLocation = "hollowengine:missing".rl,
    private val volume: Float = 1f,
    private val pitch: Float = 1f,
    private val fadeIn: Float = 1f,
    private val fadeOut: Float = 1f,
    private val position: Vec3? = null,
    private val relative: Boolean = true,
) : HollowPacket {
    override fun handle(player: Player) =
        SoundLoops.start(id, loadWave(location), volume, pitch, fadeIn, fadeOut, position, relative)
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class StopSoundLoopPacket(private val id: String = "") : HollowPacket {
    override fun handle(player: Player) = SoundLoops.stop(id)
}

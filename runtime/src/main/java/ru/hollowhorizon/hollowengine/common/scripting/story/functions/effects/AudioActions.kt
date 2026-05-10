package ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects

import kotlinx.serialization.Serializable
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.audio.SoundBuffer
import ru.hollowhorizon.hollowengine.client.audio.SoundPlayer
import ru.hollowhorizon.hollowengine.client.audio.Wave
import ru.hollowhorizon.hollowengine.client.audio.formats.Mp3Format
import ru.hollowhorizon.hollowengine.client.audio.formats.OggFormat
import ru.hollowhorizon.hollowengine.client.audio.formats.WavFormat
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.math.sqrt


infix fun Level.playSound(location: String) = playSound(location, 1f, 1f)
fun Level.playSound(
    location: String,
    volume: Float = 1f,
    pitch: Float = 1f,
    position: Vec3? = null,
    velocity: Vec3? = null,
    relative: Boolean = true,
) {
    this.players().forEach { it.playSound(location, volume, pitch, position, velocity, relative) }
}

infix fun Player.playSound(location: String) = playSound(location, 1.0f, 1.0f)
fun Player.playSound(
    location: String,
    volume: Float = 1f,
    pitch: Float = 1f,
    position: Vec3? = null,
    velocity: Vec3? = null,
    relative: Boolean = true,
) {
    if (location.substringAfter(':').startsWith("audio") || location.endsWith(".mp3") || location.endsWith(".wav")) {
        SoundEffectPacket(location.rl, volume, pitch, position, velocity, relative).send(this as ServerPlayer)
    } else {
        var pos = position ?: Vec3.ZERO
        val holder = Holder.direct(SoundEvent.createVariableRangeEvent(location.rl))
        val e: Double = pos.x - x
        val f: Double = pos.y - y
        val g: Double = pos.z - z
        val h = e * e + f * f + g * g
        var volume = volume
        val d = Mth.square(holder.value().getRange(volume)).toDouble()
        if (h > d) {
            val k = sqrt(h)
            pos = Vec3(
                x + e / k * 2.0,
                y + f / k * 2.0,
                z + g / k * 2.0
            )
            volume = 0.1f
        }
        (this as ServerPlayer).connection.send(
            ClientboundSoundPacket(
                holder,
                SoundSource.MASTER,
                pos.x,
                pos.y,
                pos.z,
                volume,
                pitch,
                level().random.nextLong()
            )
        )

    }
}

val SOUNDS = HashMap<ResourceLocation, Wave>()

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class SoundEffectPacket(
    private val location: @Serializable(ForResourceLocation::class) ResourceLocation,
    private val volume: Float,
    private val pitch: Float,
    private val position: @Serializable(ForVec3::class) Vec3?,
    private val velocity: @Serializable(ForVec3::class) Vec3?,
    private val relative: Boolean,
) : HollowPacket {
    override fun handle(player: Player) {
        val wave = SOUNDS.getOrPut(location) {
            val ext = location.path.substringAfterLast(".")
            val stream = location.stream
            when (ext) {
                "mp3" -> Mp3Format.read(stream)
                "ogg" -> OggFormat.read(stream)
                "wav" -> WavFormat.read(stream)
                else -> error("Unknown audio format: $ext ($location.path)")
            }.apply {
                stream.close()
            }
        }

        SoundPlayer(SoundBuffer(wave)).apply {
            setVolume(volume)
            setPitch(pitch)
            position?.let {
                setPosition(it.x.toFloat(), it.y.toFloat(), it.z.toFloat())
            }
            velocity?.let {
                setVelocity(it.x.toFloat(), it.y.toFloat(), it.z.toFloat())
            }
            setRelative(relative)
        }.play()
    }
}
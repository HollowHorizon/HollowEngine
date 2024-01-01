package ru.hollowhorizon.hollowengine.client.particles

import com.mojang.brigadier.StringReader
import com.mojang.serialization.Codec
import kotlinx.serialization.Serializable
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleOptions.Deserializer
import net.minecraft.core.particles.ParticleType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.util.Mth
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.serialize


class HollowParticleOptions(val particleType: ParticleType<*>) : ParticleOptions {
    var spritePicker = SpritePicker.FIRST_INDEX
    var discardType = DiscardType.NONE

    var colorData = ParticleColor(1f, 1f, 1f, 0f, 0f, 0f)
    var transparencyData = GenericData.DEFAULT
    var scaleData = GenericData.DEFAULT
    var spinData = GenericData.DEFAULT

    var noClip = false
    var lifetime = 20
    var gravity = 0f

    override fun getType() = particleType

    override fun writeToNetwork(buf: FriendlyByteBuf) {
        buf.writeInt(spritePicker.ordinal)
        buf.writeInt(discardType.ordinal)
        buf.writeNbt(NBTFormat.serialize(colorData) as CompoundTag)
        buf.writeNbt(NBTFormat.serialize(transparencyData) as CompoundTag)
        buf.writeNbt(NBTFormat.serialize(scaleData) as CompoundTag)
        buf.writeNbt(NBTFormat.serialize(spinData) as CompoundTag)
        buf.writeBoolean(noClip)
        buf.writeInt(lifetime)
        buf.writeFloat(gravity)
    }

    override fun writeToString() = ""

    companion object {
        fun codecFor(type: ParticleType<*>) = Codec.unit(HollowParticleOptions(type))

        @Suppress("DEPRECATION")
        val DESERIALIZER = object : Deserializer<HollowParticleOptions> {
            override fun fromCommand(
                pParticleType: ParticleType<HollowParticleOptions>,
                pReader: StringReader
            ) = HollowParticleOptions(pParticleType)

            override fun fromNetwork(
                pParticleType: ParticleType<HollowParticleOptions>,
                buf: FriendlyByteBuf
            ) = HollowParticleOptions(pParticleType).apply {
                spritePicker = SpritePicker.entries[buf.readInt()]
                discardType = DiscardType.entries[buf.readInt()]
                colorData = NBTFormat.deserialize(buf.readNbt() ?: return@apply)
                transparencyData = NBTFormat.deserialize(buf.readNbt() ?: return@apply)
                scaleData = NBTFormat.deserialize(buf.readNbt() ?: return@apply)
                spinData = NBTFormat.deserialize(buf.readNbt() ?: return@apply)
                noClip = buf.readBoolean()
                lifetime = buf.readInt()
                gravity = buf.readFloat()
            }
        }
    }
}

enum class SpritePicker {
    FIRST_INDEX, LAST_INDEX, WITH_AGE, RANDOM_SPRITE
}


enum class DiscardType {
    NONE,
    INVISIBLE,
    ENDING_CURVE_INVISIBLE
}

@Serializable
data class ParticleColor(
    val r1: Float,
    val g1: Float,
    val b1: Float,
    val r2: Float,
    val g2: Float,
    val b2: Float,
    val colorCoefficient: Float = 1.0f,
    val colorCurveEasing: Interpolation = Interpolation.LINEAR
) {
    fun getProgress(age: Int, lifetime: Int): Float {
        return Mth.clamp(age * colorCoefficient / lifetime, 0f, 1f)
    }
}

@Serializable
data class GenericData(
    val startingValue: Float,
    val middleValue: Float,
    val endingValue: Float,
    val coefficient: Float = 1.0f,
    val startToMiddleEasing: Interpolation = Interpolation.LINEAR,
    val middleToEndEasing: Interpolation = Interpolation.LINEAR
) {
    var valueMultiplier = 1.0f
    var coefficientMultiplier = 1.0f

    companion object {
        val DEFAULT = GenericData(0f, 1f, -1f, 1.0f)
    }

    val trinary get() = endingValue != -1f

    fun getProgress(age: Int, lifetime: Int): Float {
        return Mth.clamp(age * coefficient * coefficientMultiplier / lifetime, 0f, 1f)
    }

    fun getValue(age: Int, lifetime: Int): Float {
        val progress = getProgress(age, lifetime)
        val result = if (trinary) {
            if (progress >= 0.5f) Mth.lerp(middleToEndEasing.function(progress - 0.5f), middleValue, endingValue)
            else Mth.lerp(startToMiddleEasing.function(progress), startingValue, middleValue)
        } else {
            Mth.lerp(startToMiddleEasing.function(progress), startingValue, middleValue)
        }
        return result * valueMultiplier
    }
}
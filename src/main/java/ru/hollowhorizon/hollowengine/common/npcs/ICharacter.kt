package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT

@Serializable(ForCharacter::class)
interface ICharacter {
    val characterName: String
    val entityType: CompoundTag
    val isNPC: Boolean
        get() = false

}

@Serializable
class NPCAnimation()

enum class Animations {
    IDLE, IDLE_SNEAKED, WALK, WALK_IDLE,
    RUN, SWIM, FALL, FLY, SIT, SLEEP, ATTACK, DEATH
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ICharacter::class)
object ForCharacter : KSerializer<ICharacter> {
    override val descriptor = buildClassSerialDescriptor("ICharacter") {
        element("name", String.serializer().descriptor)
        element("entity", ForCompoundNBT.descriptor)
        element("type", Boolean.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): ICharacter {
        val dec: CompositeDecoder = decoder.beginStructure(descriptor)
        val name = dec.decodeStringElement(descriptor, 0)
        val entity = dec.decodeSerializableElement(descriptor, 1, ForCompoundNBT)
        return object : ICharacter {
            override val characterName = name
            override val entityType = entity
            override val isNPC: Boolean
                get() = dec.decodeBooleanElement(descriptor, 2)
        }
    }

    override fun serialize(encoder: Encoder, value: ICharacter) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeStringElement(descriptor, 0, value.characterName)
        compositeOutput.encodeSerializableElement(descriptor, 1, ForCompoundNBT, value.entityType)
        compositeOutput.encodeBooleanElement(descriptor, 2, value is IHollowNPC)
        compositeOutput.endStructure(descriptor)
    }

}

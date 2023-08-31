package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT

@Serializable(ForCharacter::class)
interface ICharacter {
    val characterName: String
    val entityType: LivingEntity
    val isNPC: Boolean
        get() = false

}

object ForCharacter : KSerializer<ICharacter> {
    override val descriptor = buildClassSerialDescriptor("ICharacter") {
        element("name", String.serializer().descriptor)
        element("entity", ForCompoundNBT.descriptor)
        element("type", Boolean.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): ICharacter {
        val dec: CompositeDecoder = decoder.beginStructure(descriptor)
        val name = dec.decodeStringElement(descriptor, 0)
        val entity = dec.decodeIntElement(descriptor, 1)
        return object : ICharacter {
            override val characterName = name
            override val entityType = Minecraft.getInstance().level!!.getEntity(entity) as LivingEntity
            override val isNPC: Boolean
                get() = dec.decodeBooleanElement(descriptor, 2)
        }
    }

    override fun serialize(encoder: Encoder, value: ICharacter) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeStringElement(descriptor, 0, value.characterName)
        compositeOutput.encodeIntElement(descriptor, 1, value.entityType.id)
        compositeOutput.encodeBooleanElement(descriptor, 2, value is IHollowNPC)
        compositeOutput.endStructure(descriptor)
    }

}

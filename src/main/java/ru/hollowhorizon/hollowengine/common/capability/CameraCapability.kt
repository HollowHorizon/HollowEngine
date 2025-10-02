@file:UseSerializers(ForVec3::class, LivingEntitySerializer::class)

package ru.hollowhorizon.hollowengine.common.capability

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.getLevel
import java.util.*

//TODO: Fixme
//@HollowCapability(Player::class)
//class CameraCapability : CapabilityInstance() {
//    var camera: Camera by syncable(Camera.Default)
//}
//
//var Player.cameraSystem: Camera
//    get() = this[CameraCapability::class].camera
//    set(value) {
//        this[CameraCapability::class].camera = value
//    }

@Serializable(with = CameraSerializer::class)
sealed interface Camera {
    @Serializable(with = CameraSerializer::class)
    data object Default : Camera

    @Serializable(with = CameraSerializer::class)
    class Static(val pos: Vec3, val yaw: Float, val pitch: Float, val roll: Float = 0f) : Camera

    @Serializable(with = CameraSerializer::class)
    class Watcher(val pos: Vec3, val entity: LivingEntity) : Camera
}

object CameraSerializer : KSerializer<Camera> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Camera") {
        element<String>("type")
        element("pos", ForVec3.descriptor, isOptional = true)
        element<Float>("yaw", isOptional = true)
        element<Float>("pitch", isOptional = true)
        element<Float>("roll", isOptional = true)
        element("entity", LivingEntitySerializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: Camera) {
        encoder.encodeStructure(descriptor) {
            when (value) {
                is Camera.Default -> {
                    encodeStringElement(descriptor, 0, "default")
                }

                is Camera.Static -> {
                    encodeStringElement(descriptor, 0, "static")
                    encodeSerializableElement(descriptor, 1, ForVec3, value.pos)
                    encodeFloatElement(descriptor, 2, value.yaw)
                    encodeFloatElement(descriptor, 3, value.pitch)
                    encodeFloatElement(descriptor, 4, value.roll)
                }

                is Camera.Watcher -> {
                    encodeStringElement(descriptor, 0, "watcher")
                    encodeSerializableElement(descriptor, 1, ForVec3, value.pos)
                    encodeSerializableElement(descriptor, 5, LivingEntitySerializer, value.entity)
                }
            }
        }
    }

    override fun deserialize(decoder: Decoder): Camera {
        return decoder.decodeStructure(descriptor) {
            var type: String? = null
            var pos: Vec3? = null
            var yaw: Float = 0f
            var pitch: Float = 0f
            var roll: Float = 0f
            var entity: LivingEntity? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> type = decodeStringElement(descriptor, 0)
                    1 -> pos = decodeSerializableElement(descriptor, 1, ForVec3)
                    2 -> yaw = decodeFloatElement(descriptor, 2)
                    3 -> pitch = decodeFloatElement(descriptor, 3)
                    4 -> roll = decodeFloatElement(descriptor, 4)
                    5 -> entity = decodeSerializableElement(descriptor, 5, LivingEntitySerializer)
                    else -> error("Unexpected index $index")
                }
            }

            if (type == null) type = "default"

            when (type) {
                "default" -> Camera.Default
                "static" -> Camera.Static(
                    pos ?: error("Missing 'pos' for Static camera"),
                    yaw,
                    pitch,
                    roll
                )

                "watcher" -> Camera.Watcher(
                    pos ?: error("Missing 'pos' for Watcher camera"),
                    entity ?: error("Missing 'entity' for Watcher camera")
                )

                else -> error("Unknown Camera type: $type")
            }
        }
    }
}

object LivingEntitySerializer : KSerializer<LivingEntity> {
    override val descriptor = buildClassSerialDescriptor("entity") {
        element("level", String.serializer().descriptor)
        element("uuid", ForUuid.descriptor)
    }

    override fun serialize(encoder: Encoder, value: LivingEntity) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.level().dimension().location().toString())
            encodeSerializableElement(descriptor, 1, ForUuid, value.uuid)
        }
    }

    override fun deserialize(decoder: Decoder): LivingEntity {
        return decoder.decodeStructure(descriptor) {
            var level: String? = null
            var uuid: UUID? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> level = decodeStringElement(descriptor, 0)
                    1 -> uuid = decodeSerializableElement(descriptor, 1, ForUuid)
                    else -> error("Unexpected index $index")
                }
            }

            if (isLogicalClient) {
                Minecraft.getInstance().level!!.entitiesForRendering()
                    .find { it.uuid == uuid } as LivingEntity
            } else {
                currentServer.getLevel(level ?: error("Unknown level"))!!
                    .getEntity(uuid ?: error("Entity uuid not found!")) as LivingEntity
            }
        }
    }
}
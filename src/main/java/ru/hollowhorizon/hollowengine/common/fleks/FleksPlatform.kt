package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.*
import kotlinx.serialization.*
import net.minecraft.nbt.Tag
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.fleks.lookup.MinecraftEntityLookup
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserialize
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize
import kotlin.reflect.KClass
import net.minecraft.world.entity.Entity as MCEntity

@Serializable
@SerialName("hollowengine:money")
class Money(var money: Int) : Component<Money> {
    override fun type() = Money

    companion object : ComponentType<Money>()
}

object FleksPlatform {

    @JvmStatic
    internal fun create(level: Level): World = configureWorld {
        injectables {
            add(level)
            add(MinecraftEntityLookup(WorldAccessor.get(this@configureWorld)))
        }

        systems {
            if (!level.isClientSide) {
                add(ComponentSyncSystem())
            }
        }
    }

    val format = NBTFormat
    private val serializers = hashMapOf<KClass<*>, KSerializer<*>>()

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    fun serializer(kClass: KClass<*>): KSerializer<*>? {
        serializers[kClass]?.let { return it }

        val serializer = kClass.serializerOrNull() ?: format.serializersModule.getContextual(kClass) ?: return null

        serializers[serializer::class] = serializer
        return serializer
    }

    context(world: World)
    fun saveEntity(entity: Entity): Tag = format.serialize(world.snapshotOfSerializable(entity))

    context(world: World)
    fun createEntity(mcEntity: MCEntity): Entity {
        val lookup = world.inject<MinecraftEntityLookup>()
        return lookup.linkWithMinecraft(mcEntity)
    }

    context(world: World, mcEntity: MCEntity)
    fun loadEntity(entity: Entity, tag: Tag?) {
        if (tag != null) {
            world.loadSnapshotAdditive(entity, format.deserialize(tag))
        }
    }

    context(world: World)
    fun removeEntity(entity: MCEntity) {
        val lookup = world.inject<MinecraftEntityLookup>()
        lookup.remove(entity.id)
    }

    context(world: World)
    fun changeId(entity: Entity, oldId: Int, mcId: Int) {
        val lookup = world.inject<MinecraftEntityLookup>()
        lookup.changeId(entity, oldId, mcId)
    }
}
package ru.hollowhorizon.hollowengine.common.geary.sync

import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.Entity
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.get
import com.mineinabyss.geary.serialization.SerializableComponents
import com.mineinabyss.geary.serialization.components.Persists
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.tracking.MinecraftEntityLookup
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.serializers
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.toComponentKey
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.toSerialName
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import kotlin.reflect.KClass

object Syncs

inline fun <reified T : Component> Entity.setSyncing(
    component: T,
    kClass: KClass<out T> = T::class,
    noEvent: Boolean = false,
): T {
    setRelation(world.getAddon(SerializableComponents).persists, world.componentId(kClass), Persists(), noEvent)
    setRelation(world.getAddon(SyncableComponents).syncs, world.componentId<T>(), Syncs, noEvent)
    set(component, kClass, noEvent)
    return component
}

inline fun <reified T : Component> Geary.registerSyncing() {
    val componentId = componentId<T>()
    val name = serializers.getSerialNameFor(T::class)?.toComponentKey()
        ?: error("SerialName not registered for ${T::class.simpleName}")
    componentId.toGeary().apply {
        set(name)
        add<Syncs>()
    }
}

@Serializable
sealed interface ComponentSyncPacket : HollowPacket {
    val entityId: Int

    val level get() = Minecraft.getInstance().level ?: error("Client level is not loaded yet!")
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class ComponentUpdatePacket(
    override val entityId: Int,
    val component: @Polymorphic Component
) : ComponentSyncPacket {
    override fun handle(player: Player) {
        val geary = level.geary
        val entity = geary.get<MinecraftEntityLookup>().getOrCreateById(entityId)
        with(geary) {
            entity.toGeary().set(component, component::class)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class ComponentRemovePacket(
    override val entityId: Int,
    val componentTypeId: @Serializable(ForResourceLocation::class) ResourceLocation
) : ComponentSyncPacket {
    override fun handle(player: Player) {
        val geary = level.geary
        val entity = geary.get<MinecraftEntityLookup>().getOrCreateById(entityId)
        with(geary) {
            val type = serializers.getClassFor(componentTypeId.toSerialName())
            entity.toGeary().remove(type)
        }
    }
}

//   val tag = world.formats.nbt.encode(world.serializers.getSerializerFor(kClass) as SerializationStrategy<T>, component)
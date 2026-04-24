package ru.hollowhorizon.hollowengine.common.geary.sync

import ru.hollowhorizon.hollowengine.common.geary.api.Component
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import kotlin.reflect.KClass

object Syncs

inline fun <reified T : Component> ru.hollowhorizon.hollowengine.common.geary.api.RuntimeEntityComponents.setSyncing(
    component: T,
    kClass: KClass<out T> = T::class,
    noEvent: Boolean = false,
): T = set(component, kClass, noEvent)

@Serializable
sealed interface ComponentSyncPacket : HollowPacket {
    val entityId: Int

    val level get() = Minecraft.getInstance().level ?: error("Client level is not loaded yet!")
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class ComponentUpdatePacket(
    override val entityId: Int,
    val component: @Polymorphic Component,
) : ComponentSyncPacket {
    override fun handle(player: Player) {
        val entity = level.getEntity(entityId) as? MCEntity ?: return
        entity.entity.set(component, component::class)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
data class ComponentRemovePacket(
    override val entityId: Int,
    val componentTypeId: @Serializable(ForResourceLocation::class) ResourceLocation,
) : ComponentSyncPacket {
    override fun handle(player: Player) {
        val entity = level.getEntity(entityId) as? MCEntity ?: return
        val descriptor = ComponentDescriptorRegistry.descriptorOrNull(componentTypeId) ?: return
        entity.entity.remove(descriptor.value)
    }
}
